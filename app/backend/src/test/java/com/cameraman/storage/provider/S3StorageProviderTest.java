package com.cameraman.storage.provider;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers S3StorageProvider.getPublicUrl() specifically (moved here from StorageService) — this is the method that had a real, live bug: it
 * built URLs as endpoint+bucket+key, which works by accident on a locally-open MinIO
 * instance but does NOT work on Cloudflare R2 (the S3 API endpoint requires SigV4 auth on
 * every request, GETs included — it isn't a public URL there). Two real call sites depend
 * on this being right: InventoryService's QR code images and BroadcastService's WhatsApp
 * media uploads, both of which need a URL that actually loads with no auth header.
 *
 * getPublicUrl() is deliberately package-private (not private) so this test can call it
 * directly — it's pure string building with no network calls, so there's no need to spin
 * up a mocked S3Client just to verify string concatenation logic.
 */
class S3StorageProviderTest {

    private S3StorageProvider newService(String endpoint, String bucket, String publicBaseUrl) {
        S3StorageProvider service = new S3StorageProvider();
        ReflectionTestUtils.setField(service, "endpoint", endpoint);
        ReflectionTestUtils.setField(service, "bucket", bucket);
        ReflectionTestUtils.setField(service, "publicBaseUrl", publicBaseUrl);
        return service;
    }

    @Test
    void publicBaseUrlConfigured_isUsedDirectly() {
        // The R2-correct path: a real public delivery domain, distinct from the API endpoint.
        S3StorageProvider service = newService(
                "https://myaccount.r2.cloudflarestorage.com", "cameraman-photos",
                "https://pub-abc123.r2.dev");

        String url = service.getPublicUrl("inventory/qrcodes/SKU001-qr.png");

        assertThat(url).isEqualTo("https://pub-abc123.r2.dev/inventory/qrcodes/SKU001-qr.png");
        // Must NOT contain the bucket name or the authenticated API endpoint — a
        // pub-*.r2.dev URL has neither in its path.
        assertThat(url).doesNotContain("r2.cloudflarestorage.com");
        assertThat(url).doesNotContain("cameraman-photos");
    }

    @Test
    void publicBaseUrlWithTrailingSlash_doesNotProduceDoubleSlash() {
        S3StorageProvider service = newService(
                "https://myaccount.r2.cloudflarestorage.com", "cameraman-photos",
                "https://pub-abc123.r2.dev/");

        String url = service.getPublicUrl("broadcasts/photo.jpg");

        assertThat(url).isEqualTo("https://pub-abc123.r2.dev/broadcasts/photo.jpg");
        assertThat(url).doesNotContain("//broadcasts");
    }

    @Test
    void noPublicBaseUrlConfigured_fallsBackToEndpointAndBucket() {
        // The old behaviour, kept as a fallback for local MinIO (where the S3 endpoint
        // itself commonly is reachable) — but this is the shape that's WRONG on R2, which
        // is exactly why the fallback logs a startup warning (see init()) rather than
        // silently pretending it's fine.
        S3StorageProvider service = newService(
                "http://localhost:9000", "cameraman-photos", "");

        String url = service.getPublicUrl("galleries/g1/photo.jpg");

        assertThat(url).isEqualTo("http://localhost:9000/cameraman-photos/galleries/g1/photo.jpg");
    }

    @Test
    void blankPublicBaseUrl_treatedSameAsNull() {
        S3StorageProvider serviceBlank = newService("http://localhost:9000", "bucket", "   ");
        S3StorageProvider serviceNull = newService("http://localhost:9000", "bucket", null);

        assertThat(serviceBlank.getPublicUrl("k")).isEqualTo(serviceNull.getPublicUrl("k"));
    }
}
