package com.cameraman.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.util.Map;

public class SettingsDto {
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StudioSettingsResponse {
        private String studioName;
        private String ownerName;
        private String tagline;
        private String logoUrl;
        private String watermarkText;
        private String watermarkLogoUrl;
        private String gstNumber;
        private String address;
        private String phone;
        private String email;
        private String whatsappNumber;
        private String instagramUrl;
        private String facebookUrl;
        private String youtubeUrl;
        private String mapUrl;
        private String footerTagline;
        /** Raw JSON array of {value,label} — e.g. [{"value":"500+","label":"sessions shot"}].
         *  Kept as a single opaque string here (same pattern as everything else in this
         *  key-value settings table) rather than a dedicated stats table, since it's small,
         *  low-cardinality content the admin edits as a unit. */
        private String aboutStatsJson;
        /** Raw JSON array of {icon,title,desc} for the "What we shoot" section on the
         *  public home page. `icon` is one of a fixed set of names the frontend maps to
         *  a lucide-react icon component — see PublicHome.tsx's ICON_MAP. */
        private String servicesJson;
        private String bankAccount;
        private String bankIfsc;
        private String bankName;
        private String upiId;
        private String themePrimaryColor;
        private String themeSecondaryColor;
        private Integer bookingLeadTimeHours;
        private Integer cancellationWindowHours;
        private Integer rescheduleWindowHours;
        /** Minutes of inactivity before the app signs a user out. 0 disables the idle logout. */
        private Integer sessionIdleTimeoutMinutes;
    }

    /** Safe subset of studio settings served with no auth required, for the public
     *  marketing site (footer, contact page, about page). Deliberately excludes anything
     *  from StudioSettingsResponse that shouldn't be world-readable — bank details, GST
     *  number, watermark config, booking-window internals. */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PublicSiteInfo {
        private String studioName;
        private String tagline;
        private String footerTagline;
        private String address;
        private String phone;
        private String email;
        private String whatsappNumber;
        private String instagramUrl;
        private String facebookUrl;
        private String youtubeUrl;
        private String mapUrl;
        private String aboutStatsJson;
        private String servicesJson;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SettingResponse {
        private String key;
        private String value;
        private String type;
        private String category;
        private String description;
        private boolean isPublic;
        private boolean isEditable;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateSettingsRequest {
        private Map<String, String> settings;
    }
}
