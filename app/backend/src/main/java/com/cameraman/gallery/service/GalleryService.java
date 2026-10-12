package com.cameraman.gallery.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.gallery.domain.*;
import com.cameraman.gallery.dto.GalleryDto;
import com.cameraman.gallery.dto.PhotoDto;
import com.cameraman.gallery.repository.GalleryRepository;
import com.cameraman.gallery.repository.PhotoRepository;
import com.cameraman.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GalleryService {

    private final GalleryRepository galleryRepository;
    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final PhotoUploadService photoUploadService;
    private final com.cameraman.photoselection.repository.PhotoSelectionRepository photoSelectionRepository;
    private final com.cameraman.user.repository.UserRepository userRepository;
    private final com.cameraman.notification.service.NotificationService notificationService;
    private final com.cameraman.notification.service.NotificationPublisher notificationPublisher;
    private final com.cameraman.settings.service.StudioSettingsService settingsService;
    private final com.cameraman.gallery.repository.GalleryViewerRepository galleryViewerRepository;

    public GalleryDto.GalleryResponse createGallery(GalleryDto.CreateRequest request) {
        Gallery gallery = Gallery.builder()
                .name(request.getName())
                .bookingId(request.getBookingId())
                .clientId(request.getClientId())
                .clientName(request.getClientName())
                .description(request.getDescription())
                .status(GalleryStatus.DRAFT)
                .selectionLimit(request.getSelectionLimit())
                .downloadAllowed(request.isDownloadAllowed())
                .watermarkEnabled(request.isWatermarkEnabled())
                .passwordProtected(request.isPasswordProtected())
                .galleryPassword(request.getGalleryPassword())
                .expiresAt(request.getExpiresAt())
                .build();
        return toResponse(galleryRepository.save(gallery));
    }

    public GalleryDto.GalleryResponse publishGallery(String id, GalleryDto.PublishRequest request) {
        Gallery gallery = findById(id);
        // Publishing again must not break a link the client already has: keep the existing link
        // unless there is none, it has expired, or staff explicitly asked for a new one.
        boolean noLink = gallery.getAccessToken() == null || gallery.getAccessToken().isBlank();
        boolean linkExpired = gallery.getAccessTokenExpires() != null
                && gallery.getAccessTokenExpires().isBefore(LocalDateTime.now());
        boolean newLink = noLink || linkExpired || request.isRegenerateLink();
        gallery.setStatus(GalleryStatus.PUBLISHED);
        if (newLink) {
            gallery.setAccessToken(UUID.randomUUID().toString().replace("-",""));
            gallery.setAccessTokenExpires(request.getExpiresAt() != null ? request.getExpiresAt()
                    : LocalDateTime.now().plusDays(30));
        } else if (request.getExpiresAt() != null) {
            gallery.setAccessTokenExpires(request.getExpiresAt());
        }
        if (gallery.getPublishedAt() == null || newLink) gallery.setPublishedAt(LocalDateTime.now());
        if (request.getSelectionLimit() != null) gallery.setSelectionLimit(request.getSelectionLimit());
        gallery.setDownloadAllowed(request.isDownloadAllowed());
        gallery.setWatermarkEnabled(request.isWatermarkEnabled());
        gallery.setPasswordProtected(request.isPasswordProtected());
        if (request.getGalleryPassword() != null) gallery.setGalleryPassword(request.getGalleryPassword());
        gallery = galleryRepository.save(gallery);

        // This was previously entirely missing: NotificationService.sendGalleryPublished()
        // existed but nothing ever called it, so a client whose gallery just went live got
        // no WhatsApp/email/in-app signal of any kind — they'd only find out if they
        // happened to check the client portal or a staff member remembered to tell them.
        if (newLink) notifyClientGalleryPublished(gallery);

        return toResponse(gallery);
    }

    private void notifyClientGalleryPublished(Gallery gallery) {
        String mobile = null;
        String email = null;
        if (gallery.getClientId() != null && !gallery.getClientId().isBlank()) {
            var client = userRepository.findById(gallery.getClientId()).orElse(null);
            if (client != null) {
                mobile = client.getMobile();
                email = client.getEmail();
            }
        }
        try {
            notificationService.sendGalleryPublished(mobile, email, gallery.getClientName(),
                    gallery.getAccessToken(), gallery.getName(), settingsService.getStudioName());
        } catch (Exception e) {
            log.error("Failed to send gallery-published notification for gallery {}: {}", gallery.getId(), e.getMessage());
        }
        if (gallery.getClientId() != null && !gallery.getClientId().isBlank()) {
            notificationPublisher.notifyUser(gallery.getClientId(), "GALLERY_PUBLISHED",
                    "Your gallery is ready!",
                    "\"" + gallery.getName() + "\" has been published and is ready to view.",
                    "Gallery", gallery.getId());
        }
    }

    /** Staff-facing single-gallery lookup by real ID (as opposed to
     *  getGalleryByToken, which is the client-facing lookup by share
     *  token). The admin Gallery Detail screen needs this — it navigates
     *  by database ID, not by share token. */
    @Transactional(readOnly = true)
    public GalleryDto.GalleryResponse getGalleryById(String id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public GalleryDto.GalleryResponse getGalleryByToken(String token, String password) {
        Gallery gallery = galleryRepository.findByAccessToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Gallery not found"));

        if (gallery.getStatus() != GalleryStatus.PUBLISHED) {
            throw new BusinessException("Gallery is not available", "GALLERY_NOT_PUBLISHED");
        }
        if (gallery.getAccessTokenExpires() != null && LocalDateTime.now().isAfter(gallery.getAccessTokenExpires())) {
            throw new BusinessException("Gallery link has expired", "GALLERY_EXPIRED");
        }
        if (gallery.isPasswordProtected()) {
            if (password == null || !password.equals(gallery.getGalleryPassword())) {
                throw new BusinessException("Invalid gallery password", "INVALID_PASSWORD");
            }
        }
        gallery.setViewCount(gallery.getViewCount() + 1);
        galleryRepository.save(gallery);

        GalleryDto.GalleryResponse response = toResponse(gallery);
        // Only resolved here (the client-facing single-gallery view), not in toResponse()
        // itself — doing this lookup for every row in the staff gallery list would be an
        // unnecessary extra query per gallery for something that list never displays.
        photoSelectionRepository.findByGalleryId(gallery.getId()).stream()
                .filter(s -> s.getStatus() == com.cameraman.photoselection.domain.SelectionStatus.OPEN)
                .findFirst()
                .ifPresent(s -> response.setOpenSelectionToken(s.getAccessToken()));
        return response;
    }

    @Transactional(readOnly = true)
    public PagedResponse<GalleryDto.GalleryResponse> getAllGalleries(String search, String status, Pageable pageable) {
        Page<Gallery> page;
        if (search != null && !search.isBlank()) {
            page = galleryRepository.searchGalleries(search, pageable);
        } else if (status != null && !status.isBlank()) {
            page = galleryRepository.findByStatus(GalleryStatus.valueOf(status), pageable);
        } else {
            page = galleryRepository.findAll(pageable);
        }
        return PagedResponse.of(page.map(this::toResponse));
    }

    /** Staff: every gallery of a booking (used by the album photo picker). */
    @Transactional(readOnly = true)
    public List<GalleryDto.GalleryResponse> getGalleriesByBooking(String bookingId) {
        return galleryRepository.findAllByBookingId(bookingId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** A client's own gallery list — was missing entirely before: a client with more than
     *  one shoot over time had no way to browse their galleries, only ever reaching one via
     *  a specific emailed token link. Scoped to PUBLISHED only (a client shouldn't see a
     *  gallery still being prepared) and to their own clientId — the query itself enforces
     *  both, not a post-filter, so pagination stays correct at any scale. */
    @Transactional(readOnly = true)
    public PagedResponse<GalleryDto.GalleryResponse> getMyGalleries(String clientId, Pageable pageable) {
        Page<Gallery> page = galleryRepository.findVisibleToClient(clientId, GalleryStatus.PUBLISHED, pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    /** True when this client owns the gallery or staff shared it with them. */
    @Transactional(readOnly = true)
    public boolean canClientView(String galleryId, String clientId) {
        if (clientId == null) return false;
        String owner = getGalleryClientId(galleryId);
        return clientId.equals(owner) || galleryViewerRepository.existsByGalleryIdAndClientId(galleryId, clientId);
    }

    @Transactional(readOnly = true)
    public List<GalleryDto.ViewerResponse> getViewers(String galleryId) {
        Gallery gallery = findById(galleryId);
        List<GalleryDto.ViewerResponse> out = new java.util.ArrayList<>();
        var selections = photoSelectionRepository.findByGalleryId(galleryId);
        if (gallery.getClientId() != null && !gallery.getClientId().isBlank()) {
            userRepository.findById(gallery.getClientId()).ifPresent(u -> out.add(viewerRow(u, true, true, selections)));
        }
        for (var v : galleryViewerRepository.findByGalleryId(galleryId)) {
            if (v.getClientId().equals(gallery.getClientId())) continue;
            userRepository.findById(v.getClientId()).ifPresent(u -> out.add(viewerRow(u, v.isCanSelect(), false, selections)));
        }
        return out;
    }

    private GalleryDto.ViewerResponse viewerRow(com.cameraman.user.domain.User u, boolean canSelect, boolean primary,
                                                 List<com.cameraman.photoselection.domain.PhotoSelection> selections) {
        var sel = selections.stream().filter(s -> u.getId().equals(s.getClientId())).findFirst().orElse(null);
        return GalleryDto.ViewerResponse.builder()
                .clientId(u.getId()).name((u.getFirstName() + " " + (u.getLastName() != null ? u.getLastName() : "")).trim())
                .email(u.getEmail()).mobile(u.getMobile()).canSelect(canSelect).primary(primary)
                .selectionStatus(sel != null ? sel.getStatus().name() : null)
                .selectedCount(sel != null ? sel.getSelectedCount() : 0).build();
    }

    /**
     * Replaces the set of extra viewers. Anyone with canSelect gets their own photo selection (so each
     * person picks independently); removing a viewer or turning selection off drops their selection
     * only when it is still empty and open, so picks already made are never thrown away.
     */
    public List<GalleryDto.ViewerResponse> setViewers(String galleryId, GalleryDto.ViewersRequest request) {
        Gallery gallery = findById(galleryId);
        java.util.Map<String, Boolean> wanted = new java.util.LinkedHashMap<>();
        if (request != null && request.getViewers() != null) {
            for (var in : request.getViewers()) {
                if (in.getClientId() == null || in.getClientId().isBlank()) continue;
                if (in.getClientId().equals(gallery.getClientId())) continue; // primary client is implicit
                if (userRepository.findById(in.getClientId()).isEmpty()) {
                    throw new ResourceNotFoundException("Client", "id", in.getClientId());
                }
                wanted.put(in.getClientId(), in.isCanSelect());
            }
        }
        var existing = galleryViewerRepository.findByGalleryId(galleryId);
        var selections = photoSelectionRepository.findByGalleryId(galleryId);

        for (var v : existing) {
            if (!wanted.containsKey(v.getClientId())) {
                galleryViewerRepository.delete(v);
                dropEmptyOpenSelection(selections, v.getClientId());
            }
        }
        for (var e : wanted.entrySet()) {
            var row = existing.stream().filter(v -> v.getClientId().equals(e.getKey())).findFirst()
                    .orElseGet(() -> com.cameraman.gallery.domain.GalleryViewer.builder().galleryId(galleryId).clientId(e.getKey()).build());
            row.setCanSelect(e.getValue());
            galleryViewerRepository.save(row);
            boolean has = selections.stream().anyMatch(s -> e.getKey().equals(s.getClientId()));
            if (e.getValue() && !has) {
                var u = userRepository.findById(e.getKey()).orElseThrow();
                photoSelectionRepository.save(com.cameraman.photoselection.domain.PhotoSelection.builder()
                        .galleryId(galleryId).bookingId(gallery.getBookingId()).clientId(u.getId())
                        .clientName((u.getFirstName() + " " + (u.getLastName() != null ? u.getLastName() : "")).trim())
                        .accessToken(UUID.randomUUID().toString().replace("-", ""))
                        .selectionLimit(gallery.getSelectionLimit())
                        .status(com.cameraman.photoselection.domain.SelectionStatus.OPEN).build());
            } else if (!e.getValue()) {
                dropEmptyOpenSelection(selections, e.getKey());
            }
        }
        return getViewers(galleryId);
    }

    private void dropEmptyOpenSelection(List<com.cameraman.photoselection.domain.PhotoSelection> selections, String clientId) {
        selections.stream()
                .filter(s -> clientId.equals(s.getClientId())
                        && s.getStatus() == com.cameraman.photoselection.domain.SelectionStatus.OPEN
                        && s.getSelectedCount() == 0)
                .forEach(photoSelectionRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<PhotoDto.PhotoResponse> getGalleryPhotos(String galleryId, boolean includeUrls) {
        return getGalleryPhotos(galleryId, includeUrls, false, false);
    }

    @Transactional(readOnly = true)
    public List<PhotoDto.PhotoResponse> getGalleryPhotos(String galleryId, boolean includeUrls, boolean includePending) {
        return getGalleryPhotos(galleryId, includeUrls, includePending, false);
    }

    /** @param includePending staff view: also returns photos that are still processing or
     *  failed (never the placeholders of uploads that have not finished). Clients only
     *  ever get READY photos. */
    /** @param forClient true for a client (not staff) caller: while downloads are locked (unpaid) the
     *  clean preview must not be handed out, so previewUrl is replaced by the watermarked image. */
    @Transactional(readOnly = true)
    public List<PhotoDto.PhotoResponse> getGalleryPhotos(String galleryId, boolean includeUrls, boolean includePending, boolean forClient) {
        boolean locked = forClient && galleryRepository.findById(galleryId)
                .map(g -> !g.isDownloadAllowed()).orElse(true);
        List<Photo> photos = includePending
                ? photoRepository.findByGalleryIdOrderBySortOrderAsc(galleryId).stream()
                        .filter(p -> p.getStatus() != PhotoStatus.UPLOADING)
                        .collect(Collectors.toList())
                : photoRepository.findByGalleryIdAndStatusOrderBySortOrderAsc(galleryId, PhotoStatus.READY);
        return photos.stream().map(p -> toPhotoResponse(p, includeUrls, forClient, locked)).collect(Collectors.toList());
    }

    /** Streams every photo in a gallery into a ZIP written directly to the
     *  given output stream (one file downloaded from storage and written
     *  at a time, never the whole gallery buffered in memory at once — a
     *  gallery-sized ZIP of full-resolution originals could otherwise be
     *  several GB). Applies the exact same downloadAllowed/watermark/
     *  purged-original rules as the single-photo download endpoint
     *  (getSignedPhotoUrl) so "download all" can never hand out something a
     *  single download wouldn't. */
    @Transactional(readOnly = true)
    public void streamGalleryZip(String galleryId, java.io.OutputStream out) {
        Gallery gallery = findById(galleryId);
        List<Photo> photos = photoRepository.findByGalleryIdOrderBySortOrderAsc(galleryId);
        java.util.Set<String> usedNames = new java.util.HashSet<>();

        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            int i = 0;
            for (Photo photo : photos) {
                i++;
                boolean watermarked = !gallery.isDownloadAllowed();
                if (!watermarked && photo.isOriginalPurged()) {
                    // Same rule as getSignedPhotoUrl: skip rather than fail the
                    // whole ZIP over one photo whose original was purged.
                    continue;
                }
                String key = watermarked
                        ? (photo.getWatermarkedKey() != null ? photo.getWatermarkedKey()
                            : photo.getPreviewKey() != null ? photo.getPreviewKey() : photo.getS3Key())
                        : photo.getS3Key();

                // Stream each file through instead of loading it: a full-resolution original can
                // be 100 MB, and several of those at once would exhaust the heap.
                com.cameraman.storage.provider.StoredObject object;
                try {
                    object = storageService.open(key);
                } catch (Exception e) {
                    log.warn("Skipping photo {} in ZIP for gallery {}: {}", photo.getId(), galleryId, e.getMessage());
                    continue;
                }

                String name = uniqueZipEntryName(photo.getOriginalFilename(), i, usedNames);
                try (object) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(name));
                    object.stream().transferTo(zip);
                    zip.closeEntry();
                }
            }
        } catch (java.io.IOException e) {
            // Most commonly the client disconnected mid-download (closed the
            // tab, lost connection) — not a server-side failure worth a 500.
            log.debug("Gallery ZIP stream for {} ended early: {}", galleryId, e.getMessage());
        }
    }

    private String uniqueZipEntryName(String originalFilename, int index, java.util.Set<String> usedNames) {
        String base = (originalFilename == null || originalFilename.isBlank())
                ? "photo-" + index + ".jpg" : originalFilename;
        String candidate = String.format("%03d_%s", index, base);
        while (!usedNames.add(candidate)) {
            candidate = String.format("%03d_%d_%s", index, usedNames.size(), base);
        }
        return candidate;
    }

    public String getGalleryClientId(String galleryId) {
        return galleryRepository.findById(galleryId)
                .map(Gallery::getClientId)
                .orElse(null);
    }

    public void deletePhoto(String photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", "id", photoId));

        // Delete from storage
        if (photo.getS3Key() != null) storageService.deleteFile(photo.getS3Key());
        if (photo.getThumbnailKey() != null) storageService.deleteFile(photo.getThumbnailKey());
        if (photo.getPreviewKey() != null) storageService.deleteFile(photo.getPreviewKey());
        if (photo.getWatermarkedKey() != null) storageService.deleteFile(photo.getWatermarkedKey());

        Gallery gallery = photo.getGallery();
        photoRepository.delete(photo);
        photoRepository.flush();

        // Recompute count and size from what is actually left
        gallery.setPhotoCount((int) photoRepository.countByGalleryIdAndStatusNot(gallery.getId(), PhotoStatus.UPLOADING));
        Long total = photoRepository.sumFileSizeByGallery(gallery.getId());
        gallery.setTotalSizeBytes(total != null ? total : 0L);
        galleryRepository.save(gallery);
    }

    public String getSignedPhotoUrl(String photoId, boolean watermarked) {
        return getSignedPhotoUrl(photoId, watermarked, true);
    }

    /**
     * @param isStaff false for a client: while the gallery is locked (invoice not fully paid and
     *  staff have not unlocked it) a client always gets the watermarked image, whatever they ask for.
     *  Previously {@code ?original=true} returned the full-resolution file to anyone who could see the gallery.
     */
    public String getSignedPhotoUrl(String photoId, boolean watermarked, boolean isStaff) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", "id", photoId));
        Gallery gallery = photo.getGallery();

        if (!gallery.isDownloadAllowed() && (watermarked || !isStaff)) {
            String key = photo.getWatermarkedKey() != null ? photo.getWatermarkedKey() : photo.getPreviewKey();
            if (key == null) key = photo.getS3Key();
            return storageService.generateSignedDownloadUrl(key);
        }

        if (!watermarked && photo.isOriginalPurged()) {
            throw new BusinessException(
                    "The full-resolution original for this photo was removed to save storage because it wasn't selected. Contact the studio if you need it re-uploaded.",
                    "ORIGINAL_PURGED");
        }

        String key = watermarked && photo.getWatermarkedKey() != null
                ? photo.getWatermarkedKey() : photo.getS3Key();
        return storageService.generateSignedDownloadUrl(key);
    }

    /** Photographer / client of the photo's gallery check, used by the download-url endpoint. */
    @Transactional(readOnly = true)
    public String galleryIdOfPhoto(String photoId) {
        return photoRepository.findById(photoId)
                .map(p -> p.getGallery().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Photo", "id", photoId));
    }

    /**
     * Manual override of the payment lock: staff can unlock (clean photos + downloads) or re-lock
     * (watermarked, no downloads) a gallery regardless of what the invoice says, e.g. for a client
     * who paid in cash outside the system, a goodwill release, or to take it back after a refund.
     */
    public GalleryDto.GalleryResponse setDownloadsAllowed(String galleryId, boolean allowed) {
        Gallery gallery = findById(galleryId);
        gallery.setDownloadAllowed(allowed);
        log.info("Downloads {} manually for gallery {}", allowed ? "unlocked" : "locked", galleryId);
        return toResponse(galleryRepository.save(gallery));
    }

    /** Admin sets (or clears, with days=null) the auto-expiry window for this gallery's originals. */
    public GalleryDto.GalleryResponse setOriginalsExpiry(String galleryId, Integer days) {
        if (days != null && (days < 1 || days > 3650)) {
            throw new BusinessException("Choose between 1 and 3650 days, or leave empty to never auto-delete.", "INVALID_EXPIRY_DAYS");
        }
        Gallery gallery = findById(galleryId);
        gallery.setOriginalsExpiryDays(days);
        return toResponse(galleryRepository.save(gallery));
    }

    /** Admin-triggered immediate purge, regardless of the configured expiry window. */
    public GalleryDto.PurgeResult forceExpireOriginals(String galleryId, boolean confirmNoSelection) {
        int purged = photoUploadService.purgeUnselectedOriginals(galleryId, confirmNoSelection);
        return GalleryDto.PurgeResult.builder().purgedCount(purged).purgedAt(java.time.LocalDateTime.now()).build();
    }

    private Gallery findById(String id) {
        return galleryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gallery", "id", id));
    }

    private GalleryDto.GalleryResponse toResponse(Gallery g) {
        return GalleryDto.GalleryResponse.builder()
                .id(g.getId()).name(g.getName()).bookingId(g.getBookingId())
                .clientId(g.getClientId()).clientName(g.getClientName())
                .description(g.getDescription()).status(g.getStatus().name())
                .accessToken(g.getAccessToken())
                .accessTokenExpires(g.getAccessTokenExpires())
                .passwordProtected(g.isPasswordProtected())
                .selectionLimit(g.getSelectionLimit())
                .downloadAllowed(g.isDownloadAllowed())
                .watermarkEnabled(g.isWatermarkEnabled())
                .coverPhotoId(g.getCoverPhotoId())
                .photoCount(g.getPhotoCount())
                .totalSizeBytes(g.getTotalSizeBytes())
                .publishedAt(g.getPublishedAt())
                .expiresAt(g.getExpiresAt())
                .originalsExpiryDays(g.getOriginalsExpiryDays())
                .originalsExpiredAt(g.getOriginalsExpiredAt())
                .viewCount(g.getViewCount())
                .createdAt(g.getCreatedAt())
                .build();
    }

    private PhotoDto.PhotoResponse toPhotoResponse(Photo p, boolean includeUrls, boolean forClient, boolean locked) {
        PhotoDto.PhotoResponse.PhotoResponseBuilder builder = PhotoDto.PhotoResponse.builder()
                .id(p.getId()).galleryId(p.getGallery().getId())
                .originalFilename(p.getOriginalFilename()).status(p.getStatus().name())
                .fileSizeBytes(p.getFileSizeBytes()).width(p.getWidth()).height(p.getHeight())
                .mimeType(p.getMimeType()).caption(p.getCaption())
                .favourite(p.isFavourite()).sortOrder(p.getSortOrder())
                .downloadCount(p.getDownloadCount()).originalPurged(p.isOriginalPurged())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt());

        if (includeUrls) {
            // Thumbnail/preview/watermarked tiers are never purged — always safe to link.
            if (p.getThumbnailKey() != null)
                builder.thumbnailUrl(storageService.generateSignedDownloadUrl(p.getThumbnailKey()));
            // Locked gallery, client caller: the only large image they may receive is the watermarked
            // one (served as previewUrl too, so nothing clean can be picked out of the JSON).
            // Unlocked, client caller: the clean preview is returned and the watermarked copy is
            // withheld, so the portal shows watermark-free photos as soon as the invoice is paid.
            // Staff always get both, as before.
            String wm = p.getWatermarkedKey();
            if (forClient && locked && wm != null) {
                String url = storageService.generateSignedDownloadUrl(wm);
                builder.previewUrl(url).watermarkedUrl(url);
            } else {
                if (p.getPreviewKey() != null)
                    builder.previewUrl(storageService.generateSignedDownloadUrl(p.getPreviewKey()));
                if (wm != null && !forClient)
                    builder.watermarkedUrl(storageService.generateSignedDownloadUrl(wm));
            }
        }
        return builder.build();
    }
}
