package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"image"
	"io"
	"log"
	"math"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/rekognition"
	rekTypes "github.com/aws/aws-sdk-go-v2/service/rekognition/types"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/disintegration/imaging"
	"github.com/fogleman/gg"
	"golang.org/x/image/font"
	"golang.org/x/image/font/gofont/gobold"
	"golang.org/x/image/font/opentype"
)

const (
	thumbnailMaxWidth = 800
	watermarkText     = "Elite Sport Photos"
	watermarkRotation = -25.0
	jpegQuality       = 85

	// Copyright badge — drawn opaque, so it survives the inpainting that easily
	// removes a translucent overlay.
	watermarkNotice = "PROTECTED BY COPYRIGHT · REPRODUCTION PROHIBITED"
	watermarkDomain = "ELITESPORTPHOTOS.COM"

	// Runs down the left and right margins, where the subject rarely is, so that
	// any crop tight enough to lose the branding also loses the photo.
	watermarkEdgeText = "ELITESPORTPHOTOS.COM"

	// Every mark is drawn twice, a dark shadow pass under a white pass, so the
	// watermark stays legible over bright skies as well as dark backgrounds.
	tileTextOpacity   = 0.30
	tileShadowOpacity = 0.18
	edgeTextOpacity   = 0.28
	bracketOpacity    = 0.45
	bracketShadowOpac = 0.25
	badgeBgOpacity    = 0.55
	badgeBorderOpac   = 0.35
	badgeTextOpacity  = 0.92
)

var (
	s3Client       *s3.Client
	rekClient      *rekognition.Client
	bucket         string
	callbackURL    string
	callbackSecret string
	rekPrefix      string
	httpClient     = &http.Client{Timeout: 30 * time.Second}
)

func init() {
	cfg, err := config.LoadDefaultConfig(context.Background())
	if err != nil {
		log.Fatalf("unable to load SDK config: %v", err)
	}
	s3Client = s3.NewFromConfig(cfg)
	rekClient = rekognition.NewFromConfig(cfg)

	bucket = os.Getenv("S3_BUCKET")
	callbackURL = os.Getenv("CALLBACK_URL")
	callbackSecret = os.Getenv("CALLBACK_SECRET")
	rekPrefix = os.Getenv("REKOGNITION_PREFIX")
}

type callbackPayload struct {
	OriginalS3Key  string   `json:"originalS3Key"`
	ThumbnailS3Key string   `json:"thumbnailS3Key"`
	FaceIds        []string `json:"faceIds"`
}

func handler(ctx context.Context, event events.S3Event) error {
	for _, record := range event.Records {
		key := record.S3.Object.Key
		log.Printf("Processing: %s", key)

		eventID, photoID, err := parseS3Key(key)
		if err != nil {
			log.Printf("Skipping %s: %v", key, err)
			continue
		}

		if err := processPhoto(ctx, key, eventID, photoID); err != nil {
			log.Printf("Failed to process %s: %v", key, err)
			notifyFailed(ctx, photoID)
		}
	}
	return nil
}

func parseS3Key(key string) (eventID, photoID string, err error) {
	parts := strings.Split(key, "/")
	if len(parts) != 3 || parts[0] != "originals" {
		return "", "", fmt.Errorf("unexpected key format: %s", key)
	}
	eventID = parts[1]
	photoID = strings.TrimSuffix(parts[2], ".jpg")
	return eventID, photoID, nil
}

