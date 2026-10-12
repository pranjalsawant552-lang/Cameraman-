package com.cameraman.broadcast.service;

import com.cameraman.broadcast.domain.BroadcastCampaign;
import com.cameraman.broadcast.domain.BroadcastRecipient;
import com.cameraman.broadcast.dto.BroadcastDto;
import com.cameraman.broadcast.repository.BroadcastCampaignRepository;
import com.cameraman.broadcast.repository.BroadcastRecipientRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.storage.service.StorageService;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import com.cameraman.whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final BroadcastCampaignRepository campaignRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final WhatsAppService whatsAppService;
    private final BroadcastSenderService senderService;

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final List<String> ALLOWED_VIDEO_TYPES = List.of("video/mp4", "video/3gpp");
    private static final long MAX_MEDIA_SIZE = 16L * 1024 * 1024; // WhatsApp's own limit for images/short video

    /** Who would actually receive a broadcast right now, without creating anything yet. */
    @Transactional(readOnly = true)
    public BroadcastDto.AudiencePreviewResponse previewAudience() {
        List<User> eligible = userRepository.findBroadcastEligibleClients();
        List<String> sample = eligible.stream()
                .limit(5)
                .map(u -> (u.getFirstName() + " " + (u.getLastName() != null ? u.getLastName() : "")).trim())
                .collect(Collectors.toList());
        return BroadcastDto.AudiencePreviewResponse.builder()
                .totalEligible(eligible.size())
                .sampleNames(sample)
                .build();
    }

    public BroadcastDto.MediaUploadResponse uploadMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty", "EMPTY_FILE");
        }
        if (file.getSize() > MAX_MEDIA_SIZE) {
            throw new BusinessException("File exceeds WhatsApp's 16MB media limit", "FILE_TOO_LARGE");
        }
        String contentType = file.getContentType();
        String mediaType;
        if (ALLOWED_IMAGE_TYPES.contains(contentType)) {
            mediaType = "IMAGE";
        } else if (ALLOWED_VIDEO_TYPES.contains(contentType)) {
            mediaType = "VIDEO";
        } else {
            throw new BusinessException(
                    "Unsupported file type for WhatsApp: " + contentType + ". Use JPG/PNG/WebP images or MP4 video.",
                    "INVALID_FILE_TYPE");
        }
        try {
            var uploaded = storageService.uploadFile(
                    file.getInputStream(), "broadcasts", file.getOriginalFilename(), contentType, file.getSize());
            return BroadcastDto.MediaUploadResponse.builder().url(uploaded.getUrl()).mediaType(mediaType).build();
        } catch (IOException e) {
            throw new BusinessException("Failed to read uploaded file", "FILE_READ_ERROR");
        }
    }

    /**
     * Creates the campaign and its recipient rows synchronously (fast, just DB
     * writes), then kicks off the actual WhatsApp sending in the background and
     * returns immediately — sending to potentially hundreds of clients one at a
     * time (see DELAY_BETWEEN_SENDS_MS) would otherwise make this call time out.
     */
    @Transactional
    public BroadcastDto.CampaignResponse createAndSend(
            BroadcastDto.CreateCampaignRequest request,
            String createdByUserId,
            String createdByName) {

        // Check WhatsApp configuration
        if (!whatsAppService.isConfigured()) {
            throw new BusinessException(
                    "WhatsApp isn't connected yet. Add whatsapp.access-token "
                            + "and whatsapp.phone-number-id to send broadcasts.",
                    "WHATSAPP_NOT_CONFIGURED"
            );
        }

        // Find eligible clients
        List<User> eligible = userRepository.findBroadcastEligibleClients();

        if (eligible.isEmpty()) {
            throw new BusinessException(
                    "No clients with a mobile number on file to send to",
                    "NO_RECIPIENTS"
            );
        }

        // Determine media type
        String mediaType =
                (request.getMediaUrl() == null || request.getMediaUrl().isBlank())
                        ? "NONE"
                        : request.getMediaType();

        // Create campaign
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .title(request.getTitle())
                .messageText(request.getMessageText())
                .mediaUrl(request.getMediaUrl())
                .mediaType(mediaType)
                .status("QUEUED")
                .totalRecipients(eligible.size())
                .createdBy(createdByUserId)
                .createdByName(createdByName)
                .build();

        // Save campaign
        campaign = campaignRepository.save(campaign);

        // IMPORTANT:
        // Create a final reference so it can safely be used inside the lambda.
        final BroadcastCampaign savedCampaign = campaign;

        // Create broadcast recipients
        List<BroadcastRecipient> recipients = eligible.stream()
                .map(u -> BroadcastRecipient.builder()
                        .campaignId(savedCampaign.getId())
                        .userId(u.getId())
                        .name(
                                (u.getFirstName() + " "
                                        + (u.getLastName() != null
                                        ? u.getLastName()
                                        : ""))
                                        .trim()
                        )
                        .mobile(u.getMobile())
                        .status("PENDING")
                        .build()
                )
                .collect(Collectors.toList());

        // Save recipients
        recipientRepository.saveAll(recipients);

        // Start campaign sending
        sendCampaignAsync(savedCampaign.getId());

        // Return response
        return toResponse(savedCampaign);
    }

    private void sendCampaignAsync(String campaignId) {
        senderService.send(campaignId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BroadcastDto.CampaignResponse> getAll(Pageable pageable) {
        return PagedResponse.of(campaignRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public BroadcastDto.CampaignResponse getById(String id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<BroadcastDto.RecipientResponse> getRecipients(String campaignId, Pageable pageable) {
        Page<BroadcastRecipient> page = recipientRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId, pageable);
        return PagedResponse.of(page.map(this::toRecipientResponse));
    }

    private BroadcastCampaign findById(String id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", id));
    }

    private BroadcastDto.CampaignResponse toResponse(BroadcastCampaign c) {
        return BroadcastDto.CampaignResponse.builder()
                .id(c.getId()).title(c.getTitle()).messageText(c.getMessageText())
                .mediaUrl(c.getMediaUrl()).mediaType(c.getMediaType()).status(c.getStatus())
                .totalRecipients(c.getTotalRecipients()).sentCount(c.getSentCount()).failedCount(c.getFailedCount())
                .createdByName(c.getCreatedByName()).createdAt(c.getCreatedAt())
                .startedAt(c.getStartedAt()).completedAt(c.getCompletedAt())
                .build();
    }

    private BroadcastDto.RecipientResponse toRecipientResponse(BroadcastRecipient r) {
        return BroadcastDto.RecipientResponse.builder()
                .id(r.getId()).name(r.getName()).mobile(r.getMobile()).status(r.getStatus())
                .errorMessage(r.getErrorMessage()).sentAt(r.getSentAt())
                .build();
    }
}
