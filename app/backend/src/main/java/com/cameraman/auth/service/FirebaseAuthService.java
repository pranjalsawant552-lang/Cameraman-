package com.cameraman.auth.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Verifies Firebase Phone Auth ID tokens.
 *
 * <p>The OTP itself is generated and sent entirely by Google's Firebase
 * infrastructure via the Firebase JS SDK running in the browser (with
 * invisible reCAPTCHA) — this backend never sends or stores a mobile OTP.
 * Once the user enters the code, the frontend gets a Firebase ID token and
 * sends it here; we verify it server-side and trust the phone number it
 * contains because it was cryptographically signed by Google.
 *
 * <p>Requires a Firebase service account key (Project Settings → Service
 * accounts → Generate new private key in the Firebase console), provided via
 * either {@code FIREBASE_SERVICE_ACCOUNT_JSON} (raw JSON content) or
 * {@code FIREBASE_SERVICE_ACCOUNT_PATH} (path to the JSON file).
 */
@Slf4j
@Service
public class FirebaseAuthService {

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${firebase.project-id:}")
    private String projectId;

    private boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            InputStream credentialStream = resolveCredentialStream();
            if (credentialStream == null) {
                log.warn("Firebase service account not configured (set FIREBASE_SERVICE_ACCOUNT_JSON or " +
                        "FIREBASE_SERVICE_ACCOUNT_PATH). Mobile OTP verification via Firebase will be unavailable " +
                        "until this is set.");
                return;
            }

            FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialStream));
            if (projectId != null && !projectId.isBlank()) {
                optionsBuilder.setProjectId(projectId);
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(optionsBuilder.build());
            }
            initialized = true;
            log.info("Firebase Admin SDK initialized for mobile OTP verification");
        } catch (IOException e) {
            log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage());
        }
    }

    private InputStream resolveCredentialStream() throws IOException {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            return new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
        }
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            return new FileInputStream(serviceAccountPath);
        }
        return null;
    }

    public boolean isConfigured() {
        return initialized;
    }

    /**
     * Verifies a Firebase Phone Auth ID token and returns the verified phone
     * number in E.164 format (e.g. {@code +919876543210}), or {@code null}
     * if the token has no phone number claim (shouldn't happen for a token
     * produced by phone sign-in).
     *
     * @throws com.cameraman.common.exception.BusinessException if the token is missing/invalid/expired
     */
    public String verifyPhoneToken(String idToken) {
        if (!initialized) {
            throw new com.cameraman.common.exception.BusinessException(
                    "Firebase mobile verification is not configured on the server. " +
                            "Contact the studio administrator.", "FIREBASE_NOT_CONFIGURED");
        }
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
            Object phone = decoded.getClaims().get("phone_number");
            return phone != null ? phone.toString() : null;
        } catch (FirebaseAuthException e) {
            log.warn("Firebase ID token verification failed: {}", e.getMessage());
            throw new com.cameraman.common.exception.BusinessException(
                    "Mobile verification failed or expired. Please verify your number again.", "FIREBASE_TOKEN_INVALID");
        }
    }

    /** Normalizes a phone number to bare digits (last 10) so E.164 (+91...) and local formats compare equal. */
    public String lastTenDigits(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }
}
