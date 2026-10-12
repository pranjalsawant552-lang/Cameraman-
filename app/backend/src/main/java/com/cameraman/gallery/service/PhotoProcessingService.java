package com.cameraman.gallery.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.gallery.domain.Gallery;
import com.cameraman.gallery.domain.Photo;
import com.cameraman.gallery.domain.PhotoStatus;
import com.cameraman.gallery.dto.GalleryDto;
import com.cameraman.gallery.repository.GalleryRepository;
import com.cameraman.gallery.repository.PhotoRepository;
import com.cameraman.storage.service.StorageService;
import com.cameraman.storage.service.StorageTier;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Background work for galleries: thumbnail / preview / watermark generation and the
 * Google Drive bulk import.
 *
 * This lives in its OWN bean on purpose. Spring's {@code @Async} works through a proxy,
 * so an {@code @Async} method called from another method of the same class runs
 * synchronously on the caller's thread. Calling these from PhotoUploadService (a different
 * bean) goes through the proxy and really is async.
 *
 * None of these methods rely on a surrounding transaction: every repository call is its
 * own short transaction, and the gallery is loaded with a join fetch so nothing lazy is
 * touched on the executor thread.
 *
 * Memory: camera originals can be 50-100 MB and 40+ megapixels. The original is streamed to a
 * temp file (never held as a byte[]), decoded ONCE to make the preview, and the thumbnail is
 * made from that small preview instead of decoding the original a second and third time. A
 * semaphore caps how many full-size decodes run at once so a burst of uploads cannot exhaust
 * the heap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoProcessingService {

    private final GalleryRepository galleryRepository;
    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final ImageWatermarkService watermarkService;
    private final GoogleDriveImportService googleDriveImportService;

    @Value("${storage.images.thumbnail-size:400}")
    private int thumbnailSize = 400;

    @Value("${storage.images.preview-size:1600}")
    private int previewSize = 1600;

    @Value("${storage.images.preview-quality:0.82}")
    private double previewQuality = 0.82;

    @Value("${storage.images.thumbnail-quality:0.8}")
    private double thumbnailQuality = 0.8;

    @Value("${storage.images.max-concurrent-decodes:2}")
    private int maxConcurrentDecodes = 2;

    private Semaphore decodeGate = new Semaphore(2);

    /** In-memory progress of Drive imports, keyed by gallery id. Lost on restart, which is
     *  fine: it only drives the progress bar, the photos themselves are persisted. */
    private final Map<String, GalleryDto.ImportProgress> importProgress = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        decodeGate = new Semaphore(Math.max(1, maxConcurrentDecodes));
    }

    public GalleryDto.ImportProgress getImportProgress(String galleryId) {
        GalleryDto.ImportProgress p = importProgress.get(galleryId);
        if (p == null) {
            return GalleryDto.ImportProgress.builder().running(false).total(0).done(0).failed(0).skipped(0).build();
        }
        return p;
    }

    public boolean isImportRunning(String galleryId) {
        GalleryDto.ImportProgress p = importProgress.get(galleryId);
        return p != null && p.isRunning();
    }

    @Async("photoProcessingExecutor")
    public void processPhoto(String photoId) {
        Path temp = null;
        try {
            Photo photo = photoRepository.findWithGalleryById(photoId).orElse(null);
            if (photo == null) return;

            photo.setStatus(PhotoStatus.PROCESSING);
            photoRepository.save(photo);

            temp = Files.createTempFile("cameraman-orig-", ".img");
            storageService.downloadToFile(photo.getS3Key(), temp);
            generateDerivatives(photo, temp);
        } catch (Exception e) {
            markFailed(photoId, e);
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * Makes the preview, thumbnail and (if enabled) watermarked preview from a local copy of the
     * original, stores them, and marks the photo READY. Used by {@link #processPhoto} and directly
     * by the Drive import, which already has the file on disk.
     */
    private void generateDerivatives(Photo photo, Path original) throws Exception {
        Gallery gallery = photo.getGallery();
        String galleryId = gallery.getId();

        byte[] preview;
        byte[] thumbnail;
        int[] dims;
        decodeGate.acquire();
        try {
            dims = readDimensions(original, photo.getMimeType());
            preview = resizeFile(original, previewSize, previewQuality, dims);
            thumbnail = resizeBytes(preview, thumbnailSize, thumbnailQuality);
            dims = orientDimensions(dims, preview);
        } finally {
            decodeGate.release();
        }

        // A re-run (retry after a failure) must not orphan the files a previous attempt stored.
        List<String> stale = new ArrayList<>();
        if (photo.getThumbnailKey() != null) stale.add(photo.getThumbnailKey());
        if (photo.getPreviewKey() != null) stale.add(photo.getPreviewKey());
        if (photo.getWatermarkedKey() != null) stale.add(photo.getWatermarkedKey());
        if (!stale.isEmpty()) {
            storageService.deleteFiles(stale);
            photo.setThumbnailKey(null);
            photo.setPreviewKey(null);
            photo.setWatermarkedKey(null);
        }

        long stamp = System.currentTimeMillis();
        photo.setThumbnailKey(storageService.uploadBytes(StorageTier.DERIVATIVES, thumbnail,
                "galleries/" + galleryId + "/thumbnails", "thumb_" + stamp + ".jpg", "image/jpeg").getKey());
        photo.setPreviewKey(storageService.uploadBytes(StorageTier.DERIVATIVES, preview,
                "galleries/" + galleryId + "/previews", "preview_" + stamp + ".jpg", "image/jpeg").getKey());

        // The watermark goes on the small preview, never the full-resolution original:
        // clients only ever browse the preview, and watermarking the original would
        // silently store a second near-full-size copy of every photo.
        if (gallery.isWatermarkEnabled()) {
            byte[] watermarked = watermarkService.applyWatermark(preview, gallery.getClientName());
            photo.setWatermarkedKey(storageService.uploadBytes(StorageTier.DERIVATIVES, watermarked,
                    "galleries/" + galleryId + "/watermarked", "wm_" + stamp + ".jpg", "image/jpeg").getKey());
        }

        if (photo.getWidth() == null || photo.getHeight() == null) {
            photo.setWidth(dims[0]);
            photo.setHeight(dims[1]);
        }
        photo.setFailureReason(null);
        photo.setStatus(PhotoStatus.READY);
        photoRepository.save(photo);
    }

    private void markFailed(String photoId, Exception e) {
        log.error("Error processing photo {}: {}", photoId, e.getMessage(), e);
        String reason = e instanceof UnsupportedImageException ? e.getMessage()
                : "Could not prepare previews: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        final String shortReason = reason.length() > 300 ? reason.substring(0, 300) : reason;
        photoRepository.findById(photoId).ifPresent(p -> {
            p.setStatus(PhotoStatus.FAILED);
            p.setFailureReason(shortReason);
            photoRepository.save(p);
        });
    }

    /** Re-runs processing for every FAILED photo in a gallery. Returns how many were queued. */
    public int retryFailed(String galleryId) {
        List<Photo> failed = photoRepository.findByGalleryIdAndStatusOrderBySortOrderAsc(galleryId, PhotoStatus.FAILED);
        for (Photo p : failed) {
            processPhoto(p.getId());
        }
        return failed.size();
    }

    // ------------------------------------------------------------------ Google Drive import

    @Async("photoProcessingExecutor")
    public void importFromDrive(String galleryId, List<GoogleDriveImportService.DriveFile> files) {
        GalleryDto.ImportProgress progress = GalleryDto.ImportProgress.builder()
                .running(true).total(files.size()).done(0).failed(0).skipped(0).build();
        importProgress.put(galleryId, progress);

        Gallery gallery = galleryRepository.findById(galleryId).orElse(null);
        if (gallery == null) {
            log.error("Drive import: gallery {} no longer exists, aborting", galleryId);
            progress.setRunning(false);
            return;
        }

        for (GoogleDriveImportService.DriveFile file : files) {
            Path temp = null;
            try {
                // Importing the same Drive file twice would store the same photo twice.
                String fingerprint = "drive:" + file.id();
                if (photoRepository.existsByGalleryIdAndUploadFingerprint(galleryId, fingerprint)) {
                    progress.setSkipped(progress.getSkipped() + 1);
                    continue;
                }
                if (!PhotoUploadService.ALLOWED_TYPES.contains(file.mimeType())) {
                    throw new BusinessException("Unsupported file type: " + file.mimeType(), "INVALID_FILE_TYPE");
                }
                if (file.size() != null && file.size() > PhotoUploadService.MAX_FILE_SIZE) {
                    throw new BusinessException("File size exceeds 100MB limit", "FILE_TOO_LARGE");
                }

                temp = Files.createTempFile("cameraman-import-", ".img");
                googleDriveImportService.downloadToFile(file.id(), temp);
                long size = Files.size(temp);
                if (size == 0) throw new BusinessException("File is empty", "EMPTY_FILE");
                if (size > PhotoUploadService.MAX_FILE_SIZE) {
                    throw new BusinessException("File size exceeds 100MB limit", "FILE_TOO_LARGE");
                }

                String key = storageService.uploadPath(StorageTier.ORIGINALS, temp,
                        "galleries/" + galleryId + "/originals", file.name(), file.mimeType()).getKey();
                Photo photo = photoRepository.save(Photo.builder()
                        .gallery(gallery)
                        .originalFilename(file.name())
                        .s3Key(key)
                        .uploadFingerprint(fingerprint)
                        .fileSizeBytes(size)
                        .mimeType(file.mimeType())
                        .status(PhotoStatus.PROCESSING)
                        .build());
                try {
                    // The file is already on disk: no need to download it again from storage.
                    generateDerivatives(photo, temp);
                } catch (Exception e) {
                    markFailed(photo.getId(), e);
                }
                progress.setDone(progress.getDone() + 1);
            } catch (Exception e) {
                log.error("Drive import: failed to import '{}' into gallery {}: {}", file.name(), galleryId, e.getMessage());
                progress.setFailed(progress.getFailed() + 1);
            } finally {
                deleteQuietly(temp);
            }
        }

        refreshStats(galleryId);
        progress.setRunning(false);
        log.info("Drive import for gallery {} finished: {} imported, {} skipped (already there), {} failed of {}",
                galleryId, progress.getDone(), progress.getSkipped(), progress.getFailed(), files.size());
    }

    /** Recomputes the gallery's photo count and total size from its non-pending photos. */
    public void refreshStats(String galleryId) {
        galleryRepository.findById(galleryId).ifPresent(g -> {
            g.setPhotoCount((int) photoRepository.countByGalleryIdAndStatusNot(galleryId, PhotoStatus.UPLOADING));
            Long total = photoRepository.sumFileSizeByGallery(galleryId);
            g.setTotalSizeBytes(total != null ? total : 0L);
            galleryRepository.save(g);
        });
    }

    // ------------------------------------------------------------------ image helpers

    /** Thrown when no decoder exists for the file; the message is shown to the user as-is. */
    static class UnsupportedImageException extends IOException {
        UnsupportedImageException(String message) {
            super(message);
        }
    }

    /** Pixel size straight from the file header, without decoding the image. */
    static int[] readDimensions(Path file, String mimeType) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                boolean heic = mimeType != null && (mimeType.contains("heic") || mimeType.contains("heif"));
                throw new UnsupportedImageException(heic
                        ? "HEIC/HEIF photos can't be turned into previews on the server. The original is stored safely; export it as JPEG to get previews."
                        : "This image format can't be turned into previews. Use JPEG, PNG or TIFF.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    /** Camera photos are often stored sideways with an EXIF rotation flag. The preview is made
     *  upright, so if its orientation differs from the raw header, swap the reported size. */
    static int[] orientDimensions(int[] raw, byte[] uprightPreview) throws IOException {
        var img = ImageIO.read(new ByteArrayInputStream(uprightPreview));
        if (img == null) return raw;
        boolean rawLandscape = raw[0] > raw[1];
        boolean previewLandscape = img.getWidth() > img.getHeight();
        boolean square = raw[0] == raw[1] || img.getWidth() == img.getHeight();
        return (!square && rawLandscape != previewLandscape) ? new int[]{raw[1], raw[0]} : raw;
    }

    private static byte[] resizeFile(Path file, int size, double quality, int[] dims) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var builder = Thumbnails.of(file.toFile());
        // Never enlarge a small image: that only makes the preview bigger to store and send.
        var sized = Math.max(dims[0], dims[1]) <= size ? builder.scale(1.0) : builder.size(size, size).keepAspectRatio(true);
        sized.outputFormat("jpg").outputQuality(quality).toOutputStream(out);
        return out.toByteArray();
    }

    private static byte[] resizeBytes(byte[] imageBytes, int size, double quality) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(imageBytes))
                .size(size, size)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .outputQuality(quality)
                .toOutputStream(out);
        return out.toByteArray();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete temp file {}: {}", path, e.getMessage());
        }
    }
}
