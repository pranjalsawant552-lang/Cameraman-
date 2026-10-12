package com.cameraman.storage.provider;

import com.cameraman.common.exception.StorageException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Google Drive as a private object store, used primarily for full-resolution originals.
 *
 * Every file lives in a private folder tree under {@code google.drive.storage.root-folder-id} and
 * is never shared: access is only ever through this backend, which streams bytes (or hands out
 * short-lived signed links to its own /files endpoint) after the application has authorised the
 * caller. Keys look like {@code gdrive:<driveFileId>}, so a stored key says where it lives and
 * keys from before this provider existed (no prefix) keep resolving to S3.
 *
 * Uses the JDK HttpClient against the Drive v3 REST API (the same approach as the existing
 * Drive import) so no new dependency is needed.
 */
@Slf4j
@Component
public class GoogleDriveStorageProvider implements StorageProvider, ResumableUploads {

    public static final String PREFIX = "gdrive:";
    private static final String API = "https://www.googleapis.com/drive/v3";
    private static final String UPLOAD_API = "https://www.googleapis.com/upload/drive/v3";
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final int MAX_ATTEMPTS = 4;

    private final GoogleDriveAuth auth;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> folderCache = new ConcurrentHashMap<>();
    private final Object folderLock = new Object();

    @Value("${google.drive.storage.root-folder-id:}")
    private String rootFolderId;

    public GoogleDriveStorageProvider(GoogleDriveAuth auth) {
        this.auth = auth;
    }

    @Override public String id() { return "gdrive"; }

    @Override
    public boolean isAvailable() {
        return rootFolderId != null && !rootFolderId.isBlank() && auth.isConfigured();
    }

