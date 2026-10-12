package com.cameraman.settings.service;

import com.cameraman.settings.domain.StudioSetting;
import com.cameraman.settings.dto.SettingsDto;
import com.cameraman.settings.repository.StudioSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StudioSettingsService {

    private final StudioSettingRepository settingRepository;

    private static final String STUDIO_NAME_KEY = "studio.name";
    private static final String STUDIO_OWNER_NAME_KEY = "studio.owner.name";
    private static final String STUDIO_TAGLINE_KEY = "studio.tagline";
    private static final String STUDIO_LOGO_KEY = "studio.logo.url";
    private static final String WATERMARK_TEXT_KEY = "studio.watermark.text";
    private static final String WATERMARK_LOGO_KEY = "studio.watermark.logo.url";
    private static final String GST_NUMBER_KEY = "studio.gst.number";
    private static final String STUDIO_ADDRESS_KEY = "studio.address";
    private static final String STUDIO_PHONE_KEY = "studio.phone";
    private static final String STUDIO_EMAIL_KEY = "studio.email";
    private static final String TERMS_CONDITIONS_KEY = "invoice.terms.conditions";
    private static final String THEME_PRIMARY_KEY = "theme.primary.color";
    private static final String THEME_SECONDARY_KEY = "theme.secondary.color";
    private static final String BANK_ACCOUNT_KEY = "studio.bank.account";
    private static final String BANK_IFSC_KEY = "studio.bank.ifsc";
    private static final String BANK_NAME_KEY = "studio.bank.name";
    private static final String UPI_ID_KEY = "studio.upi.id";
    private static final String WHATSAPP_NUMBER_KEY = "studio.whatsapp.number";
    private static final String INSTAGRAM_URL_KEY = "studio.instagram.url";
    private static final String FACEBOOK_URL_KEY = "studio.facebook.url";
    private static final String YOUTUBE_URL_KEY = "studio.youtube.url";
    private static final String MAP_URL_KEY = "studio.map.url";
    private static final String FOOTER_TAGLINE_KEY = "studio.footer.tagline";
    private static final String ABOUT_STATS_JSON_KEY = "studio.about.stats";
    private static final String SERVICES_JSON_KEY = "site.services";
    private static final String BOOKING_LEAD_TIME_HOURS_KEY = "booking.lead-time-hours";
    private static final String BOOKING_CANCEL_WINDOW_HOURS_KEY = "booking.cancellation-window-hours";
    private static final String BOOKING_RESCHEDULE_WINDOW_HOURS_KEY = "booking.reschedule-window-hours";
    private static final String SESSION_IDLE_TIMEOUT_KEY = "session.idle-timeout-minutes";
    private static final String ATTENDANCE_WEBHOOK_SECRET_KEY = "attendance.webhook.secret";
    private static final String ATTENDANCE_SHIFT_START_KEY = "attendance.shift-start";
    private static final String ATTENDANCE_GRACE_MINUTES_KEY = "attendance.grace-minutes";
    private static final String ATTENDANCE_WEEKLY_OFF_KEY = "attendance.weekly-off-day";
    private static final String ATTENDANCE_SHIFT_END_KEY = "attendance.shift-end";
    private static final String ATTENDANCE_HALF_DAY_KEY = "attendance.half-day-minutes";
    private static final String ATTENDANCE_MIN_OVERTIME_KEY = "attendance.min-overtime-minutes";
    private static final String ATTENDANCE_LATES_PER_HALF_DAY_KEY = "attendance.lates-per-half-day";

    /** The admin UI submits settings under their API field names (e.g. "studioName"); the
     *  table stores them under dotted keys (e.g. "studio.name"). Without this translation
     *  the UI wrote orphan rows that were never read back. Unknown keys pass through. */
    private static final Map<String, String> FIELD_TO_KEY = Map.ofEntries(
            Map.entry("studioName", STUDIO_NAME_KEY), Map.entry("ownerName", STUDIO_OWNER_NAME_KEY),
            Map.entry("tagline", STUDIO_TAGLINE_KEY), Map.entry("logoUrl", STUDIO_LOGO_KEY),
            Map.entry("watermarkText", WATERMARK_TEXT_KEY), Map.entry("watermarkLogoUrl", WATERMARK_LOGO_KEY),
            Map.entry("gstNumber", GST_NUMBER_KEY), Map.entry("address", STUDIO_ADDRESS_KEY),
            Map.entry("phone", STUDIO_PHONE_KEY), Map.entry("email", STUDIO_EMAIL_KEY),
            Map.entry("whatsappNumber", WHATSAPP_NUMBER_KEY), Map.entry("instagramUrl", INSTAGRAM_URL_KEY),
            Map.entry("facebookUrl", FACEBOOK_URL_KEY), Map.entry("youtubeUrl", YOUTUBE_URL_KEY),
            Map.entry("mapUrl", MAP_URL_KEY), Map.entry("footerTagline", FOOTER_TAGLINE_KEY),
            Map.entry("aboutStatsJson", ABOUT_STATS_JSON_KEY), Map.entry("servicesJson", SERVICES_JSON_KEY),
            Map.entry("bankAccount", BANK_ACCOUNT_KEY), Map.entry("bankIfsc", BANK_IFSC_KEY),
            Map.entry("bankName", BANK_NAME_KEY), Map.entry("upiId", UPI_ID_KEY),
            Map.entry("themePrimaryColor", THEME_PRIMARY_KEY), Map.entry("themeSecondaryColor", THEME_SECONDARY_KEY),
            Map.entry("bookingLeadTimeHours", BOOKING_LEAD_TIME_HOURS_KEY),
            Map.entry("cancellationWindowHours", BOOKING_CANCEL_WINDOW_HOURS_KEY),
            Map.entry("rescheduleWindowHours", BOOKING_RESCHEDULE_WINDOW_HOURS_KEY),
            Map.entry("sessionIdleTimeoutMinutes", SESSION_IDLE_TIMEOUT_KEY)
    );

    /** Minutes of inactivity after which the admin app signs the user out. 0 = never. Default 30. */
    @Transactional(readOnly = true)
    public int getSessionIdleTimeoutMinutes() {
        return Math.max(0, parseIntOr(getSetting(SESSION_IDLE_TIMEOUT_KEY), 30));
    }

    /** Minimum notice (hours) required before a new booking's start time. Default 24h. */
    @Transactional(readOnly = true)
    public int getBookingLeadTimeHours() {
        return parseIntOr(getSetting(BOOKING_LEAD_TIME_HOURS_KEY), 24);
    }

    /** How close to the booking a CLIENT may still self-service-cancel. Default 72h. */
    @Transactional(readOnly = true)
    public int getCancellationWindowHours() {
        return parseIntOr(getSetting(BOOKING_CANCEL_WINDOW_HOURS_KEY), 72);
    }

    /** How close to the booking a CLIENT may still self-service-reschedule. Default 48h. */
    @Transactional(readOnly = true)
    public int getRescheduleWindowHours() {
        return parseIntOr(getSetting(BOOKING_RESCHEDULE_WINDOW_HOURS_KEY), 48);
    }

    private int parseIntOr(String value, int fallback) {
        if (value == null || value.isBlank()) { return fallback; }
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    /** The shared secret every attendance ingestion endpoint checks (generic webhook and
     *  the ZKTeco/ADMS protocol endpoints) — generated once on first use and stored like
     *  any other setting, so it survives restarts and is visible/regenerable from the
     *  Attendance settings screen without touching a config file. */
    @Transactional
    public String getOrCreateAttendanceWebhookSecret() {
        String existing = getSetting(ATTENDANCE_WEBHOOK_SECRET_KEY);
        if (existing != null && !existing.isBlank()) { return existing; }
        String generated = java.util.UUID.randomUUID().toString().replace("-", "");
        updateSetting(ATTENDANCE_WEBHOOK_SECRET_KEY, generated);
        return generated;
    }

    @Transactional
    public String regenerateAttendanceWebhookSecret() {
        String generated = java.util.UUID.randomUUID().toString().replace("-", "");
        updateSetting(ATTENDANCE_WEBHOOK_SECRET_KEY, generated);
        return generated;
    }

    /** "HH:mm" the workday is expected to start — used to flag late arrivals in the
     *  monthly attendance report. Defaults to 09:30 until the studio sets its own. */
    public String getAttendanceShiftStart() {
        String v = getSetting(ATTENDANCE_SHIFT_START_KEY);
        return (v != null && !v.isBlank()) ? v : "09:30";
    }

    public int getAttendanceGraceMinutes() {
        return parseIntOr(getSetting(ATTENDANCE_GRACE_MINUTES_KEY), 15);
    }

    /** Day of week treated as the studio's standing day off (not flagged ABSENT even with
     *  no punch). Stored as the enum name, e.g. "SUNDAY". */
    public String getAttendanceWeeklyOffDay() {
        String v = getSetting(ATTENDANCE_WEEKLY_OFF_KEY);
        return (v != null && !v.isBlank()) ? v : "SUNDAY";
    }

    public String getAttendanceShiftEnd() {
        String v = getSetting(ATTENDANCE_SHIFT_END_KEY);
        return (v != null && !v.isBlank()) ? v : "18:30";
    }

    public int getAttendanceHalfDayMinutes() {
        return parseIntOr(getSetting(ATTENDANCE_HALF_DAY_KEY), 270);
    }

    public int getAttendanceMinOvertimeMinutes() {
        return parseIntOr(getSetting(ATTENDANCE_MIN_OVERTIME_KEY), 30);
    }

    public int getAttendanceLatesPerHalfDay() {
        return parseIntOr(getSetting(ATTENDANCE_LATES_PER_HALF_DAY_KEY), 3);
    }

    @Transactional
    public void updateAttendanceRules(String shiftEnd, Integer halfDayMinutes, Integer minOvertimeMinutes, Integer latesPerHalfDay) {
        if (shiftEnd != null && !shiftEnd.isBlank()) { updateSetting(ATTENDANCE_SHIFT_END_KEY, shiftEnd); }
        if (halfDayMinutes != null) { updateSetting(ATTENDANCE_HALF_DAY_KEY, String.valueOf(halfDayMinutes)); }
        if (minOvertimeMinutes != null) { updateSetting(ATTENDANCE_MIN_OVERTIME_KEY, String.valueOf(minOvertimeMinutes)); }
        if (latesPerHalfDay != null) { updateSetting(ATTENDANCE_LATES_PER_HALF_DAY_KEY, String.valueOf(latesPerHalfDay)); }
    }

    @Transactional
    public void updateAttendanceWorkHours(String shiftStart, Integer graceMinutes, String weeklyOffDay) {
        if (shiftStart != null && !shiftStart.isBlank()) { updateSetting(ATTENDANCE_SHIFT_START_KEY, shiftStart); }
        if (graceMinutes != null) { updateSetting(ATTENDANCE_GRACE_MINUTES_KEY, String.valueOf(graceMinutes)); }
        if (weeklyOffDay != null && !weeklyOffDay.isBlank()) { updateSetting(ATTENDANCE_WEEKLY_OFF_KEY, weeklyOffDay.toUpperCase()); }
    }

    @Cacheable(value = "settings", key = "#key")
    @Transactional(readOnly = true)
    public String getSetting(String key) {
        return settingRepository.findByKey(key).map(StudioSetting::getValue).orElse(null);
    }

    @Cacheable(value = "settings", key = "'studio.name'")
    @Transactional(readOnly = true)
    public String getStudioName() {
        return getSetting(STUDIO_NAME_KEY);
    }

    @Transactional(readOnly = true)
    public String getWatermarkText() { return getSetting(WATERMARK_TEXT_KEY); }

    @Transactional(readOnly = true)
    public String getDefaultTermsConditions() { return getSetting(TERMS_CONDITIONS_KEY); }

    @Cacheable(value = "studioSettings")
    @Transactional(readOnly = true)
    public SettingsDto.StudioSettingsResponse getStudioSettings() {
        Map<String, String> all = new HashMap<>();
        settingRepository.findAll().forEach(s -> all.put(s.getKey(), s.getValue()));

        return SettingsDto.StudioSettingsResponse.builder()
                .studioName(all.getOrDefault(STUDIO_NAME_KEY, "Cameraman Photo Studio"))
                .ownerName(all.get(STUDIO_OWNER_NAME_KEY))
                .tagline(all.getOrDefault(STUDIO_TAGLINE_KEY,
                        "आमच्याकडे कमर्शियल, इंडस्ट्रियल फोटो, लग्न, साखरपुडा, वाढदिवस, कॉलेज फंक्शनची व सर्व प्रकारच्या समारंभाच्या फोटो व व्हिडिओ शुटिंगची कामे काळजीपूर्वक केली जातील."))
                .logoUrl(all.get(STUDIO_LOGO_KEY))
                .watermarkText(all.get(WATERMARK_TEXT_KEY))
                .watermarkLogoUrl(all.get(WATERMARK_LOGO_KEY))
                .gstNumber(all.get(GST_NUMBER_KEY))
                .address(all.get(STUDIO_ADDRESS_KEY))
                .phone(all.get(STUDIO_PHONE_KEY))
                .email(all.get(STUDIO_EMAIL_KEY))
                .whatsappNumber(all.get(WHATSAPP_NUMBER_KEY))
                .instagramUrl(all.get(INSTAGRAM_URL_KEY))
                .facebookUrl(all.get(FACEBOOK_URL_KEY))
                .youtubeUrl(all.get(YOUTUBE_URL_KEY))
                .mapUrl(all.get(MAP_URL_KEY))
                .footerTagline(all.get(FOOTER_TAGLINE_KEY))
                .aboutStatsJson(all.get(ABOUT_STATS_JSON_KEY))
                .servicesJson(all.get(SERVICES_JSON_KEY))
                .bankAccount(all.get(BANK_ACCOUNT_KEY))
                .bankIfsc(all.get(BANK_IFSC_KEY))
                .bankName(all.get(BANK_NAME_KEY))
                .upiId(all.get(UPI_ID_KEY))
                .themePrimaryColor(all.getOrDefault(THEME_PRIMARY_KEY, "#1a73e8"))
                .themeSecondaryColor(all.getOrDefault(THEME_SECONDARY_KEY, "#34a853"))
                .bookingLeadTimeHours(parseIntOr(all.get(BOOKING_LEAD_TIME_HOURS_KEY), 24))
                .cancellationWindowHours(parseIntOr(all.get(BOOKING_CANCEL_WINDOW_HOURS_KEY), 72))
                .rescheduleWindowHours(parseIntOr(all.get(BOOKING_RESCHEDULE_WINDOW_HOURS_KEY), 48))
                .sessionIdleTimeoutMinutes(Math.max(0, parseIntOr(all.get(SESSION_IDLE_TIMEOUT_KEY), 30)))
                .build();
    }

    /** Safe-subset settings for the public marketing site — no auth, so this
     *  must never include bank details, GST number, or anything else from
     *  StudioSettingsResponse that isn't meant to be world-readable. Reuses
     *  getStudioSettings()'s cache rather than re-querying. */
    @Transactional(readOnly = true)
    public SettingsDto.PublicSiteInfo getPublicSiteInfo() {
        SettingsDto.StudioSettingsResponse s = getStudioSettings();
        return SettingsDto.PublicSiteInfo.builder()
                .studioName(s.getStudioName())
                .tagline(s.getTagline())
                .footerTagline(s.getFooterTagline())
                .address(s.getAddress())
                .phone(s.getPhone())
                .email(s.getEmail())
                .whatsappNumber(s.getWhatsappNumber())
                .instagramUrl(s.getInstagramUrl())
                .facebookUrl(s.getFacebookUrl())
                .youtubeUrl(s.getYoutubeUrl())
                .mapUrl(s.getMapUrl())
                .aboutStatsJson(s.getAboutStatsJson())
                .servicesJson(s.getServicesJson())
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, String> getPublicSettings() {
        return settingRepository.findByIsPublicTrue().stream()
                .collect(Collectors.toMap(StudioSetting::getKey, s -> s.getValue() != null ? s.getValue() : ""));
    }

    @CacheEvict(value = {"settings", "studioSettings"}, allEntries = true)
    public void updateSettings(Map<String, String> settings) {
        settings.forEach((rawKey, value) -> {
            String key = FIELD_TO_KEY.getOrDefault(rawKey, rawKey);
            StudioSetting setting = settingRepository.findByKey(key)
                    .orElse(StudioSetting.builder().key(key).build());
            if (!setting.isEditable()) {
                log.warn("Attempt to modify non-editable setting: {}", key);
                return;
            }
            setting.setValue(value);
            settingRepository.save(setting);
        });
    }

    @CacheEvict(value = {"settings", "studioSettings"}, allEntries = true)
    public void updateSetting(String key, String value) {
        StudioSetting setting = settingRepository.findByKey(key)
                .orElse(StudioSetting.builder().key(key).type("STRING").build());
        setting.setValue(value);
        settingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public List<SettingsDto.SettingResponse> getByCategory(String category) {
        return settingRepository.findByCategory(category).stream()
                .map(s -> SettingsDto.SettingResponse.builder()
                        .key(s.getKey()).value(s.getValue()).type(s.getType())
                        .category(s.getCategory()).description(s.getDescription())
                        .isPublic(s.isPublic()).isEditable(s.isEditable())
                        .build())
                .collect(Collectors.toList());
    }
}
