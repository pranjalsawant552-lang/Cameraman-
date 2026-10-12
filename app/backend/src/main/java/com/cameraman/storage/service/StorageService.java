package com.cameraman.storage.service;

import com.cameraman.common.exception.StorageException;
import com.cameraman.storage.dto.StorageDto;
import com.cameraman.storage.provider.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The single entry point for all file storage. Application code never talks to S3 or Google
 * Drive directly: it calls this service, which picks the right {@link StorageProvider}.
 *
 * <b>Where things go</b> (all configurable, see {@code storage.routing.*} in application.yml):
 * <ul>
 *   <li>ORIGINALS: Google Drive when configured, otherwise S3 ({@code auto}).</li>
 *   <li>DERIVATIVES (thumbnails, previews, watermarked previews): S3. Tiny, hot, and the browser
 *       needs fast native signed URLs.</li>
 *   <li>DOCUMENTS (invoice PDFs): S3. The server re-reads them to email and re-send.</li>
 *   <li>PUBLIC (CMS images, QR codes, broadcast media): always S3. Needs an unauthenticated URL
 *       Drive cannot provide without making files public.</li>
 * </ul>
 * <b>Where a key lives</b> is read off the key itself: {@code gdrive:<id>} is Drive, anything
 * else is S3. That is why photos stored before this change keep working with no data migration.
 */
@Slf4j
@Service
public class StorageService {

    private final S3StorageProvider s3;
    private final GoogleDriveStorageProvider drive;
    private final SignedLinkService signedLinks;

    @Value("${storage.routing.originals:auto}")
    private String originalsRoute = "auto";

    @Value("${storage.routing.derivatives:s3}")
    private String derivativesRoute = "s3";

    @Value("${storage.routing.documents:s3}")
    private String documentsRoute = "s3";

    @Value("${storage.s3.signed-url-expiry:3600}")
    private long signedUrlExpiry = 3600;

    public StorageService(S3StorageProvider s3, GoogleDriveStorageProvider drive, SignedLinkService signedLinks) {
        this.s3 = s3;
        this.drive = drive;
        this.signedLinks = signedLinks;
    }

    @PostConstruct
    void logRouting() {
        log.info("Storage routing: originals={}, derivatives={}, documents={}, public=s3 (Google Drive {})",
                providerFor(StorageTier.ORIGINALS).id(), providerFor(StorageTier.DERIVATIVES).id(),
                providerFor(StorageTier.DOCUMENTS).id(), drive.isAvailable() ? "configured" : "not configured");
    }

    // ------------------------------------------------------------------ routing

    StorageProvider providerFor(StorageTier tier) {
        String wanted = switch (tier) {
            case ORIGINALS -> originalsRoute;
            case DERIVATIVES -> derivativesRoute;
            case DOCUMENTS -> documentsRoute;
            case PUBLIC -> "s3"; // must stay reachable without auth, which Drive cannot do privately
        };
        boolean wantsDrive = "gdrive".equalsIgnoreCase(wanted) || ("auto".equalsIgnoreCase(wanted) && tier == StorageTier.ORIGINALS);
        if (wantsDrive) {
            if (drive.isAvailable()) return drive;
            if ("gdrive".equalsIgnoreCase(wanted)) {
                log.warn("storage.routing for {} is 'gdrive' but Google Drive storage is not configured; using S3", tier);
            }
        }
        return s3;
    }

    StorageProvider providerForKey(String key) {
        if (key == null || key.isBlank()) throw new StorageException("A storage key is required");
        return drive.owns(key) ? drive : s3;
    }

    // ------------------------------------------------------------------ upload

    public StorageDto.UploadResponse uploadFile(InputStream inputStream, String folder,
                                                String originalFilename, String contentType, long size) {
        return upload(StorageTier.forFolder(folder), folder, originalFilename, contentType,
                ContentSource.ofStream(inputStream), size);
    }

    public StorageDto.UploadResponse uploadFile(StorageTier tier, InputStream inputStream, String folder,
                                                String originalFilename, String contentType, long size) {
        return upload(tier, folder, originalFilename, contentType, ContentSource.ofStream(inputStream), size);
    }

    public StorageDto.UploadResponse uploadBytes(byte[] bytes, String folder, String filename, String contentType) {
        return upload(StorageTier.forFolder(folder), folder, filename, contentType,
                ContentSource.ofBytes(bytes), bytes.length);
    }

    public StorageDto.UploadResponse uploadBytes(StorageTier tier, byte[] bytes, String folder,
                                                 String filename, String contentType) {
        return upload(tier, folder, filename, contentType, ContentSource.ofBytes(bytes), bytes.length);
    }

