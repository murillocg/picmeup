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

@Service
public class ImageProcessingService {

    private static final int THUMBNAIL_MAX_WIDTH = 800;
    private static final String WATERMARK_TEXT = "PicMeUp";
    private static final float WATERMARK_OPACITY = 0.35f;
    private static final float WATERMARK_OPACITY_SECONDARY = 0.2f;
    private static final double WATERMARK_ROTATION = -30.0;
    private static final double WATERMARK_ROTATION_SECONDARY = 30.0;

    public byte[] generateThumbnail(byte[] originalBytes) throws IOException {
        var original = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (original == null) {
            throw new IOException("Unable to read image");
        }

        var output = new ByteArrayOutputStream();
        int targetWidth = Math.min(original.getWidth(), THUMBNAIL_MAX_WIDTH);

        Thumbnails.of(original)
                .width(targetWidth)
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

            int fontSize = Math.max(image.getWidth() / 10, 28);
            g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));

            var fontMetrics = g2d.getFontMetrics();
            int textWidth = fontMetrics.stringWidth(WATERMARK_TEXT);
            int textHeight = fontMetrics.getHeight();
            int spacing = (int) (textWidth * 0.8);

            var originalTransform = g2d.getTransform();

            // Primary watermark layer
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATERMARK_OPACITY));
            g2d.setTransform(AffineTransform.getRotateInstance(
                    Math.toRadians(WATERMARK_ROTATION),
                    image.getWidth() / 2.0,
                    image.getHeight() / 2.0
            ));

            for (int y = -image.getHeight(); y < image.getHeight() * 2; y += spacing) {
                for (int x = -image.getWidth(); x < image.getWidth() * 2; x += spacing) {
                    g2d.drawString(WATERMARK_TEXT, x, y);
                }
            }

            // Secondary watermark layer (opposite angle, smaller, offset)
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATERMARK_OPACITY_SECONDARY));
            int smallerFontSize = Math.max(fontSize * 2 / 3, 20);
            g2d.setFont(new Font("SansSerif", Font.ITALIC, smallerFontSize));
            g2d.setTransform(AffineTransform.getRotateInstance(
                    Math.toRadians(WATERMARK_ROTATION_SECONDARY),
                    image.getWidth() / 2.0,
                    image.getHeight() / 2.0
            ));

            int smallSpacing = (int) (spacing * 0.7);
            for (int y = -image.getHeight(); y < image.getHeight() * 2; y += smallSpacing) {
                for (int x = -image.getWidth(); x < image.getWidth() * 2; x += smallSpacing) {
                    g2d.drawString(WATERMARK_TEXT, x, y);
                }
            }

            g2d.setTransform(originalTransform);
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
}
