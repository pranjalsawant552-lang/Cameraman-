package com.cameraman.engagement.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.engagement.domain.OutreachChannel;
import com.cameraman.engagement.domain.OutreachStatus;
import com.cameraman.engagement.domain.SuggestedOutreach;
import com.cameraman.engagement.dto.EngagementDto;
import com.cameraman.engagement.repository.SuggestedOutreachRepository;
import com.cameraman.notification.service.SmsService;
import com.cameraman.whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OutreachService {

    private final SuggestedOutreachRepository outreachRepository;
    private final WhatsAppService whatsAppService;
    private final SmsService smsService;

    @Transactional(readOnly = true)
    public PagedResponse<EngagementDto.OutreachResponse> list(String status, Pageable pageable) {
        OutreachStatus statusEnum = (status != null && !status.isBlank()) ? OutreachStatus.valueOf(status) : OutreachStatus.PENDING_REVIEW;
        Page<SuggestedOutreach> page = outreachRepository.findByStatus(statusEnum, pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    /** Sends the suggestion as-is (no edits) via WhatsApp. */
    public EngagementDto.OutreachResponse approve(String id) {
        SuggestedOutreach outreach = findById(id);
        send(outreach, outreach.getSuggestedMessage());
        outreach.setStatus(OutreachStatus.APPROVED_SENT);
        return toResponse(outreachRepository.save(outreach));
    }

    /** Admin edited the message before sending — keeps the original suggestion
     *  text on record too, for tuning future rule wording. */
    public EngagementDto.OutreachResponse sendEdited(String id, String editedMessage) {
        SuggestedOutreach outreach = findById(id);
        if (editedMessage == null || editedMessage.isBlank()) {
            throw new BusinessException("Edited message cannot be empty", "EMPTY_MESSAGE");
        }
        send(outreach, editedMessage);
        outreach.setSuggestedMessage(editedMessage);
        outreach.setStatus(OutreachStatus.EDITED_SENT);
        return toResponse(outreachRepository.save(outreach));
    }

    public EngagementDto.OutreachResponse dismiss(String id, String reason) {
        SuggestedOutreach outreach = findById(id);
        outreach.setStatus(OutreachStatus.DISMISSED);
        outreach.setDismissedReason(reason);
        return toResponse(outreachRepository.save(outreach));
    }

    /**
     * Called by SuggestionEngineScheduler for rules marked autoSend, once it has already
     * confirmed the candidate opted in to marketing contact and has a mobile number.
     * Deliberately swallows send failures into a status rather than throwing: this runs
     * inside a scheduled batch job processing many candidates, and one delivery failure
     * (e.g. a provider outage) shouldn't stop the rest of the batch or crash the job.
     */
    public SuggestedOutreach autoSend(SuggestedOutreach outreach) {
        try {
            dispatch(outreach.getChannel(), outreach.getClientMobile(), outreach.getSuggestedMessage());
            outreach.setSentAt(LocalDateTime.now());
            outreach.setStatus(OutreachStatus.AUTO_SENT);
        } catch (Exception e) {
            log.error("Auto-send failed for outreach candidate (rule={}, client={}): {}",
                    outreach.getRuleName(), outreach.getClientName(), e.getMessage());
            outreach.setStatus(OutreachStatus.AUTO_SEND_FAILED);
        }
        return outreachRepository.save(outreach);
    }

    private void send(SuggestedOutreach outreach, String message) {
        if (outreach.getClientMobile() == null || outreach.getClientMobile().isBlank()) {
            throw new BusinessException("This client has no mobile number on file — can't send this message.", "NO_MOBILE");
        }
        dispatch(outreach.getChannel(), outreach.getClientMobile(), message);
        outreach.setSentAt(LocalDateTime.now());
    }

    private void dispatch(OutreachChannel channel, String mobile, String message) {
        if (channel == OutreachChannel.SMS) {
            smsService.send(mobile, message);
        } else {
            whatsAppService.sendTextMessage(mobile, message);
        }
    }

    private SuggestedOutreach findById(String id) {
        return outreachRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Suggested outreach", "id", id));
    }

    private EngagementDto.OutreachResponse toResponse(SuggestedOutreach o) {
        return EngagementDto.OutreachResponse.builder()
                .id(o.getId()).ruleName(o.getRuleName()).bookingId(o.getBookingId())
                .sourceType(o.getSourceType())
                .clientId(o.getClientId()).clientName(o.getClientName()).clientMobile(o.getClientMobile())
                .suggestedMessage(o.getSuggestedMessage()).channel(o.getChannel().name()).triggerDate(o.getTriggerDate())
                .occurrenceNumber(o.getOccurrenceNumber())
                .status(o.getStatus().name()).sentAt(o.getSentAt()).dismissedReason(o.getDismissedReason())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
