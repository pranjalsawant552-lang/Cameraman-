package com.cameraman.album.service;

import com.cameraman.album.domain.*;
import com.cameraman.album.dto.AlbumDto;
import com.cameraman.album.repository.AlbumPhotoRepository;
import com.cameraman.album.repository.AlbumRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.gallery.domain.Gallery;
import com.cameraman.gallery.domain.Photo;
import com.cameraman.gallery.domain.PhotoStatus;
import com.cameraman.gallery.repository.PhotoRepository;
import com.cameraman.photoselection.domain.PhotoSelection;
import com.cameraman.photoselection.domain.SelectedPhoto;
import com.cameraman.photoselection.repository.PhotoSelectionRepository;
import com.cameraman.photoselection.repository.SelectedPhotoRepository;
import com.cameraman.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AlbumService {

    private static final int MAX_PHOTOS = 2000;

    private final AlbumRepository albumRepository;
    private final AlbumPhotoRepository albumPhotoRepository;
    private final PhotoRepository photoRepository;
    private final PhotoSelectionRepository photoSelectionRepository;
    private final SelectedPhotoRepository selectedPhotoRepository;
    private final StorageService storageService;
    private final com.cameraman.booking.repository.BookingRepository bookingRepository;
    private final com.cameraman.user.repository.UserRepository userRepository;

    public AlbumDto.AlbumResponse createAlbum(AlbumDto.CreateRequest request) {
        // Blank ids from forms must become null (they are foreign keys), and ids that point
        // nowhere should give a readable error instead of a raw constraint violation.
        String bookingId = blankToNull(request.getBookingId());
        String clientId = blankToNull(request.getClientId());
        String clientName = request.getClientName();
        if (bookingId != null) {
            var booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new BusinessException("The selected booking no longer exists.", "BOOKING_NOT_FOUND"));
            if (clientId == null && booking.getClient() != null) clientId = booking.getClient().getId();
            if (clientName == null || clientName.isBlank()) clientName = booking.getClientName();
        }
        if (clientId != null) {
            var user = userRepository.findById(clientId).orElse(null);
            if (user == null) { clientId = null; }
            else if (clientName == null || clientName.isBlank()) clientName = user.getFullName();
        }
        Album album = Album.builder()
                .bookingId(bookingId).clientId(clientId)
                .clientName(clientName).name(request.getName())
                .albumType(parseEnum(AlbumType.class, request.getAlbumType(), "album type"))
                .material(request.getMaterial()).notes(request.getNotes())
                .coverType(request.getCoverType() != null && !request.getCoverType().isBlank()
                        ? parseEnum(AlbumCoverType.class, request.getCoverType(), "cover type") : null)
                .pageCount(request.getPageCount() != null ? request.getPageCount() : 20)
                .includedPages(request.getIncludedPages() != null ? request.getIncludedPages() : 20)
                .basePrice(request.getBasePrice() != null ? request.getBasePrice() : java.math.BigDecimal.ZERO)
                .pricePerExtraPage(request.getPricePerExtraPage() != null ? request.getPricePerExtraPage() : java.math.BigDecimal.ZERO)
                .build();
        return toResponse(albumRepository.save(album));
    }

    /** Edits album details (name/size/cover/pricing/notes). */
    public AlbumDto.AlbumResponse updateAlbum(String id, AlbumDto.UpdateRequest request) {
        Album album = findById(id);
        album.setName(request.getName());
        album.setAlbumType(parseEnum(AlbumType.class, request.getAlbumType(), "album type"));
        album.setMaterial(request.getMaterial());
        if (request.getCoverType() != null && !request.getCoverType().isBlank()) {
            album.setCoverType(parseEnum(AlbumCoverType.class, request.getCoverType(), "cover type"));
        }
        if (request.getPageCount() != null) { album.setPageCount(request.getPageCount()); }
        if (request.getIncludedPages() != null) { album.setIncludedPages(request.getIncludedPages()); }
        if (request.getBasePrice() != null) { album.setBasePrice(request.getBasePrice()); }
        if (request.getPricePerExtraPage() != null) { album.setPricePerExtraPage(request.getPricePerExtraPage()); }
        album.setNotes(request.getNotes());
        return toResponse(albumRepository.save(album));
    }

    @Transactional(readOnly = true)
    public AlbumDto.AlbumResponse getById(String id) {
        return toResponse(findById(id));
    }

    public AlbumDto.AlbumResponse updateStatus(String id, String status, String notes) {
        Album album = findById(id);
        AlbumStatus next = parseEnum(AlbumStatus.class, status, "status");
        album.setStatus(next);
        if (notes != null) album.setNotes(notes);
        if (next == AlbumStatus.APPROVED) {
            album.setApprovedAt(LocalDateTime.now());
        }
        return toResponse(albumRepository.save(album));
    }

    public AlbumDto.AlbumResponse requestRevision(String id, String revisionNotes) {
        Album album = findById(id);
        if (album.getRevisionCount() >= album.getMaxRevisions()) {
            throw new BusinessException("Maximum revisions (" + album.getMaxRevisions() + ") reached", "MAX_REVISIONS");
        }
        album.setRevisionCount(album.getRevisionCount() + 1);
        album.setStatus(AlbumStatus.REVISION_REQUESTED);
        album.setNotes(revisionNotes);
        return toResponse(albumRepository.save(album));
    }

    @Transactional(readOnly = true)
    public PagedResponse<AlbumDto.AlbumResponse> getAll(String status, Pageable pageable) {
        var page = status != null && !status.isBlank()
                ? albumRepository.findByStatus(parseEnum(AlbumStatus.class, status, "status"), pageable)
                : albumRepository.findAll(pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<AlbumDto.AlbumResponse> getByBooking(String bookingId) {
        return albumRepository.findByBookingId(bookingId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ album photos

    @Transactional(readOnly = true)
    public List<AlbumDto.AlbumPhotoResponse> getPhotos(String albumId) {
        Album album = findById(albumId);
        List<AlbumPhoto> rows = albumPhotoRepository.findByAlbumIdOrderByPositionAsc(albumId);
        if (rows.isEmpty()) return List.of();

        Map<String, Photo> photos = photoRepository.findAllById(
                rows.stream().map(AlbumPhoto::getPhotoId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Photo::getId, p -> p));

        List<AlbumDto.AlbumPhotoResponse> out = new ArrayList<>();
        for (AlbumPhoto row : rows) {
            Photo p = photos.get(row.getPhotoId());
            if (p == null) continue;
            out.add(AlbumDto.AlbumPhotoResponse.builder()
                    .id(row.getId()).photoId(row.getPhotoId()).position(row.getPosition())
                    .pageNumber(row.getPageNumber()).note(row.getNote())
                    .filename(p.getOriginalFilename())
                    .thumbnailUrl(p.getThumbnailKey() != null ? storageService.generateSignedDownloadUrl(p.getThumbnailKey()) : null)
                    .previewUrl(p.getPreviewKey() != null ? storageService.generateSignedDownloadUrl(p.getPreviewKey()) : null)
                    .cover(row.getPhotoId().equals(album.getCoverPhotoId()))
                    .build());
        }
        return out;
    }

    /**
     * Replaces the album's whole photo list with {@code photoIds}, in that order. Photos that
     * stay keep their page number and note. Every photo must be a finished photo from a
     * gallery of the same booking (or, with no booking, the same client).
     */
    public List<AlbumDto.AlbumPhotoResponse> setPhotos(String albumId, List<String> photoIds) {
        Album album = findById(albumId);
        requireEditable(album);

        List<String> ids = photoIds == null ? List.of() : photoIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.size() > MAX_PHOTOS) {
            throw new BusinessException("An album can hold at most " + MAX_PHOTOS + " photos", "TOO_MANY_PHOTOS");
        }
        validatePhotos(album, ids);

        Map<String, AlbumPhoto> existing = albumPhotoRepository.findByAlbumIdOrderByPositionAsc(albumId).stream()
                .collect(Collectors.toMap(AlbumPhoto::getPhotoId, r -> r));

        List<AlbumPhoto> keep = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            AlbumPhoto row = existing.remove(ids.get(i));
            if (row == null) {
                row = AlbumPhoto.builder().albumId(albumId).photoId(ids.get(i)).build();
            }
            row.setPosition(i);
            keep.add(row);
        }
        if (!existing.isEmpty()) {
            albumPhotoRepository.deleteAll(existing.values());
            albumPhotoRepository.flush();
        }
        albumPhotoRepository.saveAll(keep);

        if (album.getCoverPhotoId() != null && !ids.contains(album.getCoverPhotoId())) {
            album.setCoverPhotoId(null);
            albumRepository.save(album);
        }
        return getPhotos(albumId);
    }

    /** Adds the photos a client selected (that are not already in the album) to the end. */
    public List<AlbumDto.AlbumPhotoResponse> addFromSelection(String albumId, String selectionId) {
        Album album = findById(albumId);
        requireEditable(album);
        PhotoSelection selection = photoSelectionRepository.findById(selectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Selection", "id", selectionId));

        List<String> current = albumPhotoRepository.findByAlbumIdOrderByPositionAsc(albumId).stream()
                .map(AlbumPhoto::getPhotoId).collect(Collectors.toCollection(ArrayList::new));
        for (SelectedPhoto sp : selectedPhotoRepository.findBySelectionId(selection.getId())) {
            if (!current.contains(sp.getPhotoId())) current.add(sp.getPhotoId());
        }
        return setPhotos(albumId, current);
    }

    public AlbumDto.AlbumPhotoResponse updatePhoto(String albumId, String photoId, AlbumDto.UpdatePhotoRequest req) {
        Album album = findById(albumId);
        requireEditable(album);
        AlbumPhoto row = albumPhotoRepository.findByAlbumIdOrderByPositionAsc(albumId).stream()
                .filter(r -> r.getPhotoId().equals(photoId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Album photo", "id", photoId));
        if (req.getPageNumber() != null && req.getPageNumber() < 1) {
            throw new BusinessException("Page number must be 1 or more", "INVALID_PAGE");
        }
        row.setPageNumber(req.getPageNumber());
        row.setNote(req.getNote() == null || req.getNote().isBlank() ? null : req.getNote().trim());
        albumPhotoRepository.save(row);
        return getPhotos(albumId).stream().filter(r -> r.getPhotoId().equals(photoId)).findFirst().orElseThrow();
    }

    public AlbumDto.AlbumResponse setCover(String albumId, String photoId) {
        Album album = findById(albumId);
        requireEditable(album);
        if (photoId != null && !photoId.isBlank()) {
            boolean inAlbum = albumPhotoRepository.findByAlbumIdOrderByPositionAsc(albumId).stream()
                    .anyMatch(r -> r.getPhotoId().equals(photoId));
            if (!inAlbum) {
                throw new BusinessException("Pick a cover from the photos already in the album", "COVER_NOT_IN_ALBUM");
            }
            album.setCoverPhotoId(photoId);
        } else {
            album.setCoverPhotoId(null);
        }
        return toResponse(albumRepository.save(album));
    }

    private void requireEditable(Album album) {
        AlbumStatus s = album.getStatus();
        if (s == AlbumStatus.APPROVED || s == AlbumStatus.PRINTING || s == AlbumStatus.DELIVERED) {
            throw new BusinessException("The photo set is locked once the album is approved. Request a revision to change it.", "ALBUM_LOCKED");
        }
    }

    private void validatePhotos(Album album, List<String> ids) {
        if (ids.isEmpty()) return;
        List<Photo> found = photoRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new BusinessException("One or more photos no longer exist", "PHOTO_NOT_FOUND");
        }
        for (Photo p : found) {
            if (p.getStatus() != PhotoStatus.READY) {
                throw new BusinessException("\"" + p.getOriginalFilename() + "\" is not ready yet", "PHOTO_NOT_READY");
            }
            Gallery g = p.getGallery();
            boolean matches;
            if (album.getBookingId() != null && !album.getBookingId().isBlank()) {
                matches = album.getBookingId().equals(g.getBookingId());
            } else if (album.getClientId() != null && !album.getClientId().isBlank()) {
                matches = album.getClientId().equals(g.getClientId());
            } else {
                matches = true;
            }
            if (!matches) {
                throw new BusinessException("\"" + p.getOriginalFilename() + "\" belongs to a different booking's gallery", "PHOTO_WRONG_GALLERY");
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown " + label + ": " + value, "INVALID_" + label.toUpperCase().replace(' ', '_'));
        }
    }

    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }

    private Album findById(String id) {
        return albumRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Album","id",id));
    }

    private AlbumDto.AlbumResponse toResponse(Album a) {
        return AlbumDto.AlbumResponse.builder()
                .id(a.getId()).bookingId(a.getBookingId()).clientId(a.getClientId())
                .clientName(a.getClientName()).name(a.getName())
                .albumType(a.getAlbumType().name()).material(a.getMaterial())
                .coverType(a.getCoverType() != null ? a.getCoverType().name() : null)
                .pageCount(a.getPageCount()).includedPages(a.getIncludedPages())
                .basePrice(a.getBasePrice()).pricePerExtraPage(a.getPricePerExtraPage())
                .totalPrice(a.getTotalPrice())
                .status(a.getStatus().name()).revisionCount(a.getRevisionCount())
                .maxRevisions(a.getMaxRevisions()).notes(a.getNotes())
                .approvedAt(a.getApprovedAt()).createdAt(a.getCreatedAt())
                .coverPhotoId(a.getCoverPhotoId())
                .photoCount(a.getId() == null ? 0 : albumPhotoRepository.countByAlbumId(a.getId()))
                .build();
    }
}
