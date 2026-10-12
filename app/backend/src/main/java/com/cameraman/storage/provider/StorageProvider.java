package com.cameraman.storage.provider;

import java.time.Duration;
import java.util.List;

/**
 * One storage backend (S3-compatible, Google Drive, ...). Everything provider-specific lives
 * behind this interface; the rest of the application only ever talks to
 * {@link com.cameraman.storage.service.StorageService}, which picks a provider per tier and per
 * stored key. To add a backend, implement this interface, make it a Spring bean, and teach
 * {@code StorageService} its id and key prefix.
 */
public interface StorageProvider {

    /** Stable id used in configuration, e.g. {@code s3} or {@code gdrive}. */
    String id();

    /** False when the provider has no credentials / is switched off. */
    boolean isAvailable();

    /** True when this provider created {@code key} (keys are self-describing). */
    boolean owns(String key);

    /** Stores the content and returns the new storage key. */
    String store(String folder, String filename, String contentType, ContentSource source, long size);

    /** Opens a stored object for reading. */
    StoredObject open(String key);

    /** Deletes one object. Missing objects and transient errors are logged, never thrown. */
    void delete(String key);

    default void deleteAll(List<String> keys) {
        for (String key : keys) delete(key);
    }

    /** Size in bytes, or -1 when the object does not exist. */
    long size(String key);

    /** True if this provider can mint expiring URLs the browser can open directly. */
    default boolean supportsSignedUrls() { return false; }

    default String signedDownloadUrl(String key, Duration ttl) {
        throw new UnsupportedOperationException(id() + " cannot sign download URLs");
    }

    /** True if the browser can PUT a file straight to this provider via a presigned URL. */
    default boolean supportsPresignedUpload() { return false; }

    default String newKey(String folder, String filename) {
        throw new UnsupportedOperationException(id() + " does not support presigned uploads");
    }

    default String signedUploadUrl(String key, String contentType) {
        throw new UnsupportedOperationException(id() + " does not support presigned uploads");
    }

    default String copy(String sourceKey, String destinationKey) {
        throw new UnsupportedOperationException(id() + " cannot copy objects");
    }
}
