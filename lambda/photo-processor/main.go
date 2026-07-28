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
	"golang.org/x/image/font/gofont/gobold"
	"golang.org/x/image/font/opentype"
)

const (
	thumbnailMaxWidth = 800
	watermarkText     = "Elite Sport Photos"
	watermarkOpacity  = 0.35
	watermarkRotation = -25.0
	jpegQuality       = 85
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

func applyWatermark(img image.Image) image.Image {
	w := img.Bounds().Dx()
	h := img.Bounds().Dy()

	dc := gg.NewContext(w, h)
	dc.DrawImage(img, 0, 0)

	fontSize := float64(w) / 10
	if fontSize < 28 {
		fontSize = 28
	}

	f, err := opentype.Parse(gobold.TTF)
	if err != nil {
		log.Printf("Failed to parse font: %v", err)
		return img
	}
	face, err := opentype.NewFace(f, &opentype.FaceOptions{Size: fontSize, DPI: 72})
	if err != nil {
		log.Printf("Failed to create font face: %v", err)
		return img
	}
	dc.SetFontFace(face)
	dc.SetRGBA(1, 1, 1, watermarkOpacity)

	angle := watermarkRotation * math.Pi / 180
	cx := float64(w) / 2

	for _, y := range []float64{float64(h) / 5, float64(h) / 2, float64(h) * 4 / 5} {
		dc.Push()
		dc.RotateAbout(angle, cx, y)
		dc.DrawStringAnchored(watermarkText, cx, y, 0.5, 0.5)
		dc.Pop()
	}

	return dc.Image()
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
