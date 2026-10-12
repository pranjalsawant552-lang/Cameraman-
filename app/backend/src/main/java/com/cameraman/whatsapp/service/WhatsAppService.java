package com.cameraman.whatsapp.service;

import com.cameraman.whatsapp.dto.WhatsAppDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WhatsAppService {

    @Value("${whatsapp.api-url}")
    private String apiUrl;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token}")
    private String accessToken;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return phoneNumberId != null && !phoneNumberId.isBlank()
                && accessToken != null && !accessToken.isBlank();
    }

    public void sendBookingConfirmation(String mobile, String clientName, String bookingNumber,
                                        String date, String time, String packageName) {
        String message = String.format(
                "Hello %s,\n\nYour booking *%s* has been confirmed!\n\n📅 Date: %s\n⏰ Time: %s\n📷 Package: %s\n\nThank you for choosing Cameraman Photo Studio.",
                clientName, bookingNumber, date, time, packageName);
        sendTextMessage(mobile, message);
    }

    public void sendOtp(String mobile, String otp, int expiryMinutes) {
        String message = String.format(
                "Your OTP for Cameraman Photo Studio is: *%s*\n\nThis OTP is valid for %d minutes.\nDo not share this with anyone.",
                otp, expiryMinutes);
        sendTextMessage(mobile, message);
    }

    public void sendInvoice(String mobile, String clientName, String invoiceNumber,
                             BigDecimal amount, String pdfUrl) {
        String message = String.format(
                "Hello %s,\n\nYour invoice *%s* for ₹%s is ready.\n\n🔗 Download: %s\n\nThank you for your business!",
                clientName, invoiceNumber, amount.toPlainString(), pdfUrl);
        sendTextMessage(mobile, message);
    }

    public void sendPaymentReminder(String mobile, String clientName, String invoiceNumber, BigDecimal balanceDue) {
        String message = String.format(
                "Hello %s,\n\nThis is a gentle reminder that invoice *%s* has a balance of ₹%s.\n\nPlease make the payment at your earliest convenience.",
                clientName, invoiceNumber, balanceDue.toPlainString());
        sendTextMessage(mobile, message);
    }

    public void sendGalleryLink(String mobile, String clientName, String galleryToken, String studioName) {
        String galleryUrl = baseUrl + "/gallery/" + galleryToken;
        String message = String.format(
                "Hello %s,\n\nYour photo gallery from *%s* is ready! 🎉\n\n📸 View Gallery: %s\n\nThe link is valid for 30 days.",
                clientName, studioName, galleryUrl);
        sendTextMessage(mobile, message);
    }

    public void sendSelectionReminder(String mobile, String clientName, String selectionToken, int remaining) {
        String selectionUrl = baseUrl + "/selection/" + selectionToken;
        String message = String.format(
                "Hello %s,\n\nYou still have *%d photos* to select from your gallery.\n\n🖼️ Select Now: %s\n\nPlease complete your selection soon!",
                clientName, remaining, selectionUrl);
        sendTextMessage(mobile, message);
    }

    public void sendDeliveryNotification(String mobile, String clientName, String deliveryDetails) {
        String message = String.format(
                "Hello %s,\n\nGreat news! Your photos/album is ready for delivery. 📦\n\n%s\n\nThank you for choosing us!",
                clientName, deliveryDetails);
        sendTextMessage(mobile, message);
    }

    /** Like sendTextMessage but reports whether WhatsApp actually accepted the message
     *  (false when not configured or the API call failed). Used where the UI must not
     *  claim "sent" when nothing went out. */
    public boolean trySendText(String mobile, String message) {
        if (!isConfigured()) {
            log.warn("WhatsApp not configured. Would have sent to {}: {}", mobile, message);
            return false;
        }
        try {
            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", normalizePhoneNumber(mobile),
                    "type", "text",
                    "text", Map.of("body", message, "preview_url", false)
            );
            post(body, mobile);
            return true;
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", mobile, e.getMessage());
            return false;
        }
    }

    public boolean sendPickupReminder(String mobile, String clientName, String bookingNumber, String studioName, long daysWaiting) {
        String waiting = daysWaiting > 0 ? " It has been ready for " + daysWaiting + " day" + (daysWaiting == 1 ? "" : "s") + "." : "";
        String message = String.format(
                "Hello %s,\n\nYour photos/order *%s* from *%s* are ready for pickup.%s\n\nPlease visit the studio at your convenience to collect them. Thank you!",
                clientName, bookingNumber, studioName, waiting);
        return trySendText(mobile, message);
    }

    public boolean sendInvoiceChecked(String mobile, String clientName, String invoiceNumber, BigDecimal amount, String pdfUrl) {
        String message = String.format(
                "Hello %s,\n\nYour invoice *%s* for ₹%s is ready.\n\n🔗 Download: %s\n\nThank you for your business!",
                clientName, invoiceNumber, amount.toPlainString(), pdfUrl);
        return trySendText(mobile, message);
    }

    public void sendTextMessage(String mobile, String message) {
        if (!isConfigured()) {
            log.warn("WhatsApp not configured. Would have sent to {}: {}", mobile, message);
            return;
        }
        try {
            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", normalizePhoneNumber(mobile),
                    "type", "text",
                    "text", Map.of("body", message, "preview_url", false)
            );
            post(body, mobile);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", mobile, e.getMessage());
        }
    }

    /**
     * Sends an image with an optional caption, via a public HTTPS link (WhatsApp Cloud
     * API downloads the media itself rather than requiring a pre-uploaded media ID).
     * Used by the bulk broadcast module.
     */
    public void sendImageMessage(String mobile, String imageUrl, String caption) {
        if (!isConfigured()) {
            log.warn("WhatsApp not configured. Would have sent image to {}: {}", mobile, imageUrl);
            return;
        }
        try {
            Map<String, Object> imagePayload = caption != null && !caption.isBlank()
                    ? Map.of("link", imageUrl, "caption", caption)
                    : Map.of("link", imageUrl);
            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", normalizePhoneNumber(mobile),
                    "type", "image",
                    "image", imagePayload
            );
            post(body, mobile);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp image to {}: {}", mobile, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /** Sends a video with an optional caption via a public HTTPS link. Used by the bulk broadcast module. */
    public void sendVideoMessage(String mobile, String videoUrl, String caption) {
        if (!isConfigured()) {
            log.warn("WhatsApp not configured. Would have sent video to {}: {}", mobile, videoUrl);
            return;
        }
        try {
            Map<String, Object> videoPayload = caption != null && !caption.isBlank()
                    ? Map.of("link", videoUrl, "caption", caption)
                    : Map.of("link", videoUrl);
            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", normalizePhoneNumber(mobile),
                    "type", "video",
                    "video", videoPayload
            );
            post(body, mobile);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp video to {}: {}", mobile, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Single entry point the broadcast module sends through: picks the right WhatsApp
     * message type based on whether media is attached, and — unlike the other send*
     * methods on this class, which swallow errors because they're best-effort side
     * notifications — throws on failure so the broadcast service can record a real
     * per-recipient failure instead of silently marking everyone "sent".
     */
    public void sendBroadcastMessage(String mobile, String text, String mediaUrl, String mediaType) {
        if (!isConfigured()) {
            throw new IllegalStateException("WhatsApp is not configured (whatsapp.access-token / phone-number-id missing)");
        }
        if (mediaUrl != null && !mediaUrl.isBlank() && "IMAGE".equalsIgnoreCase(mediaType)) {
            sendImageMessage(mobile, mediaUrl, text);
        } else if (mediaUrl != null && !mediaUrl.isBlank() && "VIDEO".equalsIgnoreCase(mediaType)) {
            sendVideoMessage(mobile, mediaUrl, text);
        } else {
            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", normalizePhoneNumber(mobile),
                    "type", "text",
                    "text", Map.of("body", text, "preview_url", false)
            );
            try {
                post(body, mobile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void post(Map<String, Object> body, String mobile) {
        String url = apiUrl + "/" + phoneNumberId + "/messages";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("WhatsApp message sent to {}", mobile);
        } else {
            throw new RuntimeException("WhatsApp API returned status " + response.getStatusCode());
        }
    }

    public void sendTemplateMessage(String mobile, String templateName, List<String> parameters) {
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("WhatsApp not configured for template message to {}", mobile);
            return;
        }
        // Template message implementation for approved WhatsApp templates
        log.info("Sending template '{}' to {}", templateName, mobile);
    }

    private String normalizePhoneNumber(String mobile) {
        if (mobile == null) return "";
        String cleaned = mobile.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("91") && cleaned.length() == 12) return cleaned;
        if (cleaned.length() == 10) return "91" + cleaned;
        return cleaned;
    }
}
