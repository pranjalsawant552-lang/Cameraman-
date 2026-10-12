package com.cameraman.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Pluggable SMS gateway integration for sending OTPs and transactional SMS to
 * mobile numbers.
 *
 * <p>This is written against a generic HTTP SMS gateway shape compatible with
 * common Indian providers (Fast2SMS, MSG91, Twilio, etc.) - only the
 * request/response shape differs slightly between providers, so the exact
 * {@code sms.provider} value picked determines how the request is built.
 *
 * <p>Nothing here works out of the box without real credentials: set
 * {@code SMS_API_KEY}, {@code SMS_SENDER_ID}, and {@code SMS_PROVIDER} (or the
 * equivalent environment variables) to your chosen provider's values. Until
 * then, OTPs are only logged (visible in the backend console/logs) so the
 * app remains fully usable in development without a paid SMS account.
 */
@Slf4j
@Service
public class SmsService {

    @Value("${sms.provider:none}")
    private String provider; // fast2sms | msg91 | twilio | none

    @Value("${sms.api-url:}")
    private String apiUrl;

    @Value("${sms.api-key:}")
    private String apiKey;

    @Value("${sms.sender-id:CAMSTD}")
    private String senderId;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"none".equalsIgnoreCase(provider);
    }

    public void sendOtp(String mobile, String otp, int expiryMinutes) {
        String message = String.format(
                "Your OTP for Cameraman Photo Studio is %s. Valid for %d minutes. Do not share this with anyone.",
                otp, expiryMinutes);
        send(mobile, message);
    }

    public void send(String mobile, String message) {
        String normalized = normalize(mobile);

        if (!isConfigured()) {
            log.warn("SMS gateway not configured (set SMS_PROVIDER/SMS_API_KEY). " +
                    "Would have sent SMS to {}: {}", normalized, message);
            return;
        }

        try {
            switch (provider.toLowerCase()) {
                case "fast2sms" -> sendViaFast2Sms(normalized, message);
                case "msg91" -> sendViaMsg91(normalized, message);
                case "twilio" -> sendViaTwilio(normalized, message);
                default -> log.warn("Unknown SMS provider '{}'; message not sent to {}", provider, normalized);
            }
        } catch (Exception e) {
            log.error("Failed to send SMS to {} via {}: {}", normalized, provider, e.getMessage());
        }
    }

    private void sendViaFast2Sms(String mobile, String message) {
        String url = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl : "https://www.fast2sms.com/dev/bulkV2";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", apiKey);

        Map<String, Object> body = Map.of(
                "route", "otp",
                "sender_id", senderId,
                "message", message,
                "numbers", mobile
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        log.info("Fast2SMS response for {}: {}", mobile, response.getStatusCode());
    }

    private void sendViaMsg91(String mobile, String message) {
        String url = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl : "https://control.msg91.com/api/v5/flow/";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", apiKey);

        Map<String, Object> body = Map.of(
                "sender", senderId,
                "mobiles", mobile,
                "message", message
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        log.info("MSG91 response for {}: {}", mobile, response.getStatusCode());
    }

    private void sendViaTwilio(String mobile, String message) {
        // Twilio needs Account SID + Auth Token (Basic Auth) + a From number;
        // apiKey is expected to be formatted as "ACCOUNT_SID:AUTH_TOKEN:FROM_NUMBER".
        String[] parts = apiKey.split(":");
        if (parts.length != 3) {
            log.error("Twilio sms.api-key must be formatted as ACCOUNT_SID:AUTH_TOKEN:FROM_NUMBER");
            return;
        }
        String accountSid = parts[0];
        String authToken = parts[1];
        String fromNumber = parts[2];
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

        org.springframework.util.LinkedMultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("To", "+" + mobile);
        form.add("From", fromNumber);
        form.add("Body", message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(accountSid, authToken);

        HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        log.info("Twilio response for {}: {}", mobile, response.getStatusCode());
    }

    private String normalize(String mobile) {
        if (mobile == null) return "";
        String cleaned = mobile.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("91") && cleaned.length() == 12) return cleaned;
        if (cleaned.length() == 10) return "91" + cleaned;
        return cleaned;
    }
}
