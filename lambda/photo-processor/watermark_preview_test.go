package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/disintegration/imaging"
)

// TestWatermarkPreview renders the watermark over real photos so the result can
// be inspected by eye. It is skipped unless WATERMARK_PREVIEW_SRC is set to a
// comma-separated list of image paths:
//
//	WATERMARK_PREVIEW_SRC=/path/a.jpg,/path/b.jpg \
//	WATERMARK_PREVIEW_OUT=/tmp/out go test -run TestWatermarkPreview ./...
func TestWatermarkPreview(t *testing.T) {
	sources := os.Getenv("WATERMARK_PREVIEW_SRC")
	if sources == "" {
		t.Skip("set WATERMARK_PREVIEW_SRC to render watermark previews")
	}

	outDir := os.Getenv("WATERMARK_PREVIEW_OUT")
	if outDir == "" {
		outDir = t.TempDir()
	}

	for _, src := range filepath.SplitList(strings.ReplaceAll(sources, ",", string(filepath.ListSeparator))) {
		img, err := imaging.Open(src, imaging.AutoOrientation(true))
		if err != nil {
			t.Fatalf("open %s: %v", src, err)
		}

		out := filepath.Join(outDir, "watermarked_"+filepath.Base(src))
		if err := imaging.Save(applyWatermark(generateThumbnail(img)), out, imaging.JPEGQuality(jpegQuality)); err != nil {
			t.Fatalf("save %s: %v", out, err)
		}
		t.Logf("wrote %s", out)
	}
}
