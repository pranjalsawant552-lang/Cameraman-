package com.cameraman.gallery.service;

import com.cameraman.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bulk-imports images from a Google Drive folder into a gallery — an alternative to
 * uploading through the browser, for studios whose workflow already lands shoot
 * photos in a Drive folder (e.g. synced from a camera-backup app).
 *
 * Deliberately uses a plain Drive API key (not full OAuth) — this only works for
 * folders shared as "Anyone with the link can view", which matches how studios
 * typically already share these folders internally. This is a completely separate
 * concern from client-facing gallery privacy (that's the existing signed-URL +
 * watermark + admin-gated-download system) — Drive is never exposed to clients here,
 * it's purely a bulk-ingestion source for staff.
 *
 * Uses java.net.http.HttpClient (built into the JDK since 11) rather than adding a
 * new HTTP client dependency.
 */
@Slf4j
@Service
public class GoogleDriveImportService {

    @Value("${google.drive.api-key:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern FOLDER_ID_PATTERN = Pattern.compile("(?:folders/|[?&]id=)([a-zA-Z0-9_-]{10,})");

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public record DriveFile(String id, String name, String mimeType, Long size) {}

    /** Extracts the folder ID from any of Drive's common URL formats, or returns the
     *  input unchanged if it's already a bare folder ID. */
    public String extractFolderId(String urlOrId) {
        Matcher m = FOLDER_ID_PATTERN.matcher(urlOrId);
        if (m.find()) {
            return m.group(1);
        }
        // Assume it's already a bare folder ID if it doesn't look like a URL
        if (!urlOrId.contains("/") && !urlOrId.contains("?")) {
            return urlOrId.trim();
        }
        throw new BusinessException("Couldn't find a folder ID in that link. Paste the full Drive folder share link.", "INVALID_DRIVE_URL");
    }

    /** Lists image files directly inside the given folder (does not recurse into subfolders). */
    public List<DriveFile> listImageFiles(String folderId) {
        if (!isConfigured()) {
            throw new BusinessException(
                    "Google Drive import isn't set up yet — add a GOOGLE_DRIVE_API_KEY to enable it.",
                    "DRIVE_NOT_CONFIGURED");
        }

        List<DriveFile> results = new ArrayList<>();
        String pageToken = null;

        try {
            do {
                String query = "'" + folderId + "' in parents and trashed = false and mimeType contains 'image/'";
                StringBuilder url = new StringBuilder("https://www.googleapis.com/drive/v3/files")
                        .append("?q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
                        .append("&fields=nextPageToken,files(id,name,mimeType,size)")
                        .append("&pageSize=1000")
                        .append("&key=").append(apiKey);
                if (pageToken != null) {
                    url.append("&pageToken=").append(pageToken);
                }

                HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                        .timeout(Duration.ofSeconds(30)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.error("Drive API list error ({}): {}", response.statusCode(), response.body());
                    throw new BusinessException(
                            "Couldn't read that Drive folder. Make sure it's shared as \"Anyone with the link can view\".",
                            "DRIVE_ACCESS_DENIED");
                }

                JsonNode root = objectMapper.readTree(response.body());
                for (JsonNode f : root.path("files")) {
                    results.add(new DriveFile(f.get("id").asText(), f.get("name").asText(), f.get("mimeType").asText(),
                            f.hasNonNull("size") ? Long.valueOf(f.get("size").asText()) : null));
                }
                pageToken = root.has("nextPageToken") ? root.get("nextPageToken").asText() : null;
            } while (pageToken != null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to list Drive folder {}: {}", folderId, e.getMessage());
            throw new BusinessException("Couldn't connect to Google Drive: " + e.getMessage(), "DRIVE_ERROR");
        }

        return results;
    }

    /**
     * Streams a single file from Drive straight to disk. Never held in memory (a camera original
     * can be 100 MB), and a failed or short download deletes the partial file.
     */
    public void downloadToFile(String fileId, java.nio.file.Path target) {
        try {
            String url = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media&key=" + apiKey;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(10)).GET().build();
            HttpResponse<java.nio.file.Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() != 200) {
                java.nio.file.Files.deleteIfExists(target);
                throw new BusinessException("Failed to download a file from Drive (status " + response.statusCode() + ")", "DRIVE_DOWNLOAD_ERROR");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Download from Drive was interrupted", "DRIVE_ERROR");
        } catch (Exception e) {
            throw new BusinessException("Failed to download a file from Drive: " + e.getMessage(), "DRIVE_ERROR");
        }
    }
}
