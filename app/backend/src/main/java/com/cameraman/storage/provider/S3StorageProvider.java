package com.cameraman.storage.provider;

import com.cameraman.common.exception.StorageException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * S3-compatible storage (AWS S3, Cloudflare R2, iDrive e2, MinIO). This is the code that used to
 * live directly in StorageService, moved here unchanged in behaviour so it is one provider among
 * several. Keys it creates have no prefix; any key without a provider prefix belongs to S3, which
 * is what keeps every photo stored before the multi-provider change working.
 */
@Slf4j
@Component
public class S3StorageProvider implements StorageProvider {

    @Value("${storage.s3.endpoint}")
    private String endpoint;

    @Value("${storage.s3.bucket}")
    private String bucket;

    @Value("${storage.s3.region}")
    private String region;

    @Value("${storage.s3.access-key}")
    private String accessKey;

    @Value("${storage.s3.secret-key}")
    private String secretKey;

    @Value("${storage.s3.signed-url-expiry:3600}")
    private long signedUrlExpiry;

    /** The real public, unauthenticated URL base for the bucket. A separate setting from
     *  {@code endpoint}: on Cloudflare R2 the S3 API endpoint needs SigV4 on every request, so it
     *  is NOT a public URL; the pub-&lt;hash&gt;.r2.dev domain or a custom domain is. Left blank,
     *  {@link #getPublicUrl} falls back to endpoint+bucket+key, correct only for an open MinIO or
     *  a public-read AWS bucket. */
    @Value("${storage.s3.public-base-url:}")
    private String publicBaseUrl;

    /** Endpoint baked into presigned URLs handed to the BROWSER, when it differs from the one
     *  the backend uses (MinIO in Docker: backend http://minio:9000, browser localhost:9000). */
    @Value("${storage.s3.presign-endpoint:}")
    private String presignEndpoint;

    @Value("${storage.s3.path-style:true}")
    private boolean pathStyle;

    private S3Client s3Client;
    private S3Presigner presigner;

    @PostConstruct
    public void init() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region))
                .forcePathStyle(pathStyle)
                .build();

        String signingEndpoint = (presignEndpoint == null || presignEndpoint.isBlank())
                ? endpoint : presignEndpoint;

        presigner = S3Presigner.builder()
                .endpointOverride(URI.create(signingEndpoint))
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build())
                .build();

        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            log.warn("storage.s3.public-base-url is not configured. Assets that need a genuinely public, " +
                    "unauthenticated URL (inventory QR codes, WhatsApp broadcast media) will fall back to the " +
                    "raw S3 endpoint, which requires SigV4 auth on Cloudflare R2 and real AWS S3 - those links " +
                    "will not load in a browser or be fetchable by WhatsApp's servers. Set S3_PUBLIC_BASE_URL " +
                    "to your bucket's public domain to fix this. Private content (gallery photos, invoice " +
                    "PDFs) is unaffected; it uses time-limited signed URLs.");
        }
    }

    @Override public String id() { return "s3"; }
    @Override public boolean isAvailable() { return true; }
    /** S3 is the default home of every key that carries no other provider prefix. */
    @Override public boolean owns(String key) { return key != null && !key.contains(":"); }
    @Override public boolean supportsSignedUrls() { return true; }
    @Override public boolean supportsPresignedUpload() { return true; }

    @Override
    public String newKey(String folder, String filename) {
        return folder + "/" + UUID.randomUUID() + "_" + sanitize(filename);
    }

    @Override
    public String store(String folder, String filename, String contentType, ContentSource source, long size) {
        String key = newKey(folder, filename);
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket).key(key).contentType(contentType).contentLength(size).build();
            if (source.bytes() != null) {
                s3Client.putObject(putRequest, RequestBody.fromBytes(source.bytes()));
            } else if (source.path() != null) {
                s3Client.putObject(putRequest, RequestBody.fromFile(source.path()));
            } else {
                try (InputStream in = source.open()) {
                    s3Client.putObject(putRequest, RequestBody.fromInputStream(in, size));
                }
            }
            return key;
        } catch (Exception e) {
            log.error("Failed to upload file to S3 storage: {}", e.getMessage(), e);
            throw new StorageException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    @Override
    public StoredObject open(String key) {
        try {
            ResponseInputStream<GetObjectResponse> in = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            GetObjectResponse head = in.response();
            return new StoredObject(in, head.contentType(), head.contentLength() == null ? -1 : head.contentLength());
        } catch (Exception e) {
            throw new StorageException("Failed to download file: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.error("Failed to delete file with key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void deleteAll(List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        // S3 accepts at most 1000 keys per DeleteObjects request.
        for (int i = 0; i < keys.size(); i += 1000) {
            List<String> batch = keys.subList(i, Math.min(keys.size(), i + 1000));
            try {
                List<ObjectIdentifier> ids = batch.stream()
                        .map(k -> ObjectIdentifier.builder().key(k).build()).toList();
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket).delete(Delete.builder().objects(ids).build()).build());
            } catch (Exception e) {
                log.error("Failed to delete {} files: {}", batch.size(), e.getMessage());
            }
        }
    }

    @Override
    public long size(String key) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength();
        } catch (NoSuchKeyException e) {
            return -1L;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return -1L;
            throw new StorageException("Failed to read file metadata: " + key, e);
        } catch (Exception e) {
            throw new StorageException("Failed to read file metadata: " + key, e);
        }
    }

    @Override
    public String signedDownloadUrl(String key, Duration ttl) {
        try {
            GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl == null ? Duration.ofSeconds(signedUrlExpiry) : ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build();
            return presigner.presignGetObject(req).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate signed URL for key {}: {}", key, e.getMessage());
            throw new StorageException("Failed to generate download URL", e);
        }
    }

    @Override
    public String signedUploadUrl(String key, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket).key(key).contentType(contentType).build();
            return presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(60))
                    .putObjectRequest(putRequest)
                    .build()).url().toString();
        } catch (Exception e) {
            throw new StorageException("Failed to generate upload URL", e);
        }
    }

    @Override
    public String copy(String sourceKey, String destinationKey) {
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket).sourceKey(sourceKey)
                    .destinationBucket(bucket).destinationKey(destinationKey).build());
            return getPublicUrl(destinationKey);
        } catch (Exception e) {
            throw new StorageException("Failed to copy file", e);
        }
    }

    // Package-private so it can be unit-tested without a live S3Client: pure string building.
    String getPublicUrl(String key) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
            return base + "/" + key;
        }
        return endpoint + "/" + bucket + "/" + key;
    }

    /** Public, unauthenticated URL for an object in the bucket (PUBLIC tier assets only). */
    public String publicUrl(String key) { return getPublicUrl(key); }

    public String bucketName() { return bucket; }

    static String sanitize(String filename) {
        String name = (filename == null || filename.isBlank()) ? "file" : filename;
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.length() > 150 ? name.substring(name.length() - 150) : name;
    }
}
