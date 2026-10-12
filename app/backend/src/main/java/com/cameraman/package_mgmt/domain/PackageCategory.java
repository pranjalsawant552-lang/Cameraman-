package com.cameraman.package_mgmt.domain;

import java.util.Locale;

public enum PackageCategory {
    CASUAL, WEDDING, PRE_WEDDING, BIRTHDAY, CORPORATE, MATERNITY, PORTFOLIO, NEWBORN, EVENT, FAMILY, PRODUCT, OTHER;

    /** Lenient parser: "Pre-Wedding", "studio" etc. Legacy/unknown names map to OTHER (STUDIO -> CASUAL). */
    public static PackageCategory parse(String raw) {
        if (raw == null || raw.isBlank()) return OTHER;
        String key = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        if ("STUDIO".equals(key)) return CASUAL;
        for (PackageCategory c : values()) {
            if (c.name().equals(key)) return c;
        }
        return OTHER;
    }
}
