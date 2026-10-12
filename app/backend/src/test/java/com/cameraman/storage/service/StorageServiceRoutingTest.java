package com.cameraman.storage.service;

import com.cameraman.storage.provider.GoogleDriveStorageProvider;
import com.cameraman.storage.provider.S3StorageProvider;
import com.cameraman.storage.provider.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Which provider gets which file, and how a stored key finds its way back to its provider. */
class StorageServiceRoutingTest {

    private S3StorageProvider s3;
    private GoogleDriveStorageProvider drive;
    private SignedLinkService links;
    private StorageService service;

    @BeforeEach
    void setUp() {
        s3 = mock(S3StorageProvider.class);
        drive = mock(GoogleDriveStorageProvider.class);
        links = new SignedLinkService("test-secret-test-secret-test-secret", "http://localhost:8080/api");
        service = new StorageService(s3, drive, links);
        when(s3.id()).thenReturn("s3");
        when(drive.id()).thenReturn("gdrive");
        when(drive.owns(anyString())).thenAnswer(i -> ((String) i.getArgument(0)).startsWith("gdrive:"));
    }

    @Test
    void originalsGoToDriveWhenConfigured() {
        when(drive.isAvailable()).thenReturn(true);
        assertThat(service.providerFor(StorageTier.ORIGINALS)).isSameAs(drive);
    }

    @Test
    void originalsFallBackToS3WhenDriveIsNotConfigured() {
        when(drive.isAvailable()).thenReturn(false);
        assertThat(service.providerFor(StorageTier.ORIGINALS)).isSameAs(s3);
    }

    @Test
    void derivativesDocumentsAndPublicStayOnS3EvenWithDrive() {
        when(drive.isAvailable()).thenReturn(true);
        assertThat(service.providerFor(StorageTier.DERIVATIVES)).isSameAs(s3);
        assertThat(service.providerFor(StorageTier.DOCUMENTS)).isSameAs(s3);
        assertThat(service.providerFor(StorageTier.PUBLIC)).isSameAs(s3);
    }

    @Test
    void publicTierCanNeverBeRoutedToDrive() {
        when(drive.isAvailable()).thenReturn(true);
        ReflectionTestUtils.setField(service, "originalsRoute", "gdrive");
        ReflectionTestUtils.setField(service, "documentsRoute", "gdrive");
        assertThat(service.providerFor(StorageTier.PUBLIC)).isSameAs(s3);
        assertThat(service.providerFor(StorageTier.DOCUMENTS)).isSameAs(drive);
    }

    @Test
    void explicitS3RoutingKeepsOriginalsOnS3() {
        when(drive.isAvailable()).thenReturn(true);
        ReflectionTestUtils.setField(service, "originalsRoute", "s3");
        assertThat(service.providerFor(StorageTier.ORIGINALS)).isSameAs(s3);
    }

    @Test
    void keysResolveToTheirProvider() {
        assertThat(service.providerForKey("gdrive:1AbC")).isSameAs(drive);
        // Every key written before multi-provider support has no prefix and must stay on S3.
        assertThat(service.providerForKey("galleries/g1/originals/abc_photo.jpg")).isSameAs(s3);
    }

    @Test
    void tiersAreInferredFromLegacyFolderNames() {
        assertThat(StorageTier.forFolder("galleries/g1/originals")).isEqualTo(StorageTier.ORIGINALS);
        assertThat(StorageTier.forFolder("galleries/g1/thumbnails")).isEqualTo(StorageTier.DERIVATIVES);
        assertThat(StorageTier.forFolder("galleries/g1/previews")).isEqualTo(StorageTier.DERIVATIVES);
        assertThat(StorageTier.forFolder("galleries/g1/watermarked")).isEqualTo(StorageTier.DERIVATIVES);
        assertThat(StorageTier.forFolder("invoices")).isEqualTo(StorageTier.DOCUMENTS);
        assertThat(StorageTier.forFolder("inventory/qrcodes")).isEqualTo(StorageTier.PUBLIC);
        assertThat(StorageTier.forFolder("cms")).isEqualTo(StorageTier.PUBLIC);
    }

    @Test
    void s3KeysGetNativePresignedUrlsAndDriveKeysGetSignedApiLinks() {
        when(s3.supportsSignedUrls()).thenReturn(true);
        when(s3.signedDownloadUrl(anyString(), any(Duration.class))).thenReturn("https://bucket/signed");
        when(drive.supportsSignedUrls()).thenReturn(false);

        assertThat(service.generateSignedDownloadUrl("galleries/g/previews/p.jpg")).isEqualTo("https://bucket/signed");
        String driveUrl = service.generateSignedDownloadUrl("gdrive:1AbC");
        assertThat(driveUrl).startsWith("http://localhost:8080/api/files/");
        // The Drive file id must never be handed out as a Drive URL.
        assertThat(driveUrl).doesNotContain("drive.google.com").doesNotContain("googleapis.com");
    }

    @Test
    void deleteFilesSplitsKeysByProvider() {
        service.deleteFiles(List.of("galleries/a.jpg", "gdrive:1", "galleries/b.jpg", "gdrive:2"));
        verify(s3).deleteAll(List.of("galleries/a.jpg", "galleries/b.jpg"));
        verify(drive).deleteAll(List.of("gdrive:1", "gdrive:2"));
    }

    @Test
    void providerInterfaceIsImplementedByBoth() {
        assertThat(StorageProvider.class).isAssignableFrom(S3StorageProvider.class);
        assertThat(StorageProvider.class).isAssignableFrom(GoogleDriveStorageProvider.class);
    }
}
