package com.cameraman.storage.provider;

import com.cameraman.common.exception.StorageException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Server-side OAuth access tokens for the studio's Google Drive storage. The credentials are
 * read from environment/config only; they are never logged, stored in the database or sent to a
 * browser. Two modes:
 * <ul>
 *   <li><b>OAuth refresh token</b> (recommended for a normal Google account, uses that
 *       account's own Drive quota): client id + client secret + refresh token.</li>
 *   <li><b>Service account</b> (needs a Shared Drive or Workspace, because a service account has
 *       no personal Drive quota): the JSON key, inline or as a file path.</li>
 * </ul>
 * Access tokens are cached until a minute before they expire.
 */
@Slf4j
@Component
public class GoogleDriveAuth {

    private static final String SCOPE = "https://www.googleapis.com/auth/drive";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    @Value("${google.drive.storage.oauth.client-id:}") private String clientId;
    @Value("${google.drive.storage.oauth.client-secret:}") private String clientSecret;
    @Value("${google.drive.storage.oauth.refresh-token:}") private String refreshToken;
    @Value("${google.drive.storage.service-account-json:}") private String serviceAccountJson;
    @Value("${google.drive.storage.service-account-path:}") private String serviceAccountPath;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private String cachedToken;
    private long cachedUntilEpochSec;
    private JsonNode serviceAccount;

    public boolean isConfigured() {
        return hasOAuth() || hasServiceAccount();
    }

    private boolean hasOAuth() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(refreshToken);
    }

    private boolean hasServiceAccount() {
        return notBlank(serviceAccountJson) || notBlank(serviceAccountPath);
    }

    /** A valid access token, refreshing it when needed. */
    public synchronized String accessToken() {
        long now = System.currentTimeMillis() / 1000;
        if (cachedToken != null && now < cachedUntilEpochSec - 60) return cachedToken;
        fetchToken();
        return cachedToken;
    }

    /** Drops the cached token (after a 401) so the next call fetches a fresh one. */
    public synchronized void invalidate() {
        cachedToken = null;
    }

    private void fetchToken() {
        try {
            Map<String, String> form = new LinkedHashMap<>();
            String tokenUri = DEFAULT_TOKEN_URI;
            if (hasOAuth()) {
                form.put("grant_type", "refresh_token");
                form.put("client_id", clientId);
                form.put("client_secret", clientSecret);
                form.put("refresh_token", refreshToken);
            } else if (hasServiceAccount()) {
                JsonNode sa = loadServiceAccount();
                if (sa.hasNonNull("token_uri")) tokenUri = sa.get("token_uri").asText();
                form.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
                form.put("assertion", signedJwt(sa, tokenUri));
            } else {
                throw new StorageException("Google Drive storage credentials are not configured");
            }

            String body = form.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                            + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(tokenUri))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                // Body deliberately not logged in full: it can echo request details.
                log.error("Google token endpoint returned {}", res.statusCode());
                throw new StorageException("Could not sign in to Google Drive (status " + res.statusCode()
                        + "). Check the Drive credentials in the server configuration.");
            }
            JsonNode json = mapper.readTree(res.body());
            cachedToken = json.get("access_token").asText();
            cachedUntilEpochSec = System.currentTimeMillis() / 1000 + json.path("expires_in").asLong(3600);
        } catch (StorageException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("Interrupted while signing in to Google Drive", e);
        } catch (Exception e) {
            throw new StorageException("Could not sign in to Google Drive: " + e.getMessage(), e);
        }
    }

    private JsonNode loadServiceAccount() throws Exception {
        if (serviceAccount == null) {
            String json = notBlank(serviceAccountJson) ? serviceAccountJson : Files.readString(Path.of(serviceAccountPath));
            serviceAccount = mapper.readTree(json);
        }
        return serviceAccount;
    }

    private String signedJwt(JsonNode sa, String audience) throws Exception {
        long now = System.currentTimeMillis() / 1000;
        String header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", sa.get("client_email").asText());
        claims.put("scope", SCOPE);
        claims.put("aud", audience);
        claims.put("iat", now);
        claims.put("exp", now + 3600);
        String payload = b64(mapper.writeValueAsString(claims));
        String unsigned = header + "." + payload;

        String pem = sa.get("private_key").asText()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(unsigned.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
