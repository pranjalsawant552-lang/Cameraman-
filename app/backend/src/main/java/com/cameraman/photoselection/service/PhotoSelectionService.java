package com.cameraman.photoselection.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.notification.service.NotificationService;
import com.cameraman.photoselection.domain.*;
import com.cameraman.photoselection.dto.SelectionDto;
import com.cameraman.photoselection.repository.PhotoSelectionRepository;
import com.cameraman.photoselection.repository.SelectedPhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class PhotoSelectionService {

    private final PhotoSelectionRepository selectionRepository;
    private final SelectedPhotoRepository selectedPhotoRepository;
    private final NotificationService notificationService;
    private final com.cameraman.gallery.repository.PhotoRepository photoRepository;
    private final com.cameraman.booking.service.BookingService bookingService;
    private final com.cameraman.notification.service.NotificationPublisher notificationPublisher;

    public SelectionDto.SelectionResponse createSelection(SelectionDto.CreateRequest request) {
        PhotoSelection selection = PhotoSelection.builder()
                .galleryId(request.getGalleryId())
                .bookingId(request.getBookingId())
                .clientId(request.getClientId())
                .clientName(request.getClientName())
                .accessToken(UUID.randomUUID().toString().replace("-", ""))
                .selectionLimit(request.getSelectionLimit())
                .status(SelectionStatus.OPEN)
                .expiresAt(request.getExpiresAt())
                .adminNotes(request.getAdminNotes())
                .build();
        return toResponse(selectionRepository.save(selection));
    }

    @Transactional(readOnly = true)
    public SelectionDto.SelectionResponse getByToken(String token, String currentUserId) {
        PhotoSelection selection = selectionRepository.findByAccessToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Selection not found"));
        verifyOwnership(selection, currentUserId);

        if (selection.getStatus() == SelectionStatus.EXPIRED) {
            throw new BusinessException("This selection link has expired", "SELECTION_EXPIRED");
        }
        if (selection.getExpiresAt() != null && LocalDateTime.now().isAfter(selection.getExpiresAt())) {
            selection.setStatus(SelectionStatus.EXPIRED);
            selectionRepository.save(selection);
            throw new BusinessException("This selection link has expired", "SELECTION_EXPIRED");
        }
        return toResponse(selection);
    }

    @Transactional(readOnly = true)
    public List<SelectionDto.SelectionResponse> getMySelections(String currentUserId) {
        return selectionRepository.findByClientId(currentUserId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Staff-facing list — previously there was no way at all for staff to browse selections. */
    @Transactional(readOnly = true)
    public PagedResponse<SelectionDto.SelectionResponse> getAll(String status, Pageable pageable) {
        var page = (status != null && !status.isBlank())
                ? selectionRepository.findByStatus(SelectionStatus.valueOf(status.toUpperCase()), pageable)
                : selectionRepository.findAll(pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    public SelectionDto.SelectionResponse selectPhoto(String token, String photoId, String notes, String currentUserId) {
        PhotoSelection selection = getActiveSelection(token, currentUserId);

        // A client may only pick photos that are actually in THIS selection's gallery.
        boolean inGallery = photoRepository.findById(photoId)
                .map(p -> p.getGallery() != null && selection.getGalleryId().equals(p.getGallery().getId()))
                .orElse(false);
        if (!inGallery) {
            throw new BusinessException("That photo is not part of this gallery", "PHOTO_NOT_IN_GALLERY");
        }

        if (selectedPhotoRepository.existsBySelectionIdAndPhotoId(selection.getId(), photoId)) {
            throw new BusinessException("Photo already selected", "ALREADY_SELECTED");
        }

        if (selection.getSelectionLimit() != null &&
                selection.getSelectedCount() >= selection.getSelectionLimit()) {
            throw new BusinessException(
                    "Selection limit of " + selection.getSelectionLimit() + " photos reached",
                    "SELECTION_LIMIT_REACHED");
        }

        SelectedPhoto sp = SelectedPhoto.builder()
                .selection(selection)
                .photoId(photoId)
                .notes(notes)
                .build();
        selectedPhotoRepository.save(sp);

        selection.setSelectedCount(selection.getSelectedCount() + 1);
        return toResponse(selectionRepository.save(selection));
    }

    /**
     * Saves the client's picks as a draft: the given list becomes the selection (added photos are
     * inserted, removed ones deleted) in a single request. The client can pick hundreds of photos in
     * the browser, save, close the page and come back later; nothing is submitted to the studio until
     * lockSelection. Idempotent, so an auto-save that is retried does no harm.
     */
    public SelectionDto.SelectionResponse saveDraft(String token, SelectionDto.SaveDraftRequest request, String currentUserId) {
        PhotoSelection selection = getActiveSelection(token, currentUserId);

        java.util.LinkedHashSet<String> wanted = new java.util.LinkedHashSet<>(
                request.getPhotoIds() != null ? request.getPhotoIds() : java.util.List.<String>of());

        if (selection.getSelectionLimit() != null && wanted.size() > selection.getSelectionLimit()) {
            throw new BusinessException("You can choose up to " + selection.getSelectionLimit()
                    + " photos. Remove " + (wanted.size() - selection.getSelectionLimit()) + " to save.", "SELECTION_LIMIT_REACHED");
        }

        var found = photoRepository.findAllById(wanted);
        boolean allInGallery = found.size() == wanted.size() && found.stream()
                .allMatch(p -> p.getGallery() != null && selection.getGalleryId().equals(p.getGallery().getId()));
        if (!allInGallery) {
            throw new BusinessException("Some of those photos are not part of this gallery", "PHOTO_NOT_IN_GALLERY");
        }

        var existing = selectedPhotoRepository.findBySelectionId(selection.getId());
        java.util.Set<String> have = existing.stream().map(SelectedPhoto::getPhotoId).collect(Collectors.toSet());

        List<SelectedPhoto> toRemove = existing.stream().filter(sp -> !wanted.contains(sp.getPhotoId())).collect(Collectors.toList());
        selectedPhotoRepository.deleteAll(toRemove);
        List<SelectedPhoto> toAdd = wanted.stream().filter(id -> !have.contains(id))
                .map(id -> SelectedPhoto.builder().selection(selection).photoId(id).build())
                .collect(Collectors.toList());
        selectedPhotoRepository.saveAll(toAdd);

        selection.setSelectedCount(wanted.size());
        if (request.getClientNotes() != null) selection.setClientNotes(request.getClientNotes());
        selection.setUpdatedAt(LocalDateTime.now());   // so "saved at" moves even when nothing changed
        return toResponse(selectionRepository.save(selection));
    }

    public SelectionDto.SelectionResponse deselectPhoto(String token, String photoId, String currentUserId) {
        PhotoSelection selection = getActiveSelection(token, currentUserId);

        SelectedPhoto sp = selectedPhotoRepository.findBySelectionIdAndPhotoId(selection.getId(), photoId)
                .orElseThrow(() -> new BusinessException("Photo not in selection", "PHOTO_NOT_SELECTED"));

        selectedPhotoRepository.delete(sp);
        selection.setSelectedCount(Math.max(0, selection.getSelectedCount() - 1));
        return toResponse(selectionRepository.save(selection));
    }

    public SelectionDto.SelectionResponse lockSelection(String token, String clientNotes, String currentUserId) {
        PhotoSelection selection = getActiveSelection(token, currentUserId);

        if (selection.getSelectedCount() == 0) {
            throw new BusinessException("Cannot lock selection with no photos selected", "NO_PHOTOS_SELECTED");
        }

        selection.setStatus(SelectionStatus.LOCKED);
        selection.setLockedAt(LocalDateTime.now());
        selection.setClientNotes(clientNotes);
        selection = selectionRepository.save(selection);
        advanceBookingStage(selection, "SELECTION_LOCKED", "Client submitted their photo selection", currentUserId);

        try {
            notificationService.sendSelectionLockedNotification(selection);
        } catch (Exception e) {
            log.error("Failed to send selection locked notification: {}", e.getMessage());
        }
        return toResponse(selection);
    }

    public SelectionDto.SelectionResponse approveSelection(String id) {
        return approveSelection(id, null);
    }

    /** Staff approve a LOCKED selection: moves the booking to album design and tells the client. */
    public SelectionDto.SelectionResponse approveSelection(String id, String approverId) {
        PhotoSelection selection = selectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Selection", "id", id));
        if (selection.getStatus() != SelectionStatus.LOCKED) {
            throw new BusinessException("Only a locked selection can be approved", "INVALID_SELECTION_STATE");
        }
        selection.setStatus(SelectionStatus.APPROVED);
        selection = selectionRepository.save(selection);

        advanceBookingStage(selection, "ALBUM_DESIGNING", "Selection approved: " + selection.getSelectedCount() + " photos", approverId);
        try {
            notificationPublisher.notifyUser(selection.getClientId(), "SELECTION_APPROVED",
                    "Your photo selection is approved",
                    "We have received your " + selection.getSelectedCount() + " chosen photos and will start on your order.",
                    "Selection", selection.getId());
        } catch (Exception e) {
            log.error("Failed to notify client of selection approval: {}", e.getMessage());
        }
        return toResponse(selection);
    }

    /** Staff re-open a LOCKED selection so the client can change their picks (APPROVED ones stay final). */
    public SelectionDto.SelectionResponse reopenSelection(String id) {
        PhotoSelection selection = selectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Selection", "id", id));
        if (selection.getStatus() != SelectionStatus.LOCKED) {
            throw new BusinessException("Only a locked selection can be re-opened", "INVALID_SELECTION_STATE");
        }
        selection.setStatus(SelectionStatus.OPEN);
        selection.setLockedAt(null);
        return toResponse(selectionRepository.save(selection));
    }

    /** Best-effort pipeline update: a failure here must never undo the client's selection. */
    private void advanceBookingStage(PhotoSelection selection, String stage, String note, String actorId) {
        if (selection.getBookingId() == null || selection.getBookingId().isBlank()) return;
        try {
            com.cameraman.booking.dto.BookingDto.UpdateStageRequest req = new com.cameraman.booking.dto.BookingDto.UpdateStageRequest();
            req.setStage(stage);
            req.setNote(note);
            bookingService.updateOrderStage(selection.getBookingId(), req, actorId != null ? actorId : selection.getClientId());
        } catch (Exception e) {
            log.warn("Could not advance booking {} to {}: {}", selection.getBookingId(), stage, e.getMessage());
        }
    }

    /** Staff-facing counterpart to lockSelection(token, ...) above — for a client who gave
     *  their final choices some other way (phone call, in person) and can't/won't submit
     *  through their own link. Same guard (must have at least one photo selected already)
     *  and same downstream effects (status, timestamp, notification) as the client's own
     *  lock action; the only difference is being reached by selection id under staff auth
     *  instead of by the client's token. This didn't exist before — the staff detail
     *  screen's "Lock Selection" button called an endpoint that was never built. */
    public SelectionDto.SelectionResponse lockSelectionByStaff(String id, String staffNotes) {
        PhotoSelection selection = selectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Selection", "id", id));

        if (selection.getStatus() != SelectionStatus.OPEN) {
            throw new BusinessException("Only an open selection can be locked", "INVALID_SELECTION_STATE");
        }
        if (selection.getSelectedCount() == 0) {
            throw new BusinessException("Cannot lock selection with no photos selected", "NO_PHOTOS_SELECTED");
        }

        selection.setStatus(SelectionStatus.LOCKED);
        selection.setLockedAt(LocalDateTime.now());
        if (staffNotes != null && !staffNotes.isBlank()) {
            selection.setClientNotes(staffNotes);
        }
        selection = selectionRepository.save(selection);
        advanceBookingStage(selection, "SELECTION_LOCKED", "Selection locked by staff", null);

        try {
            notificationService.sendSelectionLockedNotification(selection);
        } catch (Exception e) {
            log.error("Failed to send selection locked notification: {}", e.getMessage());
        }
        return toResponse(selection);
    }

    @Transactional(readOnly = true)
    public List<SelectionDto.SelectedPhotoResponse> getSelectedPhotos(String selectionId, String currentUserId, boolean isStaff) {
        PhotoSelection selection = selectionRepository.findById(selectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Selection", "id", selectionId));
        if (!isStaff) {
            verifyOwnership(selection, currentUserId);
        }
        return selectedPhotoRepository.findBySelectionId(selectionId).stream()
                .map(sp -> SelectionDto.SelectedPhotoResponse.builder()
                        .id(sp.getId()).photoId(sp.getPhotoId())
                        .selectedAt(sp.getSelectedAt()).favourite(sp.isFavourite())
                        .notes(sp.getNotes()).build())
                .collect(Collectors.toList());
    }

    /**
     * Selections are private: only the client the selection was created for
     * (and staff, via the admin-only endpoints) may view or act on it. This
     * enforces requirement that a client can only see/select photos meant
     * for them after logging in - a raw link alone is not enough.
     */
    private void verifyOwnership(PhotoSelection selection, String currentUserId) {
        if (selection.getClientId() != null && !selection.getClientId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Selection not found");
        }
    }

    private PhotoSelection getActiveSelection(String token, String currentUserId) {
        PhotoSelection selection = selectionRepository.findByAccessToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Selection not found"));
        verifyOwnership(selection, currentUserId);
        if (selection.getStatus() != SelectionStatus.OPEN) {
            throw new BusinessException("Selection is " + selection.getStatus(), "SELECTION_NOT_OPEN");
        }
        if (selection.getExpiresAt() != null && LocalDateTime.now().isAfter(selection.getExpiresAt())) {
            throw new BusinessException("This selection link has expired", "SELECTION_EXPIRED");
        }
        return selection;
    }

    private SelectionDto.SelectionResponse toResponse(PhotoSelection s) {
        return SelectionDto.SelectionResponse.builder()
                .id(s.getId()).galleryId(s.getGalleryId()).bookingId(s.getBookingId())
                .clientId(s.getClientId()).clientName(s.getClientName())
                .accessToken(s.getAccessToken()).selectionLimit(s.getSelectionLimit())
                .selectedCount(s.getSelectedCount()).status(s.getStatus().name())
                .lockedAt(s.getLockedAt()).expiresAt(s.getExpiresAt())
                .adminNotes(s.getAdminNotes()).clientNotes(s.getClientNotes())
                .createdAt(s.getCreatedAt()).lastSavedAt(s.getUpdatedAt()).build();
    }
}
