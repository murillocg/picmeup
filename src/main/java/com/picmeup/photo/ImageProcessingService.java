package com.picmeup.photo;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ImageProcessingService {

    private static final int THUMBNAIL_MAX_WIDTH = 800;
    private static final String WATERMARK_TEXT = "Elite Sport Photos";
    private static final float WATERMARK_OPACITY = 0.35f;
    private static final double WATERMARK_ROTATION = -25.0;

    public byte[] generateThumbnail(byte[] originalBytes) throws IOException {
        // Read once just to validate and guard against upscaling small images
        var sizeCheck = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (sizeCheck == null) {
            throw new IOException("Unable to read image");
        }
        int targetWidth = Math.min(sizeCheck.getWidth(), THUMBNAIL_MAX_WIDTH);
        sizeCheck.flush();

        // Pass raw bytes to Thumbnailator so it can read and apply the EXIF orientation tag —
        // ImageIO strips EXIF metadata, so passing a BufferedImage skips orientation correction.
        var output = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(originalBytes))
                .width(targetWidth)
                .useExifOrientation(true)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .outputQuality(0.85)
                .toOutputStream(output);

        return output.toByteArray();
    }

    public byte[] applyWatermark(byte[] imageBytes) throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IOException("Unable to read image");
        }

        var watermarked = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g2d = watermarked.createGraphics();

        try {
            g2d.drawImage(image, 0, 0, null);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setColor(Color.WHITE);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATERMARK_OPACITY));

            int fontSize = Math.max(image.getWidth() / 10, 28);
            g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));

            var fontMetrics = g2d.getFontMetrics();
            int textWidth = fontMetrics.stringWidth(WATERMARK_TEXT);
            int textAscent = fontMetrics.getAscent();

            int imgW = image.getWidth();
            int imgH = image.getHeight();
            int centerX = (imgW - textWidth) / 2;

            // Three watermarks: top, center, bottom — each rotated at its own position
            int topY = imgH / 5;
            int centerY = imgH / 2;
            int bottomY = imgH * 4 / 5;

            for (int y : new int[]{topY, centerY, bottomY}) {
                var transform = AffineTransform.getRotateInstance(
                        Math.toRadians(WATERMARK_ROTATION), centerX + textWidth / 2.0, y);
                g2d.setTransform(transform);
                g2d.drawString(WATERMARK_TEXT, centerX, y + textAscent / 2);
            }

            g2d.setTransform(new AffineTransform());
        } finally {
            g2d.dispose();
        }

        var output = new ByteArrayOutputStream();
        ImageIO.write(watermarked, "jpg", output);
        return output.toByteArray();
    }

    public byte[] processPhoto(byte[] originalBytes) throws IOException {
        byte[] thumbnail = generateThumbnail(originalBytes);
        return applyWatermark(thumbnail);
    }

    public byte[] processPhoto(InputStream inputStream) throws IOException {
        // Pass stream directly to Thumbnailator so EXIF orientation is applied before resizing
        var thumbnailOutput = new ByteArrayOutputStream();
        Thumbnails.of(inputStream)
                .width(THUMBNAIL_MAX_WIDTH)
                .useExifOrientation(true)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .outputQuality(0.85)
                .toOutputStream(thumbnailOutput);

        if (thumbnailOutput.size() == 0) {
            throw new IOException("Unable to read image");
        }

        return applyWatermark(thumbnailOutput.toByteArray());
    }
}
