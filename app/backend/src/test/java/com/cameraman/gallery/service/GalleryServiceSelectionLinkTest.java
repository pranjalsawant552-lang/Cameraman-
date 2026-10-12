package com.cameraman.gallery.service;

import com.cameraman.gallery.domain.Gallery;
import com.cameraman.gallery.domain.GalleryStatus;
import com.cameraman.gallery.dto.GalleryDto;
import com.cameraman.gallery.repository.GalleryRepository;
import com.cameraman.gallery.repository.PhotoRepository;
import com.cameraman.notification.service.NotificationPublisher;
import com.cameraman.notification.service.NotificationService;
import com.cameraman.photoselection.domain.PhotoSelection;
import com.cameraman.photoselection.domain.SelectionStatus;
import com.cameraman.photoselection.repository.PhotoSelectionRepository;
import com.cameraman.settings.service.StudioSettingsService;
import com.cameraman.storage.service.StorageService;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * getGalleryByToken() previously left clients with no path from viewing their gallery to
 * actually picking their favorites — see gallery-view.component.ts's new CTA banner, which
 * this openSelectionToken field feeds. These tests lock in the resolution logic: it must
 * surface a token only for a genuinely OPEN selection, and stay null for anything else
 * (no selection at all, or one that's already LOCKED/APPROVED/EXPIRED) — an already-decided
 * selection showing "pick your favorites" again would be a confusing regression, not a fix.
 */
@ExtendWith(MockitoExtension.class)
class GalleryServiceSelectionLinkTest {

    @Mock private GalleryRepository galleryRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private StorageService storageService;
    @Mock private PhotoUploadService photoUploadService;
    @Mock private PhotoSelectionRepository photoSelectionRepository;
    // These 4 were added to GalleryService before this test file was last touched — the
    // constructor call below was never updated to match, so this test was already failing
    // to compile independent of anything else. Unused by the tests below (none of them
    // exercise a path that touches these), but required for construction to resolve.
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private StudioSettingsService settingsService;

    private GalleryService service;

    @BeforeEach
    void setUp() {
        service = new GalleryService(galleryRepository, photoRepository, storageService,
                photoUploadService, photoSelectionRepository, userRepository,
                notificationService, notificationPublisher, settingsService,
                null /* galleryViewerRepository */);
    }

    private Gallery publishedGallery() {
        return Gallery.builder()
                .id("g-1").name("Test Gallery").accessToken("tok-1")
                .status(GalleryStatus.PUBLISHED)
                .accessTokenExpires(LocalDateTime.now().plusDays(10))
                .build();
    }

    @Test
    void openSelection_surfacesItsToken() {
        Gallery gallery = publishedGallery();
        when(galleryRepository.findByAccessToken("tok-1")).thenReturn(Optional.of(gallery));
        when(galleryRepository.save(gallery)).thenReturn(gallery);

        PhotoSelection open = PhotoSelection.builder()
                .galleryId("g-1").accessToken("sel-tok-1").status(SelectionStatus.OPEN).build();
        when(photoSelectionRepository.findByGalleryId("g-1")).thenReturn(List.of(open));

        GalleryDto.GalleryResponse response = service.getGalleryByToken("tok-1", null);

        assertThat(response.getOpenSelectionToken()).isEqualTo("sel-tok-1");
    }

    @Test
    void lockedSelection_doesNotSurfaceAToken() {
        Gallery gallery = publishedGallery();
        when(galleryRepository.findByAccessToken("tok-1")).thenReturn(Optional.of(gallery));
        when(galleryRepository.save(gallery)).thenReturn(gallery);

        PhotoSelection locked = PhotoSelection.builder()
                .galleryId("g-1").accessToken("sel-tok-2").status(SelectionStatus.LOCKED).build();
        when(photoSelectionRepository.findByGalleryId("g-1")).thenReturn(List.of(locked));

        GalleryDto.GalleryResponse response = service.getGalleryByToken("tok-1", null);

        assertThat(response.getOpenSelectionToken()).isNull();
    }

    @Test
    void noSelectionAtAll_tokenStaysNull() {
        Gallery gallery = publishedGallery();
        when(galleryRepository.findByAccessToken("tok-1")).thenReturn(Optional.of(gallery));
        when(galleryRepository.save(gallery)).thenReturn(gallery);
        when(photoSelectionRepository.findByGalleryId("g-1")).thenReturn(List.of());

        GalleryDto.GalleryResponse response = service.getGalleryByToken("tok-1", null);

        assertThat(response.getOpenSelectionToken()).isNull();
    }

    @Test
    void mixOfOldAndOpenSelections_picksTheOpenOne() {
        // A gallery could realistically have an old APPROVED selection from a prior round
        // plus a new OPEN one — must not accidentally grab the stale one.
        Gallery gallery = publishedGallery();
        when(galleryRepository.findByAccessToken("tok-1")).thenReturn(Optional.of(gallery));
        when(galleryRepository.save(gallery)).thenReturn(gallery);

        PhotoSelection approved = PhotoSelection.builder()
                .galleryId("g-1").accessToken("sel-old").status(SelectionStatus.APPROVED).build();
        PhotoSelection open = PhotoSelection.builder()
                .galleryId("g-1").accessToken("sel-new").status(SelectionStatus.OPEN).build();
        when(photoSelectionRepository.findByGalleryId("g-1")).thenReturn(List.of(approved, open));

        GalleryDto.GalleryResponse response = service.getGalleryByToken("tok-1", null);

        assertThat(response.getOpenSelectionToken()).isEqualTo("sel-new");
    }
}