func processPhoto(ctx context.Context, originalKey, eventID, photoID string) error {
	getResp, err := s3Client.GetObject(ctx, &s3.GetObjectInput{
		Bucket: &bucket,
		Key:    &originalKey,
	})
	if err != nil {
		return fmt.Errorf("download original: %w", err)
	}
	defer getResp.Body.Close()

	img, err := imaging.Decode(getResp.Body, imaging.AutoOrientation(true))
	if err != nil {
		return fmt.Errorf("decode image: %w", err)
	}

	thumbnail := generateThumbnail(img)
	watermarked := applyWatermark(thumbnail)

	var buf bytes.Buffer
	if err := imaging.Encode(&buf, watermarked, imaging.JPEG, imaging.JPEGQuality(jpegQuality)); err != nil {
		return fmt.Errorf("encode thumbnail: %w", err)
	}

	thumbnailKey := fmt.Sprintf("thumbnails/%s/%s.jpg", eventID, photoID)
	_, err = s3Client.PutObject(ctx, &s3.PutObjectInput{
		Bucket:      &bucket,
		Key:         &thumbnailKey,
		Body:        bytes.NewReader(buf.Bytes()),
		ContentType: aws.String("image/jpeg"),
	})
	if err != nil {
		return fmt.Errorf("upload thumbnail: %w", err)
	}

	faceIDs, err := indexFaces(ctx, eventID, photoID, originalKey)
	if err != nil {
		log.Printf("Face indexing failed for %s (non-fatal): %v", photoID, err)
		faceIDs = []string{}
	}

	return notifyProcessed(ctx, photoID, &callbackPayload{
		OriginalS3Key:  originalKey,
		ThumbnailS3Key: thumbnailKey,
		FaceIds:        faceIDs,
	})
}

func generateThumbnail(img image.Image) image.Image {
	width := img.Bounds().Dx()
	if width <= thumbnailMaxWidth {
		return img
	}
	return imaging.Resize(img, thumbnailMaxWidth, 0, imaging.Lanczos)
}

var (
	boldFont     *opentype.Font
	boldFontErr  error
	boldFontOnce sync.Once
)

// newFace builds a font face at the given size, parsing the embedded font only once.
func newFace(size float64) (font.Face, error) {
	boldFontOnce.Do(func() {
		boldFont, boldFontErr = opentype.Parse(gobold.TTF)
	})
	if boldFontErr != nil {
		return nil, boldFontErr
	}
	return opentype.NewFace(boldFont, &opentype.FaceOptions{Size: size, DPI: 72, Hinting: font.HintingFull})
}

// applyWatermark layers three marks over the preview: a diagonal grid of small
// repeats, corner brackets that make a crop obvious, and an opaque copyright
// badge. Many small marks are far harder to remove than a few large ones, and
// they leave the subject readable enough to still sell the photo.
func applyWatermark(img image.Image) image.Image {
	w := float64(img.Bounds().Dx())
	h := float64(img.Bounds().Dy())

	dc := gg.NewContext(img.Bounds().Dx(), img.Bounds().Dy())
	dc.DrawImage(img, 0, 0)

	if err := drawTiledText(dc, w, h); err != nil {
		log.Printf("Failed to draw watermark tiles: %v", err)
		return img
	}
	if err := drawEdgeText(dc, w, h); err != nil {
		log.Printf("Failed to draw edge text: %v", err)
	}
	drawCornerBrackets(dc, w, h)
	if err := drawCopyrightBadge(dc, w, h); err != nil {
		log.Printf("Failed to draw copyright badge: %v", err)
	}

	return dc.Image()
}

// drawTiledText covers the whole frame with a diagonal grid of the brand name.
// The context is rotated once and the grid drawn over a square the size of the
// image diagonal, which guarantees coverage to the corners at any angle.
func drawTiledText(dc *gg.Context, w, h float64) error {
	size := math.Max(w/22, 14)
	face, err := newFace(size)
	if err != nil {
		return err
	}
	dc.SetFontFace(face)

	textW, _ := dc.MeasureString(watermarkText)
	stepX := textW * 1.45
	stepY := size * 5.5
	shadow := math.Max(size/16, 1)

	diag := math.Hypot(w, h)
	cx, cy := w/2, h/2

	dc.Push()
	dc.RotateAbout(watermarkRotation*math.Pi/180, cx, cy)
	for row, y := 0, cy-diag/2; y <= cy+diag/2; row, y = row+1, y+stepY {
		offset := 0.0
		if row%2 == 1 {
			offset = stepX / 2
		}
		for x := cx - diag/2 + offset; x <= cx+diag/2; x += stepX {
			dc.SetRGBA(0, 0, 0, tileShadowOpacity)
			dc.DrawStringAnchored(watermarkText, x+shadow, y+shadow, 0.5, 0.5)
			dc.SetRGBA(1, 1, 1, tileTextOpacity)
			dc.DrawStringAnchored(watermarkText, x, y, 0.5, 0.5)
		}
	}
	dc.Pop()

	return nil
}

