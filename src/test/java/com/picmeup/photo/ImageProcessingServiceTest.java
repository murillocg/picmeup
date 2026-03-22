package com.picmeup.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessingServiceTest {

    private ImageProcessingService imageProcessingService;

    @BeforeEach
    void setUp() {
        imageProcessingService = new ImageProcessingService();
    }

    @Test
    void generateThumbnail_shouldResizeToMaxWidth() throws IOException {
        byte[] original = createTestImage(2000, 1500);

        byte[] thumbnail = imageProcessingService.generateThumbnail(original);

        var image = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(image.getWidth()).isEqualTo(800);
        assertThat(image.getHeight()).isEqualTo(600);
    }

    @Test
    void generateThumbnail_shouldNotUpscaleSmallImages() throws IOException {
        byte[] original = createTestImage(400, 300);

        byte[] thumbnail = imageProcessingService.generateThumbnail(original);

        var image = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(image.getWidth()).isLessThanOrEqualTo(400);
    }

    @Test
    void applyWatermark_shouldReturnImageWithSameDimensions() throws IOException {
        byte[] original = createTestImage(800, 600);

        byte[] watermarked = imageProcessingService.applyWatermark(original);

        var image = ImageIO.read(new ByteArrayInputStream(watermarked));
        assertThat(image.getWidth()).isEqualTo(800);
        assertThat(image.getHeight()).isEqualTo(600);
    }

    @Test
    void applyWatermark_shouldModifyImagePixels() throws IOException {
        byte[] original = createTestImage(800, 600);

        byte[] watermarked = imageProcessingService.applyWatermark(original);

        assertThat(watermarked).isNotEqualTo(original);
    }

    @Test
    void processPhoto_shouldResizeAndWatermark() throws IOException {
        byte[] original = createTestImage(2000, 1500);

        byte[] processed = imageProcessingService.processPhoto(original);

        var image = ImageIO.read(new ByteArrayInputStream(processed));
        assertThat(image.getWidth()).isEqualTo(800);
        assertThat(image.getHeight()).isEqualTo(600);
        assertThat(processed.length).isGreaterThan(0);
    }

    @Test
    void applyWatermark_shouldThrowForInvalidImage() {
        byte[] invalidData = "not an image".getBytes();

        assertThatThrownBy(() -> imageProcessingService.applyWatermark(invalidData))
                .isInstanceOf(IOException.class);
    }

    private byte[] createTestImage(int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
