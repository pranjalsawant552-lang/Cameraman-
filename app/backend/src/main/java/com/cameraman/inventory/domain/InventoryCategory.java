package com.cameraman.inventory.domain;

import java.util.Locale;

public enum InventoryCategory {
    ALBUM, FRAME, CANVAS_PRINT, USB_DRIVE, PHOTO_PAPER, INK_CARTRIDGE,
    CAMERA_ACCESSORY, LIGHTING_EQUIPMENT, BACKDROP, PROPS, PACKAGING, OTHER;

    /**
     * Lenient parser: accepts "FRAME", "Frames", "photo paper", "Canvas Print" etc.
     * Unknown values fall back to OTHER instead of failing the request.
     */
    public static InventoryCategory parse(String raw) {
        if (raw == null || raw.isBlank()) return OTHER;
        String key = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        for (InventoryCategory c : values()) {
            if (c.name().equals(key)) return c;
        }
        // tolerate simple plurals: FRAMES -> FRAME, ALBUMS -> ALBUM, PROPS stays PROPS
        if (key.endsWith("S")) {
            String singular = key.substring(0, key.length() - 1);
            for (InventoryCategory c : values()) {
                if (c.name().equals(singular)) return c;
            }
        }
        return OTHER;
    }
}
