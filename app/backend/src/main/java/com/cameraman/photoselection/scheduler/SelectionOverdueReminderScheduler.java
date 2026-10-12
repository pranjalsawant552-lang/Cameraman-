package com.cameraman.photoselection.scheduler;

import com.cameraman.photoselection.domain.PhotoSelection;
import com.cameraman.photoselection.repository.PhotoSelectionRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import com.cameraman.whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs daily and sends a one-time WhatsApp nudge to any client whose photo
 * selection is still OPEN after their expected deadline (PhotoSelection.expiresAt)
 * has passed — "you haven't picked your photos yet" — using the
 * sendSelectionReminder template that already existed in WhatsAppService but was
 * never actually wired up to anything.
 *
 * Deliberately a one-time reminder, not a repeating nag: reminderSentAt is set the
 * first time it fires, so the same client isn't messaged every day they stay
 * overdue. If you want an escalating series instead (day 1, day 7, day 14), that's
 * a straightforward extension of findOverdueNeedingReminder — flag it if wanted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectionOverdueReminderScheduler {

    private final PhotoSelectionRepository photoSelectionRepository;
    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;

    @Scheduled(cron = "0 0 11 * * *") // 11:00 AM daily — daytime send, more likely to be seen/actioned
    @Transactional
    public void sendOverdueSelectionReminders() {
        List<PhotoSelection> overdue = photoSelectionRepository.findOverdueNeedingReminder();
        if (overdue.isEmpty()) {
            return;
        }

        int sent = 0;
        for (PhotoSelection selection : overdue) {
            try {
                String mobile = resolveMobile(selection);
                if (mobile == null || mobile.isBlank()) {
                    log.warn("Selection {} is overdue but has no client mobile on file — skipping WhatsApp reminder", selection.getId());
                    continue;
                }

                int remaining = selection.getSelectionLimit() != null
                        ? Math.max(0, selection.getSelectionLimit() - selection.getSelectedCount())
                        : 0;

                whatsAppService.sendSelectionReminder(mobile, selection.getClientName(), selection.getAccessToken(), remaining);
                selection.setReminderSentAt(LocalDateTime.now());
                photoSelectionRepository.save(selection);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send overdue-selection reminder for selection {}: {}", selection.getId(), e.getMessage());
            }
        }

        log.info("Overdue-selection reminder job: sent {}/{} reminder(s)", sent, overdue.size());
    }

    private String resolveMobile(PhotoSelection selection) {
        if (selection.getClientId() == null) {
            return null;
        }
        return userRepository.findById(selection.getClientId()).map(User::getMobile).orElse(null);
    }
}