    @Override
    public boolean owns(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    static String fileId(String key) {
        return key.substring(PREFIX.length());
    }

    // ------------------------------------------------------------------ whole-file store

    @Override
    public String store(String folder, String filename, String contentType, ContentSource source, long size) {
        int attempts = source.replayable() ? 3 : 1;
        StorageException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                Session session = beginResumable(folder, filename, contentType, size);
                HttpResponse<String> res = call(token -> HttpRequest.newBuilder(URI.create(session.sessionUri()))
                        .timeout(transferTimeout(size))
                        .header("Content-Type", contentType == null ? "application/octet-stream" : contentType)
                        .PUT(publisherFor(source, size)).build(),
                        HttpResponse.BodyHandlers.ofString(), false);
                if (res.statusCode() == 200 || res.statusCode() == 201) return session.key();
                throw failure("Upload to Google Drive", res.statusCode(), res.body());
            } catch (StorageException e) {
                last = e;
                log.warn("Drive upload of '{}' failed (attempt {}/{}): {}", filename, attempt, attempts, e.getMessage());
                if (attempt < attempts) sleep(backoff(attempt));
            }
        }
        throw last;
    }

    // ------------------------------------------------------------------ resumable (chunked)

    @Override
    public Session beginResumable(String folder, String filename, String contentType, long totalSize) {
        for (int pass = 1; ; pass++) {
            String folderId = ensureFolder(folder);
            String id = generateId();
            try {
                String meta = mapper.writeValueAsString(Map.of(
                        "id", id, "name", safeName(filename), "parents", List.of(folderId)));
                HttpResponse<String> res = call(token -> HttpRequest.newBuilder(URI.create(
                                UPLOAD_API + "/files?uploadType=resumable&supportsAllDrives=true"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("X-Upload-Content-Type", contentType == null ? "application/octet-stream" : contentType)
                        .header("X-Upload-Content-Length", String.valueOf(totalSize))
                        .POST(HttpRequest.BodyPublishers.ofString(meta)).build(),
                        HttpResponse.BodyHandlers.ofString(), true);
                if (res.statusCode() == 404 && pass == 1) {
                    folderCache.clear(); // the cached folder was deleted or moved behind our back
                    continue;
                }
                if (res.statusCode() != 200) throw failure("Starting the Google Drive upload", res.statusCode(), res.body());
                String location = res.headers().firstValue("Location")
                        .orElseThrow(() -> new StorageException("Google Drive did not return an upload session"));
                return new Session(PREFIX + id, location);
            } catch (IOException e) {
                throw new StorageException("Could not start the Google Drive upload: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public Progress uploadChunk(String sessionUri, long offset, byte[] chunk, long totalSize) {
        long end = offset + chunk.length - 1;
        // A chunk is NOT retried here: after a 5xx the safe move is to ask the provider how much
        // it has (queryProgress) and resume from there, which the caller does.
        HttpResponse<String> res = call(token -> HttpRequest.newBuilder(URI.create(sessionUri))
                .timeout(transferTimeout(chunk.length))
                .header("Content-Range", "bytes " + offset + "-" + end + "/" + totalSize)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk)).build(),
                HttpResponse.BodyHandlers.ofString(), false);
        return interpretProgress(res, totalSize, "Uploading to Google Drive");
    }

    @Override
    public Progress queryProgress(String sessionUri, long totalSize) {
        HttpResponse<String> res = call(token -> HttpRequest.newBuilder(URI.create(sessionUri))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Range", "bytes */" + totalSize)
                .PUT(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(), true);
        return interpretProgress(res, totalSize, "Checking the Google Drive upload");
    }

    private Progress interpretProgress(HttpResponse<String> res, long total, String what) {
        int s = res.statusCode();
        if (s == 200 || s == 201) return new Progress(true, total);
        if (s == 308) {
            // "Range: bytes=0-N" means N+1 bytes are stored; no header means nothing yet.
            long received = res.headers().firstValue("Range")
                    .map(r -> Long.parseLong(r.substring(r.indexOf('-') + 1)) + 1).orElse(0L);
            return new Progress(false, received);
        }
        if (s == 404 || s == 410) {
            throw new UploadSessionExpiredException("The upload session expired. Please upload the file again.");
        }
        throw failure(what, s, res.body());
    }

    // ------------------------------------------------------------------ read / delete / stat

    @Override
    public StoredObject open(String key) {
        HttpResponse<InputStream> res = call(token -> HttpRequest.newBuilder(URI.create(
                        API + "/files/" + fileId(key) + "?alt=media&supportsAllDrives=true"))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream(), true);
        if (res.statusCode() != 200) {
            try (InputStream body = res.body()) {
                String text = new String(body.readNBytes(2048), StandardCharsets.UTF_8);
                throw failure("Downloading from Google Drive", res.statusCode(), text);
            } catch (IOException e) {
                throw new StorageException("Downloading from Google Drive failed (HTTP " + res.statusCode() + ")", e);
            }
        }
        String type = res.headers().firstValue("Content-Type").orElse("application/octet-stream");
        long length = res.headers().firstValueAsLong("Content-Length").orElse(-1L);
        return new StoredObject(res.body(), type, length);
    }

    @Override
    public void delete(String key) {
        try {
            HttpResponse<String> res = call(token -> HttpRequest.newBuilder(URI.create(
                            API + "/files/" + fileId(key) + "?supportsAllDrives=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token).DELETE().build(),
                    HttpResponse.BodyHandlers.ofString(), true);
            int s = res.statusCode();
            if (s != 204 && s != 200 && s != 404) {
                log.error("Failed to delete Drive file {} (HTTP {})", key, s);
            }
        } catch (Exception e) {
            log.error("Failed to delete Drive file {}: {}", key, e.getMessage());
        }
    }

    @Override
    public long size(String key) {
        HttpResponse<String> res = call(token -> HttpRequest.newBuilder(URI.create(
                        API + "/files/" + fileId(key) + "?fields=size,trashed&supportsAllDrives=true"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString(), true);
        if (res.statusCode() == 404) return -1L;
        if (res.statusCode() != 200) throw failure("Reading Google Drive file details", res.statusCode(), res.body());
        try {
            JsonNode json = mapper.readTree(res.body());
            if (json.path("trashed").asBoolean(false)) return -1L;
            return json.hasNonNull("size") ? Long.parseLong(json.get("size").asText()) : 0L;
        } catch (IOException e) {
            throw new StorageException("Unreadable response from Google Drive", e);
        }
    }

    // ------------------------------------------------------------------ folders

    /** Finds or creates the folder path under the root, e.g. galleries/{id}/originals. */
    private String ensureFolder(String path) {
        String cached = folderCache.get(path);
        if (cached != null) return cached;
        synchronized (folderLock) {
            String parent = rootFolderId;
            StringBuilder soFar = new StringBuilder();
            for (String segment : path.split("/")) {
                if (segment.isBlank()) continue;
                if (soFar.length() > 0) soFar.append('/');
                soFar.append(segment);
                String known = folderCache.get(soFar.toString());
                if (known == null) {
                    known = findFolder(parent, segment);
                    if (known == null) known = createFolder(parent, segment);
                    folderCache.put(soFar.toString(), known);
                }
                parent = known;
            }
            return parent;
        }
    }

    private String findFolder(String parentId, String name) {
        String q = "name = '" + name.replace("\\", "\\\\").replace("'", "\\'") + "' and '" + parentId
                + "' in parents and mimeType = '" + FOLDER_MIME + "' and trashed = false";
        HttpResponse<String> res = call(token -> HttpRequest.newBuilder(URI.create(API + "/files?q="
                        + URLEncoder.encode(q, StandardCharsets.UTF_8)
                        + "&fields=files(id)&pageSize=1&corpora=allDrives&includeItemsFromAllDrives=true&supportsAllDrives=true"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString(), true);
        if (res.statusCode() != 200) throw failure("Looking up a Google Drive folder", res.statusCode(), res.body());
        try {
            JsonNode files = mapper.readTree(res.body()).path("files");
            return files.isEmpty() ? null : files.get(0).get("id").asText();
        } catch (IOException e) {
            throw new StorageException("Unreadable response from Google Drive", e);
        }
    }

    private String createFolder(String parentId, String name) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "name", name, "mimeType", FOLDER_MIME, "parents", List.of(parentId)));
            HttpResponse<String> res = call(token -> HttpRequest.newBuilder(URI.create(
                            API + "/files?fields=id&supportsAllDrives=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString(), true);
            if (res.statusCode() != 200) throw failure("Creating a Google Drive folder", res.statusCode(), res.body());
            return mapper.readTree(res.body()).get("id").asText();
        } catch (IOException e) {
            throw new StorageException("Could not create a Google Drive folder: " + e.getMessage(), e);
        }
    }

    private String generateId() {
        HttpResponse<String> res = call(token -> HttpRequest.newBuilder(URI.create(
                        API + "/files/generateIds?count=1&space=drive"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString(), true);
        if (res.statusCode() != 200) throw failure("Reserving a Google Drive file id", res.statusCode(), res.body());
        try {
            return mapper.readTree(res.body()).get("ids").get(0).asText();
        } catch (IOException e) {
            throw new StorageException("Unreadable response from Google Drive", e);
        }
    }

    // ------------------------------------------------------------------ HTTP plumbing

    /**
     * Sends a request built from the current access token. A 401 refreshes the token once; with
     * {@code retry}, 429 / 5xx / network errors are retried with exponential backoff.
     */
    private <T> HttpResponse<T> call(Function<String, HttpRequest> build, HttpResponse.BodyHandler<T> handler, boolean retry) {
        int attempts = retry ? MAX_ATTEMPTS : 1;
        boolean refreshed = false;
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<T> res = http.send(build.apply(auth.accessToken()), handler);
                int s = res.statusCode();
                if (s == 401 && !refreshed) {
                    auth.invalidate();
                    refreshed = true;
                    closeQuietly(res.body());
                    attempt--;
                    continue;
                }
                if ((s == 429 || s >= 500) && attempt < attempts) {
                    closeQuietly(res.body());
                    sleep(backoff(attempt));
                    continue;
                }
                return res;
            } catch (IOException e) {
                if (attempt >= attempts) {
                    throw new StorageException("Google Drive is unreachable: " + e.getMessage(), e);
                }
                sleep(backoff(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StorageException("Interrupted while talking to Google Drive", e);
            }
        }
    }

    private HttpRequest.BodyPublisher publisherFor(ContentSource source, long size) {
        if (source.bytes() != null) return HttpRequest.BodyPublishers.ofByteArray(source.bytes());
        return HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return source.open();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }), size);
    }

    /** A message that tells the user what is actually wrong, not just "failed". */
    private StorageException failure(String what, int status, String body) {
        String b = body == null ? "" : body;
        if (b.contains("storageQuotaExceeded")) {
            return new StorageException("Google Drive storage is full. Free up space or add storage, then retry.");
        }
        if (status == 403 && (b.contains("rateLimit") || b.contains("RateLimit"))) {
            return new StorageException("Google Drive is rate limiting uploads. Please retry in a moment.");
        }
        if (status == 401 || status == 403) {
            return new StorageException(what + " was refused by Google (HTTP " + status
                    + "). Check the Drive credentials and that the root folder is shared with the account.");
        }
        if (status == 404) {
            return new StorageException(what + " failed: the file or folder was not found in Google Drive.");
        }
        return new StorageException(what + " failed (HTTP " + status + ").");
    }

    private static Duration transferTimeout(long bytes) {
        // Assume at least ~100 KB/s, never less than two minutes.
        return Duration.ofSeconds(Math.max(120, bytes / 100_000));
    }

    private static long backoff(int attempt) {
        return 500L * (1L << (attempt - 1)) + (long) (Math.random() * 250);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(Object body) {
        if (body instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // nothing useful to do
            }
        }
    }

    private static String safeName(String filename) {
        String n = (filename == null || filename.isBlank()) ? "file" : filename.replace('/', '_').replace('\\', '_');
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }
}
