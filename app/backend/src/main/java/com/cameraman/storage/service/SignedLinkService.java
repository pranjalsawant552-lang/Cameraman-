package com.cameraman.storage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Short-lived, tamper-proof download links for files that live in a provider with no native
 * presigned URLs (Google Drive). It is the same security model the app already uses for S3: the
 * application authorises the caller first, then hands out an expiring signed URL. The link points
 * at this backend's own {@code /files/{token}} endpoint, which verifies the HMAC and expiry and
 * then streams the bytes. The Drive file itself is never shared and no Drive credential or Drive
 * URL ever reaches the browser.
 *
 * Token = base64url(key) . expiryEpochSeconds . base64url(HMAC-SHA256(secret, b64key + "." + expiry)).
 */
@Service
public class SignedLinkService {

    private final byte[] secret;
    private final String apiBaseUrl;

    public SignedLinkService(@Value("${storage.signed-link.secret}") String secret,
                             @Value("${app.api-url}") String apiBaseUrl) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("storage.signed-link.secret must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
    }

    public String createUrl(String key, Duration ttl) {
        return apiBaseUrl + "/files/" + createToken(key, ttl, nowSeconds());
    }

    String createToken(String key, Duration ttl, long nowSeconds) {
        String encodedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        long expiry = nowSeconds + ttl.toSeconds();
        String signed = encodedKey + "." + expiry;
        return signed + "." + sign(signed);
    }

    /** The storage key a valid, unexpired token was issued for; empty for anything else. */
    public Optional<String> verify(String token) {
        return verify(token, nowSeconds());
    }

    Optional<String> verify(String token, long nowSeconds) {
        if (token == null) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3) return Optional.empty();
        String signed = parts[0] + "." + parts[1];
        byte[] expected = sign(signed).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.UTF_8))) return Optional.empty();
        try {
            if (Long.parseLong(parts[1]) < nowSeconds) return Optional.empty();
            return Optional.of(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000;
    }
}