// drawEdgeText places the domain once down each margin, vertically centred,
// reading upward on the left and downward on the right. The type is shrunk if
// needed so it stays clear of the corner brackets at both ends.
func drawEdgeText(dc *gg.Context, w, h float64) error {
	size := math.Max(w/40, 10)
	face, err := newFace(size)
	if err != nil {
		return err
	}
	dc.SetFontFace(face)
	textW, _ := dc.MeasureString(watermarkEdgeText)

	// Keep clear of the bracket arms at top and bottom.
	m := math.Min(w, h)
	available := h - 2*(m*0.035+m*0.07)
	if textW > available {
		size *= available / textW
		if face, err = newFace(size); err != nil {
			return err
		}
		dc.SetFontFace(face)
	}

	inset := w * 0.028
	shadow := math.Max(size/16, 1)
	cy := h / 2

	for _, edge := range []struct{ x, angle float64 }{
		{inset, -math.Pi / 2},
		{w - inset, math.Pi / 2},
	} {
		dc.Push()
		dc.RotateAbout(edge.angle, edge.x, cy)
		dc.SetRGBA(0, 0, 0, tileShadowOpacity)
		dc.DrawStringAnchored(watermarkEdgeText, edge.x+shadow, cy+shadow, 0.5, 0.5)
		dc.SetRGBA(1, 1, 1, edgeTextOpacity)
		dc.DrawStringAnchored(watermarkEdgeText, edge.x, cy, 0.5, 0.5)
		dc.Pop()
	}

	return nil
}

// drawCornerBrackets frames the photo so that cropping the watermark away is
// visually obvious in the result.
func drawCornerBrackets(dc *gg.Context, w, h float64) {
	m := math.Min(w, h)
	arm := m * 0.07
	inset := m * 0.035
	lineWidth := math.Max(m/320, 1.5)
	dc.SetLineWidth(lineWidth)

	corners := []struct{ x, y, dx, dy float64 }{
		{inset, inset, 1, 1},
		{w - inset, inset, -1, 1},
		{inset, h - inset, 1, -1},
		{w - inset, h - inset, -1, -1},
	}

	for _, pass := range []struct {
		offset  float64
		shadow  bool
		opacity float64
	}{
		{lineWidth, true, bracketShadowOpac},
		{0, false, bracketOpacity},
	} {
		if pass.shadow {
			dc.SetRGBA(0, 0, 0, pass.opacity)
		} else {
			dc.SetRGBA(1, 1, 1, pass.opacity)
		}
		for _, c := range corners {
			dc.MoveTo(c.x+c.dx*arm+pass.offset, c.y+pass.offset)
			dc.LineTo(c.x+pass.offset, c.y+pass.offset)
			dc.LineTo(c.x+pass.offset, c.y+c.dy*arm+pass.offset)
			dc.Stroke()
		}
	}
}

// drawCopyrightBadge places the legal notice and domain in an opaque box centred
// at the bottom. Type size and margin come from the short edge rather than the
// width, which keeps the badge from spanning a landscape frame as a slab.
func drawCopyrightBadge(dc *gg.Context, w, h float64) error {
	const referenceSize = 100

	shortEdge := math.Min(w, h)

	refFace, err := newFace(referenceSize)
	if err != nil {
		return err
	}
	dc.SetFontFace(refFace)
	refW, _ := dc.MeasureString(watermarkNotice)
	noticeSize := math.Max(referenceSize*(shortEdge*0.72)/refW, 8)
	domainSize := noticeSize * 1.05

	noticeFace, err := newFace(noticeSize)
	if err != nil {
		return err
	}
	dc.SetFontFace(noticeFace)
	noticeW, noticeH := dc.MeasureString(watermarkNotice)

	domainFace, err := newFace(domainSize)
	if err != nil {
		return err
	}
	dc.SetFontFace(domainFace)
	domainW, domainH := dc.MeasureString(watermarkDomain)

	padX := noticeSize * 1.6
	padY := noticeSize * 1.0
	lineGap := noticeSize * 0.55
	boxW := math.Min(math.Max(noticeW, domainW)+padX*2, w*0.9)
	boxH := noticeH + domainH + lineGap + padY*2
	margin := shortEdge * 0.045
	boxX := (w - boxW) / 2
	boxY := h - boxH - margin
	radius := boxH * 0.16

	dc.SetRGBA(0, 0, 0, badgeBgOpacity)
	dc.DrawRoundedRectangle(boxX, boxY, boxW, boxH, radius)
	dc.Fill()

	dc.SetRGBA(1, 1, 1, badgeBorderOpac)
	dc.SetLineWidth(math.Max(w/700, 1))
	dc.DrawRoundedRectangle(boxX, boxY, boxW, boxH, radius)
	dc.Stroke()

	textCX := boxX + boxW/2
	dc.SetRGBA(1, 1, 1, badgeTextOpacity)
	dc.SetFontFace(noticeFace)
	dc.DrawStringAnchored(watermarkNotice, textCX, boxY+padY+noticeH/2, 0.5, 0.5)
	dc.SetFontFace(domainFace)
	dc.DrawStringAnchored(watermarkDomain, textCX, boxY+padY+noticeH+lineGap+domainH/2, 0.5, 0.5)

	return nil
}

