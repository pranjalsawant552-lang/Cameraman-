package com.cameraman.gallery.scheduler;

import com.cameraman.gallery.domain.Gallery;
import com.cameraman.gallery.repository.GalleryRepository;
import com.cameraman.gallery.service.PhotoUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs daily and auto-deletes unselected full-resolution originals for any gallery
 * whose admin-configured expiry window (Gallery.originalsExpiryDays, counted from
 * publishedAt) has elapsed. Selected photos' originals are never touched — see
 * PhotoUploadService.purgeUnselectedOriginals for the actual purge logic, which this
 * just triggers on a schedule. Admins can also trigger this immediately for a specific
 * gallery via "force expire" instead of waiting for this job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GalleryOriginalsExpiryScheduler {

    private final GalleryRepository galleryRepository;
    private final PhotoUploadService photoUploadService;

    @Scheduled(cron = "0 30 2 * * *") // 2:30 AM daily — off-peak, after any late-night uploads settle
    public void purgeExpiredOriginals() {
        List<Gallery> candidates = galleryRepository.findPendingOriginalsExpiry();
        LocalDateTime now = LocalDateTime.now();
        int processed = 0;

        for (Gallery gallery : candidates) {
            LocalDateTime dueAt = gallery.getPublishedAt().plusDays(gallery.getOriginalsExpiryDays());
            if (now.isBefore(dueAt)) {
                continue;
            }
            try {
                int purged = photoUploadService.purgeUnselectedOriginals(gallery.getId(), false);
                log.info("Auto-expired {} unselected original(s) in gallery {} ({}) — expiry window of {} day(s) elapsed",
                        purged, gallery.getId(), gallery.getName(), gallery.getOriginalsExpiryDays());
                processed++;
            } catch (com.cameraman.common.exception.BusinessException e) {
                // e.g. NO_SELECTION: the client has not picked anything yet, so leave the originals alone
                log.info("Skipped auto-expiry for gallery {}: {}", gallery.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Failed to auto-expire originals for gallery {}: {}", gallery.getId(), e.getMessage());
            }
        }

        if (processed > 0) {
            log.info("Gallery originals-expiry job: processed {} gallery(ies)", processed);
        }
    }

    /** Hourly: drop placeholders of direct uploads that were never completed. */
    @Scheduled(cron = "0 15 * * * *")
    public void cleanupAbandonedUploads() {
        try {
            photoUploadService.cleanupStaleUploads();
        } catch (Exception e) {
            log.error("Failed to clean up abandoned uploads: {}", e.getMessage());
        }
    }
}