    /** Uploads a local file without loading it into memory; retryable on providers that retry. */
    public StorageDto.UploadResponse uploadPath(StorageTier tier, Path file, String folder,
                                                String filename, String contentType) {
        try {
            return upload(tier, folder, filename, contentType, ContentSource.ofPath(file), Files.size(file));
        } catch (IOException e) {
            throw new StorageException("Failed to read file for upload: " + e.getMessage(), e);
        }
    }

    private StorageDto.UploadResponse upload(StorageTier tier, String folder, String filename,
                                             String contentType, ContentSource source, long size) {
        StorageProvider provider = providerFor(tier);
        String key = provider.store(folder, filename, contentType, source, size);
        boolean isS3 = provider == s3;
        return StorageDto.UploadResponse.builder()
                .key(key)
                // Only S3 has a public URL, and only the PUBLIC tier is meant to be read through it.
                // A Drive object deliberately has none.
                .url(isS3 && tier == StorageTier.PUBLIC ? s3.publicUrl(key) : null)
                .bucket(isS3 ? s3.bucketName() : provider.id())
                .contentType(contentType)
                .sizeBytes(size)
                .build();
    }

    // ------------------------------------------------------------------ browser-direct upload

    /** True when originals go to a provider that needs chunked uploads relayed by the backend. */
    public boolean originalsUseResumableUploads() {
        return providerFor(StorageTier.ORIGINALS) instanceof ResumableUploads;
    }

    public ResumableUploads.Session beginResumableOriginal(String folder, String filename,
                                                           String contentType, long totalSize) {
        if (!(providerFor(StorageTier.ORIGINALS) instanceof ResumableUploads resumable)) {
            throw new StorageException("The originals storage does not support resumable uploads");
        }
        return resumable.beginResumable(folder, filename, contentType, totalSize);
    }

    public ResumableUploads.Progress uploadChunk(String sessionUri, long offset, byte[] chunk, long total) {
        return drive.uploadChunk(sessionUri, offset, chunk, total);
    }

    public ResumableUploads.Progress resumableProgress(String sessionUri, long total) {
        return drive.queryProgress(sessionUri, total);
    }

    /** A fresh storage key for a presigned (S3) direct upload of an original. */
    public String newPresignedOriginalKey(String folder, String filename) {
        return s3.newKey(folder, filename);
    }

    public String generateSignedUploadUrl(String key, String contentType) {
        return s3.signedUploadUrl(key, contentType);
    }

    // ------------------------------------------------------------------ download

    /**
     * A time-limited URL the browser can open. S3 objects get a native presigned URL; Drive
     * objects get a signed link to this backend's own /files endpoint. Callers must authorise the
     * user BEFORE asking for one: possession of the URL is the access.
     */
    public String generateSignedDownloadUrl(String key) {
        StorageProvider provider = providerForKey(key);
        Duration ttl = Duration.ofSeconds(signedUrlExpiry);
        return provider.supportsSignedUrls() ? provider.signedDownloadUrl(key, ttl) : signedLinks.createUrl(key, ttl);
    }

    public StoredObject open(String key) {
        return providerForKey(key).open(key);
    }

    public byte[] downloadFile(String key) {
        try (StoredObject object = open(key)) {
            return object.stream().readAllBytes();
        } catch (IOException e) {
            throw new StorageException("Failed to download file: " + key, e);
        }
    }

    /** Streams an object to a local file (never fully in memory). */
    public void downloadToFile(String key, Path target) {
        try (StoredObject object = open(key)) {
            Files.copy(object.stream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to download file: " + key, e);
        }
    }

    // ------------------------------------------------------------------ housekeeping

    public void deleteFile(String key) {
        if (key == null || key.isBlank()) return;
        providerForKey(key).delete(key);
    }

    public void deleteFiles(List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        List<String> driveKeys = new ArrayList<>();
        List<String> s3Keys = new ArrayList<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            (drive.owns(key) ? driveKeys : s3Keys).add(key);
        }
        if (!s3Keys.isEmpty()) s3.deleteAll(s3Keys);
        if (!driveKeys.isEmpty()) drive.deleteAll(driveKeys);
    }

    public boolean fileExists(String key) {
        return getFileSize(key) >= 0;
    }

    /** Size in bytes of a stored object, or -1 when it does not exist. */
    public long getFileSize(String key) {
        return providerForKey(key).size(key);
    }

    /** Server-side copy; S3 only (used for public assets). */
    public String copyFile(String sourceKey, String destinationKey) {
        return s3.copy(sourceKey, destinationKey);
    }
}
