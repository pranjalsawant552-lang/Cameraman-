package com.cameraman.gallery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoProcessingImageTest {

    @TempDir Path tmp;

    private byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void readsDimensionsFromTheHeader() throws Exception {
        Path f = tmp.resolve("a.png");
        Files.write(f, png(640, 480));
        assertThat(PhotoProcessingService.readDimensions(f, "image/png")).containsExactly(640, 480);
    }

    @Test
    void undecodableHeicGetsAnActionableMessage() throws Exception {
        Path f = tmp.resolve("a.heic");
        Files.write(f, new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'});
        assertThatThrownBy(() -> PhotoProcessingService.readDimensions(f, "image/heic"))
                .isInstanceOf(PhotoProcessingService.UnsupportedImageException.class)
                .hasMessageContaining("HEIC");
    }

    @Test
    void sidewaysCameraPhotoReportsItsUprightSize() throws Exception {
        // Header says landscape 4000x3000, but the upright preview is portrait (EXIF rotation).
        int[] oriented = PhotoProcessingService.orientDimensions(new int[]{4000, 3000}, png(300, 400));
        assertThat(oriented).containsExactly(3000, 4000);
    }

    @Test
    void uprightPhotoKeepsItsSize() throws Exception {
        int[] oriented = PhotoProcessingService.orientDimensions(new int[]{4000, 3000}, png(400, 300));
        assertThat(oriented).containsExactly(4000, 3000);
    }
}