func indexFaces(ctx context.Context, eventID, photoID, s3Key string) ([]string, error) {
	collectionID := fmt.Sprintf("%s-event-%s", rekPrefix, eventID)

	resp, err := rekClient.IndexFaces(ctx, &rekognition.IndexFacesInput{
		CollectionId: &collectionID,
		Image: &rekTypes.Image{
			S3Object: &rekTypes.S3Object{
				Bucket: &bucket,
				Name:   &s3Key,
			},
		},
		ExternalImageId:     &photoID,
		DetectionAttributes: []rekTypes.Attribute{rekTypes.AttributeDefault},
		QualityFilter:       rekTypes.QualityFilterAuto,
	})
	if err != nil {
		var notFound *rekTypes.ResourceNotFoundException
		if errors.As(err, &notFound) {
			_, createErr := rekClient.CreateCollection(ctx, &rekognition.CreateCollectionInput{
				CollectionId: &collectionID,
			})
			if createErr != nil {
				return nil, fmt.Errorf("create collection: %w", createErr)
			}
			return indexFaces(ctx, eventID, photoID, s3Key)
		}
		return nil, err
	}

	faceIDs := make([]string, 0, len(resp.FaceRecords))
	for _, record := range resp.FaceRecords {
		if record.Face != nil && record.Face.FaceId != nil {
			faceIDs = append(faceIDs, *record.Face.FaceId)
		}
	}
	return faceIDs, nil
}

func notifyProcessed(ctx context.Context, photoID string, payload *callbackPayload) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("marshal callback: %w", err)
	}

	url := fmt.Sprintf("%s/%s/processed", callbackURL, photoID)

	for attempt := 0; attempt < 3; attempt++ {
		if attempt > 0 {
			time.Sleep(2 * time.Second)
		}

		req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewReader(body))
		if err != nil {
			return fmt.Errorf("create request: %w", err)
		}
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("X-Callback-Secret", callbackSecret)

		resp, err := httpClient.Do(req)
		if err != nil {
			log.Printf("Callback attempt %d failed: %v", attempt+1, err)
			continue
		}
		io.Copy(io.Discard, resp.Body)
		resp.Body.Close()

		if resp.StatusCode == http.StatusNotFound {
			log.Printf("Photo %s not found yet, retrying...", photoID)
			continue
		}
		if resp.StatusCode >= 400 {
			return fmt.Errorf("callback returned %d", resp.StatusCode)
		}

		log.Printf("Photo %s processed successfully", photoID)
		return nil
	}
	return fmt.Errorf("callback failed after 3 attempts for photo %s", photoID)
}

func notifyFailed(ctx context.Context, photoID string) {
	url := fmt.Sprintf("%s/%s/failed", callbackURL, photoID)
	req, err := http.NewRequestWithContext(ctx, "POST", url, nil)
	if err != nil {
		log.Printf("Failed to create error callback: %v", err)
		return
	}
	req.Header.Set("X-Callback-Secret", callbackSecret)

	resp, err := httpClient.Do(req)
	if err != nil {
		log.Printf("Error callback failed: %v", err)
		return
	}
	io.Copy(io.Discard, resp.Body)
	resp.Body.Close()
}

func main() {
	lambda.Start(handler)
}
