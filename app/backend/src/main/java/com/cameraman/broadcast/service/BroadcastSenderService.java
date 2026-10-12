package com.cameraman.broadcast.service;

import com.cameraman.broadcast.domain.BroadcastCampaign;
import com.cameraman.broadcast.domain.BroadcastRecipient;
import com.cameraman.broadcast.repository.BroadcastCampaignRepository;
import com.cameraman.broadcast.repository.BroadcastRecipientRepository;
import com.cameraman.whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs the actual WhatsApp send loop for a broadcast campaign, off the request
 * thread. Kept as its own bean (rather than a method on BroadcastService) so
 * that Spring's @Async proxy actually applies — a self-invoked @Async method
 * on the same bean silently executes synchronously instead of being handed
 * off to the executor, which would block the "send broadcast" HTTP request
 * for as long as the whole campaign takes to send.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastSenderService {

    private final BroadcastCampaignRepository campaignRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final WhatsAppService whatsAppService;

    /** Delay between individual sends, to stay well under WhatsApp Cloud API rate limits. */
    private static final long DELAY_BETWEEN_SENDS_MS = 1200;

    @Async("broadcastExecutor")
    public void send(String campaignId) {
        BroadcastCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return;

        campaign.setStatus("SENDING");
        campaign.setStartedAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        List<BroadcastRecipient> recipients = recipientRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);
        int sent = 0, failed = 0;

        for (BroadcastRecipient recipient : recipients) {
            try {
                whatsAppService.sendBroadcastMessage(
                        recipient.getMobile(), campaign.getMessageText(), campaign.getMediaUrl(), campaign.getMediaType());
                recipient.setStatus("SENT");
                recipient.setSentAt(LocalDateTime.now());
                sent++;
            } catch (Exception e) {
                recipient.setStatus("FAILED");
                recipient.setErrorMessage(truncate(e.getMessage(), 480));
                failed++;
                log.warn("Broadcast {} failed for {}: {}", campaignId, recipient.getMobile(), e.getMessage());
            }
            recipientRepository.save(recipient);

            // Persist running totals as we go, so the campaign detail page can poll and show live progress
            campaign.setSentCount(sent);
            campaign.setFailedCount(failed);
            campaignRepository.save(campaign);

            try {
                Thread.sleep(DELAY_BETWEEN_SENDS_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        campaign.setStatus(failed > 0 && sent == 0 ? "FAILED" : "COMPLETED");
        campaign.setCompletedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
        log.info("Broadcast {} finished: {} sent, {} failed", campaignId, sent, failed);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
