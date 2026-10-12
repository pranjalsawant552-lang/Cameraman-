package com.cameraman.gallery.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.gallery.domain.*;
import com.cameraman.gallery.dto.GalleryDto;
import com.cameraman.gallery.dto.PhotoDto;
import com.cameraman.gallery.repository.GalleryRepository;
import com.cameraman.gallery.repository.PhotoRepository;
import com.cameraman.storage.provider.ResumableUploads;
import com.cameraman.storage.provider.UploadSessionExpiredException;
import com.cameraman.storage.service.StorageService;
import com.cameraman.storage.service.StorageTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Photo ingestion: browser multipart upload, direct-to-storage (presigned) upload and
 * Google Drive import, plus the originals-expiry purge. The slow image work
 * (thumbnails / watermark) happens in {@link PhotoProcessingService}, which is a
 * separate bean so its {@code @Async} methods really run in the background.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoUploadService {

    private final GalleryRepository galleryRepository;
    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final PhotoProcessingService processingService;
    private final com.cameraman.photoselection.repository.PhotoSelectionRepository photoSelectionRepository;
    private final com.cameraman.photoselection.repository.SelectedPhotoRepository selectedPhotoRepository;
    private final GoogleDriveImportService googleDriveImportService;

    static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/tiff", "image/heic"
    );
    static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100MB

    /** Direct uploads that were never confirmed are abandoned after this many hours. */
    private static final int STALE_UPLOAD_HOURS = 3;

    // ------------------------------------------------------------------ browser multipart

    @Transactional
    public List<PhotoDto.PhotoResponse> uploadPhotos(String galleryId, List<MultipartFile> files, String uploadedBy) {
        Gallery gallery = findGallery(galleryId);

        List<Photo> savedPhotos = new ArrayList<>();
        List<PhotoDto.PhotoResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                validateFile(file);
                Photo photo = processAndSavePhoto(gallery, file);
                savedPhotos.add(photo);
                responses.add(toResponse(photo));
            } catch (Exception e) {
                log.error("Failed to upload photo {}: {}", file.getOriginalFilename(), e.getMessage());
                // Continue with the other files
            }
        }

        refreshStats(gallery);
        List<String> ids = savedPhotos.stream().map(Photo::getId).toList();
        afterCommit(() -> ids.forEach(processingService::processPhoto));
        return responses;
    }

    @Transactional
    public PhotoDto.PhotoResponse uploadSinglePhoto(String galleryId, MultipartFile file) {
        return uploadSinglePhoto(galleryId, file, null);
    }

    @Transactional
    public PhotoDto.PhotoResponse uploadSinglePhoto(String galleryId, MultipartFile file, String fingerprint) {
        Gallery gallery = findGallery(galleryId);
        validateFile(file);
        String fp = cleanFingerprint(fingerprint);
        if (fp != null) {
            Photo existing = photoRepository
                    .findFirstByGalleryIdAndUploadFingerprintOrderByCreatedAtAsc(galleryId, fp).orElse(null);
            if (existing != null && existing.getStatus() != PhotoStatus.UPLOADING && existing.getStatus() != PhotoStatus.FAILED) {
                return toResponse(existing); // already in the gallery: do not store it twice
            }
        }
        Photo photo = processAndSavePhoto(gallery, file);
        photo.setUploadFingerprint(fp);
        photoRepository.save(photo);
        refreshStats(gallery);
        String id = photo.getId();
        afterCommit(() -> processingService.processPhoto(id));
        return toResponse(photo);
    }

    // ------------------------------------------------------------------ Google Drive

    public boolean isDriveConfigured() {
        return googleDriveImportService.isConfigured();
    }

    /**
     * Lists the images in a Drive folder and hands them to the background importer.
     * Returns immediately with the number of files found; progress is available from
     * {@link PhotoProcessingService#getImportProgress}.
     */
    public int startDriveImport(String galleryId, String driveFolderUrl) {
        findGallery(galleryId);
        if (processingService.isImportRunning(galleryId)) {
            throw new BusinessException("An import is already running for this gallery. Wait for it to finish.", "DRIVE_IMPORT_RUNNING");
        }

        String folderId = googleDriveImportService.extractFolderId(driveFolderUrl);
        List<GoogleDriveImportService.DriveFile> files = googleDriveImportService.listImageFiles(folderId);

        if (files.isEmpty()) {
            throw new BusinessException(
                    "No images found in that folder. Make sure it's shared as \"Anyone with the link can view\" and contains image files directly (not in a subfolder).",
                    "DRIVE_FOLDER_EMPTY");
        }

        processingService.importFromDrive(galleryId, files);
        return files.size();
    }

    // ------------------------------------------------------------------ direct-to-storage

    /** Chunk size for relayed (Google Drive) uploads. A multiple of 256 KiB, as Drive requires,
     *  and small enough to stay under typical reverse-proxy body limits and to retry cheaply. */
    static final int CHUNK_SIZE = 8 * 1024 * 1024;

    /**
     * Step 1 of a direct upload (kept for callers that don't send a fingerprint).
     * See {@link #initUpload}.
     */
    @Transactional
    public GalleryDto.PresignedUploadResponse getPresignedUploadUrl(String galleryId, String filename, String contentType, Long sizeBytes) {
        return initUpload(galleryId, filename, contentType, sizeBytes, null);
    }

    /**
     * Step 1 of a direct upload: validates the file and creates a placeholder photo (status
     * UPLOADING). Depending on where originals are stored the browser then either
     * <ul>
     *   <li>PUTs the file straight to the bucket with the returned presigned URL (mode DIRECT), or</li>
     *   <li>sends it in chunks to this API, which relays them to a resumable provider session
     *       (mode CHUNKED, used for Google Drive).</li>
     * </ul>
     * Either way big files never sit in this server's memory. If a {@code fingerprint} is sent and
     * that photo is already in the gallery, nothing is uploaded again (mode EXISTS); if an earlier
     * attempt was interrupted, it is resumed rather than restarted.
     */
    @Transactional
    public GalleryDto.PresignedUploadResponse initUpload(String galleryId, String filename, String contentType,
                                                         Long sizeBytes, String fingerprint) {
        Gallery gallery = findGallery(galleryId);

        if (filename == null || filename.isBlank()) {
            throw new BusinessException("A file name is required", "INVALID_FILE_NAME");
        }
        String type = contentType == null ? "" : contentType.trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException("Unsupported file type: " + contentType, "INVALID_FILE_TYPE");
        }
        if (sizeBytes != null && sizeBytes > MAX_FILE_SIZE) {
            throw new BusinessException("File size exceeds 100MB limit", "FILE_TOO_LARGE");
        }
        if (sizeBytes != null && sizeBytes <= 0) {
            throw new BusinessException("File is empty", "EMPTY_FILE");
        }

        String fp = cleanFingerprint(fingerprint);
        if (fp != null) {
            GalleryDto.PresignedUploadResponse reused = reuseExisting(galleryId, fp);
            if (reused != null) return reused;
        }

        String cleanName = filename.length() > 200 ? filename.substring(filename.length() - 200) : filename;
        String folder = "galleries/" + galleryId + "/originals";

        if (storageService.originalsUseResumableUploads()) {
            if (sizeBytes == null) {
                throw new BusinessException("The file size is required for this upload", "FILE_SIZE_REQUIRED");
            }
            var session = storageService.beginResumableOriginal(folder, cleanName, type, sizeBytes);
            Photo photo = photoRepository.save(Photo.builder()
                    .gallery(gallery).originalFilename(cleanName).s3Key(session.key())
                    .uploadSessionUri(session.sessionUri()).uploadFingerprint(fp)
                    .mimeType(type).fileSizeBytes(sizeBytes).status(PhotoStatus.UPLOADING).build());
            return GalleryDto.PresignedUploadResponse.builder()
                    .mode("CHUNKED").photoId(photo.getId()).key(photo.getS3Key())
                    .chunkSize(CHUNK_SIZE).uploadedBytes(0L).build();
        }

        String key = storageService.newPresignedOriginalKey(folder, cleanName);
        String uploadUrl = storageService.generateSignedUploadUrl(key, type);
        Photo photo = photoRepository.save(Photo.builder()
                .gallery(gallery).originalFilename(cleanName).s3Key(key).uploadFingerprint(fp)
                .mimeType(type).fileSizeBytes(sizeBytes != null ? sizeBytes : 0L)
                .status(PhotoStatus.UPLOADING).build());
        return GalleryDto.PresignedUploadResponse.builder()
                .mode("DIRECT").uploadUrl(uploadUrl).photoId(photo.getId()).key(key).build();
    }

    /** Returns a response when the fingerprinted photo already exists (skip or resume), else null. */
    private GalleryDto.PresignedUploadResponse reuseExisting(String galleryId, String fingerprint) {
        Photo existing = photoRepository
                .findFirstByGalleryIdAndUploadFingerprintOrderByCreatedAtAsc(galleryId, fingerprint).orElse(null);
        if (existing == null) return null;

        if (existing.getStatus() == PhotoStatus.UPLOADING) {
            return resumeInterrupted(existing);
        }
        if (existing.getStatus() == PhotoStatus.FAILED) {
            // The original made it to storage and only processing failed: re-run processing
            // instead of uploading the same bytes again. If it is gone, start clean.
            if (existing.getS3Key() != null && storageService.fileExists(existing.getS3Key())) {
                existing.setStatus(PhotoStatus.UPLOADED);
                existing.setFailureReason(null);
                photoRepository.save(existing);
                String id = existing.getId();
                afterCommit(() -> processingService.processPhoto(id));
                return existsResponse(existing);
            }
            photoRepository.delete(existing);
            photoRepository.flush();
            return null;
        }
        return existsResponse(existing);
    }

    private GalleryDto.PresignedUploadResponse existsResponse(Photo p) {
        return GalleryDto.PresignedUploadResponse.builder().mode("EXISTS").photoId(p.getId()).key(p.getS3Key()).build();
    }

    private GalleryDto.PresignedUploadResponse resumeInterrupted(Photo p) {
        if (p.getUploadSessionUri() != null) {
            try {
                var progress = storageService.resumableProgress(p.getUploadSessionUri(), p.getFileSizeBytes());
                if (progress.complete()) {
                    finalizeUploaded(p, p.getFileSizeBytes(), null, null);
                    return existsResponse(p);
                }
                return GalleryDto.PresignedUploadResponse.builder()
                        .mode("CHUNKED").photoId(p.getId()).key(p.getS3Key())
                        .chunkSize(CHUNK_SIZE).uploadedBytes(progress.received()).build();
            } catch (UploadSessionExpiredException e) {
                storageService.deleteFile(p.getS3Key());
                photoRepository.delete(p);
                photoRepository.flush();
                return null;
            }
        }
        // Presigned PUT: the same key can simply be uploaded again.
        return GalleryDto.PresignedUploadResponse.builder()
                .mode("DIRECT").photoId(p.getId()).key(p.getS3Key())
                .uploadUrl(storageService.generateSignedUploadUrl(p.getS3Key(), p.getMimeType())).build();
    }

    /**
     * Step 2 for DIRECT uploads: the browser finished PUT-ing the file. We verify the object
     * really exists (and take its real size from storage, not from the client), then queue
     * processing. Idempotent: a retried confirm does nothing.
     */
    @Transactional
    public void confirmUpload(String photoId, Integer width, Integer height) {
        Photo photo = photoRepository.findWithGalleryById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", "id", photoId));

        if (photo.getStatus() != PhotoStatus.UPLOADING) {
            return; // idempotent: a retried confirm must not re-process a finished photo
        }

        long size = storageService.getFileSize(photo.getS3Key());
        if (size < 0) {
            throw new BusinessException("The file has not reached storage yet. Please retry the upload.", "UPLOAD_NOT_FOUND");
        }
        if (size > MAX_FILE_SIZE) {
            storageService.deleteFile(photo.getS3Key());
            photoRepository.delete(photo);
            throw new BusinessException("File size exceeds 100MB limit", "FILE_TOO_LARGE");
        }
        finalizeUploaded(photo, size, width, height);
    }

    /**
     * Step 2 for CHUNKED uploads: relays one chunk to the provider session. When the last chunk
     * lands the photo is finalized right here, so a browser that dies right after the final chunk
     * does not leave a complete upload to be cleaned up as "abandoned".
     */
    public GalleryDto.ChunkResponse uploadChunk(String photoId, long offset, long total, byte[] data) {
        Photo photo = photoRepository.findWithGalleryById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", "id", photoId));
        if (photo.getStatus() != PhotoStatus.UPLOADING) {
            return GalleryDto.ChunkResponse.builder().complete(true).receivedBytes(photo.getFileSizeBytes()).build();
        }
        if (photo.getUploadSessionUri() == null) {
            throw new BusinessException("This upload cannot be continued. Please start it again.", "UPLOAD_NOT_RESUMABLE");
        }
        if (data == null || data.length == 0 || offset < 0 || total <= 0 || offset + data.length > total
                || total != photo.getFileSizeBytes()) {
            throw new BusinessException("The upload chunk does not match the file. Please start the upload again.", "INVALID_CHUNK");
        }
        boolean last = offset + data.length == total;
        if (offset % ResumableUploads.CHUNK_ALIGNMENT != 0 || (!last && data.length % ResumableUploads.CHUNK_ALIGNMENT != 0)) {
            throw new BusinessException("The upload chunk is not aligned. Please start the upload again.", "INVALID_CHUNK");
        }
        try {
            var progress = storageService.uploadChunk(photo.getUploadSessionUri(), offset, data, total);
            if (progress.complete()) {
                long size = storageService.getFileSize(photo.getS3Key());
                finalizeUploaded(photo, size >= 0 ? size : total, null, null);
                return GalleryDto.ChunkResponse.builder().complete(true).receivedBytes(total).build();
            }
            return GalleryDto.ChunkResponse.builder().complete(false).receivedBytes(progress.received()).build();
        } catch (UploadSessionExpiredException e) {
            throw new BusinessException(e.getMessage(), "UPLOAD_SESSION_EXPIRED");
        }
    }

    /** How many bytes of a chunked upload the provider already has (used to resume after an error). */
    public GalleryDto.ChunkResponse getUploadProgress(String photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", "id", photoId));
        if (photo.getStatus() != PhotoStatus.UPLOADING) {
            return GalleryDto.ChunkResponse.builder().complete(true).receivedBytes(photo.getFileSizeBytes()).build();
        }
        if (photo.getUploadSessionUri() == null) {
            throw new BusinessException("This upload cannot be continued. Please start it again.", "UPLOAD_NOT_RESUMABLE");
        }
        try {
            var progress = storageService.resumableProgress(photo.getUploadSessionUri(), photo.getFileSizeBytes());
            return GalleryDto.ChunkResponse.builder().complete(progress.complete()).receivedBytes(progress.received()).build();
        } catch (UploadSessionExpiredException e) {
            throw new BusinessException(e.getMessage(), "UPLOAD_SESSION_EXPIRED");
        }
    }

    /** Marks an uploaded original as stored and queues thumbnail/preview generation. */
    private void finalizeUploaded(Photo photo, long size, Integer width, Integer height) {
        photo.setFileSizeBytes(size);
        if (width != null && width > 0) photo.setWidth(width);
        if (height != null && height > 0) photo.setHeight(height);
        photo.setStatus(PhotoStatus.UPLOADED);
        photo.setUploadSessionUri(null);
        photo.setFailureReason(null);
        photoRepository.save(photo);

        processingService.refreshStats(photo.getGallery().getId());
        String id = photo.getId();
        afterCommit(() -> processingService.processPhoto(id));
    }

    private static String cleanFingerprint(String fingerprint) {
        if (fingerprint == null) return null;
        String f = fingerprint.trim();
        if (f.isEmpty()) return null;
        return f.length() > 255 ? f.substring(0, 255) : f;
    }

    /** Live counters for the upload screen (polled while anything is still processing). */
    @Transactional(readOnly = true)
    public GalleryDto.UploadStatus getUploadStatus(String galleryId) {
        findGallery(galleryId);
        return GalleryDto.UploadStatus.builder()
                .uploading(photoRepository.countByGalleryIdAndStatus(galleryId, PhotoStatus.UPLOADING))
                .processing(photoRepository.countByGalleryIdAndStatus(galleryId, PhotoStatus.UPLOADED)
                        + photoRepository.countByGalleryIdAndStatus(galleryId, PhotoStatus.PROCESSING))
                .ready(photoRepository.countByGalleryIdAndStatus(galleryId, PhotoStatus.READY))
                .failed(photoRepository.countByGalleryIdAndStatus(galleryId, PhotoStatus.FAILED))
                .driveImport(processingService.getImportProgress(galleryId))
                .build();
    }

    /** Re-queues every photo whose processing failed. */
    public int retryFailed(String galleryId) {
        findGallery(galleryId);
        return processingService.retryFailed(galleryId);
    }

    /**
     * Removes placeholder photos whose direct upload never completed (tab closed, network
     * dropped) so they don't linger forever. Called by the scheduler.
     */
    @Transactional
    public int cleanupStaleUploads() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(STALE_UPLOAD_HOURS);
        List<Photo> stale = photoRepository.findByStatusAndCreatedAtBefore(PhotoStatus.UPLOADING, cutoff);
        for (Photo p : stale) {
            if (p.getS3Key() != null) storageService.deleteFile(p.getS3Key());
            photoRepository.delete(p);
        }
        if (!stale.isEmpty()) log.info("Removed {} abandoned direct upload(s)", stale.size());
        return stale.size();
    }

    // ------------------------------------------------------------------ originals expiry

    /**
     * Deletes the full-resolution ORIGINAL (not the thumbnail/preview/watermarked
     * versions, so the gallery still displays) of every photo the client did NOT select.
     * Selected photos are never touched. Idempotent: already-purged photos are skipped.
     *
     * Safety: if no photo has been selected at all, this would delete EVERY original. That
     * is refused unless {@code allowWhenNothingSelected} is true (an explicit admin
     * confirmation); the nightly job never passes true.
     *
     * @return number of originals actually deleted
     */
    @Transactional
    public int purgeUnselectedOriginals(String galleryId, boolean allowWhenNothingSelected) {
        Gallery gallery = findGallery(galleryId);

        java.util.Set<String> selectedPhotoIds = photoSelectionRepository.findByGalleryId(galleryId).stream()
                .flatMap(sel -> selectedPhotoRepository.findBySelectionId(sel.getId()).stream())
                .map(com.cameraman.photoselection.domain.SelectedPhoto::getPhotoId)
                .collect(java.util.stream.Collectors.toSet());

        if (selectedPhotoIds.isEmpty() && !allowWhenNothingSelected) {
            throw new BusinessException(
                    "The client has not selected any photos yet, so this would delete every original. Confirm explicitly to continue.",
                    "NO_SELECTION");
        }

        List<Photo> photos = photoRepository.findByGalleryIdOrderBySortOrderAsc(galleryId);
        List<String> keysToDelete = new ArrayList<>();
        List<Photo> purged = new ArrayList<>();

        for (Photo photo : photos) {
            if (photo.isOriginalPurged() || selectedPhotoIds.contains(photo.getId())) {
                continue;
            }
            // Never purge an original that has not finished being processed: the thumbnails
            // are generated from it.
            if (photo.getStatus() != PhotoStatus.READY) {
                continue;
            }
            if (photo.getS3Key() != null && !photo.getS3Key().isBlank()) {
                keysToDelete.add(photo.getS3Key());
            }
            photo.setOriginalPurged(true);
            purged.add(photo);
        }

        if (!keysToDelete.isEmpty()) {
            storageService.deleteFiles(keysToDelete);
        }
        if (!purged.isEmpty()) {
            photoRepository.saveAll(purged);
        }

        gallery.setOriginalsExpiredAt(LocalDateTime.now());
        galleryRepository.save(gallery);

        log.info("Purged {} unselected original(s) from gallery {} ({} kept as selected)",
                purged.size(), galleryId, selectedPhotoIds.size());
        return purged.size();
    }

    // ------------------------------------------------------------------ helpers

    private Gallery findGallery(String galleryId) {
        return galleryRepository.findById(galleryId)
                .orElseThrow(() -> new ResourceNotFoundException("Gallery", "id", galleryId));
    }

    private void refreshStats(Gallery gallery) {
        String galleryId = gallery.getId();
        photoRepository.flush();
        gallery.setPhotoCount((int) photoRepository.countByGalleryIdAndStatusNot(galleryId, PhotoStatus.UPLOADING));
        Long total = photoRepository.sumFileSizeByGallery(galleryId);
        gallery.setTotalSizeBytes(total != null ? total : 0L);
        galleryRepository.save(gallery);
    }

    /** Runs the task once the current transaction commits (or immediately when there is none),
     *  so a background thread never looks for a photo row that is not visible yet. */
    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private Photo processAndSavePhoto(Gallery gallery, MultipartFile file) {
        try {
            String folder = "galleries/" + gallery.getId() + "/originals";
            var uploadResult = storageService.uploadFile(StorageTier.ORIGINALS,
                    file.getInputStream(), folder,
                    file.getOriginalFilename(), file.getContentType(), file.getSize());

            Photo photo = Photo.builder()
                    .gallery(gallery)
                    .originalFilename(file.getOriginalFilename())
                    .s3Key(uploadResult.getKey())
                    .fileSizeBytes(file.getSize())
                    .mimeType(file.getContentType())
                    .status(PhotoStatus.UPLOADED)
                    .build();

            return photoRepository.save(photo);
        } catch (IOException e) {
            throw new BusinessException("Failed to read file: " + file.getOriginalFilename(), "FILE_READ_ERROR");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("File is empty", "EMPTY_FILE");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("File size exceeds 100MB limit", "FILE_TOO_LARGE");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BusinessException("Unsupported file type: " + file.getContentType(), "INVALID_FILE_TYPE");
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private PhotoDto.PhotoResponse toResponse(Photo p) {
        return PhotoDto.PhotoResponse.builder()
                .id(p.getId())
                .galleryId(p.getGallery().getId())
                .originalFilename(p.getOriginalFilename())
                .status(p.getStatus().name())
                .fileSizeBytes(p.getFileSizeBytes())
                .width(p.getWidth())
                .height(p.getHeight())
                .mimeType(p.getMimeType())
                .caption(p.getCaption())
                .favourite(p.isFavourite())
                .sortOrder(p.getSortOrder())
                .originalPurged(p.isOriginalPurged())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
