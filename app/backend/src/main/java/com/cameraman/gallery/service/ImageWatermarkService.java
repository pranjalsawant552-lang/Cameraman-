package com.cameraman.gallery.service;

import com.cameraman.settings.service.StudioSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageWatermarkService {

    private final StudioSettingsService settingsService;

    @Value("${image.watermark.opacity:0.5}")
    private float watermarkOpacity;

    /**
     * @param clientLabel identifying text baked into every image (e.g. "Priya Sharma
     *                     · 98765xxxxx · 30 Jul 2026") so that if a preview leaks, it's
     *                     traceable back to which client's gallery it came from. Pass
     *                     null/blank to fall back to the plain studio watermark only
     *                     (used for public/portfolio images that aren't tied to a client).
     */
    public byte[] applyWatermark(byte[] imageBytes, String clientLabel) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (original == null) return imageBytes;

        String studioText = settingsService.getWatermarkText();
        if (studioText == null || studioText.isBlank()) {
            studioText = settingsService.getStudioName();
        }

        BufferedImage output = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = output.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, null);

        // Tiled diagonal watermark across the WHOLE image (not just one corner) — a corner
        // mark is trivially cropped out; a tiled diagonal pattern survives any crop small
        // enough to still be a usable photo.
        int fontSize = Math.max(16, original.getWidth() / 28);
        Font font = new Font("Arial", Font.BOLD, fontSize);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int tileText = fm.stringWidth(studioText);
        int stepX = tileText + 120;
        int stepY = fontSize + 100;

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(watermarkOpacity, 0.22f)));
        g2d.setColor(Color.WHITE);
        java.awt.geom.AffineTransform originalTransform = g2d.getTransform();
        for (int y = -stepY; y < original.getHeight() + stepY; y += stepY) {
            for (int x = -stepX; x < original.getWidth() + stepX; x += stepX) {
                g2d.translate(x, y);
                g2d.rotate(-Math.PI / 6);
                g2d.drawString(studioText, 0, 0);
                g2d.setTransform(originalTransform);
            }
        }

        // Bottom identifying strip: studio name + (if provided) the client this gallery
        // belongs to, at higher opacity so it's always legible even if the diagonal tile
        // lands somewhere awkward.
        String stripText = (clientLabel != null && !clientLabel.isBlank())
                ? studioText + "  ·  For: " + clientLabel
                : studioText;
        int stripFontSize = Math.max(14, original.getWidth() / 45);
        Font stripFont = new Font("Arial", Font.BOLD, stripFontSize);
        g2d.setFont(stripFont);
        FontMetrics stripFm = g2d.getFontMetrics();
        int stripTextWidth = stripFm.stringWidth(stripText);
        int sx = Math.max(10, (original.getWidth() - stripTextWidth) / 2);
        int sy = original.getHeight() - 20;

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g2d.setColor(Color.BLACK);
        g2d.drawString(stripText, sx + 2, sy + 2);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g2d.setColor(Color.WHITE);
        g2d.drawString(stripText, sx, sy);

        g2d.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(output, "jpg", out);
        return out.toByteArray();
    }

    /** Studio-only watermark, no client identifying strip (portfolio/public images). */
    public byte[] applyWatermark(byte[] imageBytes) throws IOException {
        return applyWatermark(imageBytes, null);
    }
}
