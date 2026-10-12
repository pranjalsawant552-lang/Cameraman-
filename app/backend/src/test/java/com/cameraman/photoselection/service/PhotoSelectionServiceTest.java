package com.cameraman.photoselection.service;

import com.cameraman.booking.dto.BookingDto;
import com.cameraman.booking.service.BookingService;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.gallery.domain.Gallery;
import com.cameraman.gallery.domain.Photo;
import com.cameraman.gallery.repository.PhotoRepository;
import com.cameraman.notification.service.NotificationPublisher;
import com.cameraman.notification.service.NotificationService;
import com.cameraman.photoselection.domain.PhotoSelection;
import com.cameraman.photoselection.domain.SelectionStatus;
import com.cameraman.photoselection.dto.SelectionDto;
import com.cameraman.photoselection.repository.PhotoSelectionRepository;
import com.cameraman.photoselection.repository.SelectedPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhotoSelectionServiceTest {

    @Mock private PhotoSelectionRepository selectionRepository;
    @Mock private SelectedPhotoRepository selectedPhotoRepository;
    @Mock private NotificationService notificationService;
    @Mock private PhotoRepository photoRepository;
    @Mock private BookingService bookingService;
    @Mock private NotificationPublisher notificationPublisher;

    private PhotoSelectionService service;
    private PhotoSelection selection;

    @BeforeEach
    void setUp() {
        service = new PhotoSelectionService(selectionRepository, selectedPhotoRepository, notificationService,
                photoRepository, bookingService, notificationPublisher);
        selection = PhotoSelection.builder().id("s1").galleryId("g1").bookingId("b1").clientId("c1")
                .accessToken("tok").selectionLimit(2).status(SelectionStatus.OPEN).build();
        when(selectionRepository.findByAccessToken("tok")).thenReturn(Optional.of(selection));
        when(selectionRepository.findById("s1")).thenReturn(Optional.of(selection));
        when(selectionRepository.save(any(PhotoSelection.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void photoInGallery(String photoId, String galleryId) {
        Photo p = Photo.builder().id(photoId).gallery(Gallery.builder().id(galleryId).build()).build();
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(p));
    }

    @Test
    void select_photoFromAnotherGallery_isRejected() {
        photoInGallery("p1", "other-gallery");
        assertThatThrownBy(() -> service.selectPhoto("tok", "p1", null, "c1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("not part");
        verify(selectedPhotoRepository, never()).save(any());
    }

    @Test
    void select_unknownPhoto_isRejected() {
        when(photoRepository.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.selectPhoto("tok", "nope", null, "c1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void select_incrementsCount_andRespectsLimit() {
        photoInGallery("p1", "g1"); photoInGallery("p2", "g1"); photoInGallery("p3", "g1");
        assertThat(service.selectPhoto("tok", "p1", null, "c1").getSelectedCount()).isEqualTo(1);
        assertThat(service.selectPhoto("tok", "p2", null, "c1").getSelectedCount()).isEqualTo(2);
        assertThatThrownBy(() -> service.selectPhoto("tok", "p3", null, "c1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("limit");
    }

    @Test
    void select_samePhotoTwice_isRejected() {
        photoInGallery("p1", "g1");
        when(selectedPhotoRepository.existsBySelectionIdAndPhotoId("s1", "p1")).thenReturn(true);
        assertThatThrownBy(() -> service.selectPhoto("tok", "p1", null, "c1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void select_byAnotherClient_looksLikeNotFound() {
        photoInGallery("p1", "g1");
        assertThatThrownBy(() -> service.selectPhoto("tok", "p1", null, "intruder"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void select_afterLock_isRejected() {
        selection.setStatus(SelectionStatus.LOCKED);
        photoInGallery("p1", "g1");
        assertThatThrownBy(() -> service.selectPhoto("tok", "p1", null, "c1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void select_whenExpired_isRejected() {
        selection.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        photoInGallery("p1", "g1");
        assertThatThrownBy(() -> service.selectPhoto("tok", "p1", null, "c1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("expired");
    }

    @Test
    void deselect_decrementsCount_neverBelowZero() {
        selection.setSelectedCount(0);
        when(selectedPhotoRepository.findBySelectionIdAndPhotoId("s1", "p1"))
                .thenReturn(Optional.of(com.cameraman.photoselection.domain.SelectedPhoto.builder().photoId("p1").build()));
        assertThat(service.deselectPhoto("tok", "p1", "c1").getSelectedCount()).isEqualTo(0);
    }

    @Test
    void lock_withNothingSelected_isRejected() {
        assertThatThrownBy(() -> service.lockSelection("tok", null, "c1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void lock_setsLocked_andMovesBookingToSelectionLocked() {
        selection.setSelectedCount(2);
        SelectionDto.SelectionResponse res = service.lockSelection("tok", "please crop #2", "c1");

        assertThat(res.getStatus()).isEqualTo("LOCKED");
        assertThat(res.getClientNotes()).isEqualTo("please crop #2");
        ArgumentCaptor<BookingDto.UpdateStageRequest> req = ArgumentCaptor.forClass(BookingDto.UpdateStageRequest.class);
        verify(bookingService).updateOrderStage(eq("b1"), req.capture(), eq("c1"));
        assertThat(req.getValue().getStage()).isEqualTo("SELECTION_LOCKED");
    }

    @Test
    void lock_stillSucceeds_whenStageUpdateFails() {
        selection.setSelectedCount(1);
        when(bookingService.updateOrderStage(anyString(), any(), anyString())).thenThrow(new RuntimeException("boom"));
        assertThat(service.lockSelection("tok", null, "c1").getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void approve_requiresLocked() {
        assertThatThrownBy(() -> service.approveSelection("s1", "staff")).isInstanceOf(BusinessException.class);
    }

    @Test
    void approve_movesBookingToAlbumDesigning_andNotifiesClient() {
        selection.setStatus(SelectionStatus.LOCKED);
        selection.setSelectedCount(12);

        SelectionDto.SelectionResponse res = service.approveSelection("s1", "staff-1");

        assertThat(res.getStatus()).isEqualTo("APPROVED");
        ArgumentCaptor<BookingDto.UpdateStageRequest> req = ArgumentCaptor.forClass(BookingDto.UpdateStageRequest.class);
        verify(bookingService).updateOrderStage(eq("b1"), req.capture(), eq("staff-1"));
        assertThat(req.getValue().getStage()).isEqualTo("ALBUM_DESIGNING");
        verify(notificationPublisher).notifyUser(eq("c1"), eq("SELECTION_APPROVED"), anyString(), anyString(), eq("Selection"), eq("s1"));
    }

    @Test
    void reopen_onlyFromLocked_andClearsLockTime() {
        assertThatThrownBy(() -> service.reopenSelection("s1")).isInstanceOf(BusinessException.class);

        selection.setStatus(SelectionStatus.LOCKED);
        selection.setLockedAt(LocalDateTime.now());
        assertThat(service.reopenSelection("s1").getStatus()).isEqualTo("OPEN");
        assertThat(selection.getLockedAt()).isNull();
    }

    @Test
    void staffLock_requiresOpenAndSomePhotos() {
        assertThatThrownBy(() -> service.lockSelectionByStaff("s1", null)).isInstanceOf(BusinessException.class); // 0 selected
        selection.setSelectedCount(1);
        assertThat(service.lockSelectionByStaff("s1", "by phone").getStatus()).isEqualTo("LOCKED");
        assertThatThrownBy(() -> service.lockSelectionByStaff("s1", null)).isInstanceOf(BusinessException.class); // already locked
    }
}
