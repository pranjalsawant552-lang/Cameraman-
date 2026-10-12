package com.cameraman.contact.service;

import com.cameraman.contact.domain.ContactInquiry;
import com.cameraman.contact.dto.ContactDto;
import com.cameraman.contact.repository.ContactInquiryRepository;
import com.cameraman.contact.domain.ContactInquiryReply;
import com.cameraman.contact.repository.ContactInquiryReplyRepository;
import com.cameraman.notification.service.EmailService;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactInquiryRepository repository;
    private final EmailService emailService;
    private final ContactInquiryReplyRepository replyRepository;
    private final UserRepository userRepository;

    @Transactional
    public ContactDto.InquiryResponse submit(ContactDto.InquiryRequest request) {
        ContactInquiry inquiry = ContactInquiry.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .mobile(request.getMobile() == null || request.getMobile().isBlank() ? null : request.getMobile().trim())
                .message(request.getMessage().trim())
                .status("NEW")
                .build();
        inquiry = repository.save(inquiry);

        emailService.sendContactInquiryNotification(
                inquiry.getName(), inquiry.getEmail(), inquiry.getMobile(), inquiry.getMessage());

        return toResponse(inquiry);
    }

    @Transactional(readOnly = true)
    public Page<ContactDto.InquiryResponse> getAll(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    /** Previously there was no way to mark an inquiry as handled — every
     *  submission stayed "NEW" forever with no way to track which ones
     *  staff had actually followed up on. */
    @Transactional
    public ContactDto.InquiryResponse updateStatus(String id, String status) {
        if (!java.util.List.of("NEW", "CONTACTED", "CLOSED").contains(status)) {
            throw new com.cameraman.common.exception.BusinessException(
                    "status must be one of NEW, CONTACTED, CLOSED", "INVALID_STATUS");
        }
        ContactInquiry inquiry = repository.findById(id)
                .orElseThrow(() -> new com.cameraman.common.exception.ResourceNotFoundException("Contact inquiry", "id", id));
        inquiry.setStatus(status);
        return toResponse(repository.save(inquiry));
    }

    @Transactional(readOnly = true)
    public java.util.List<ContactDto.ReplyResponse> getReplies(String inquiryId) {
        find(inquiryId);
        return replyRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId).stream()
                .map(r -> ContactDto.ReplyResponse.builder().id(r.getId()).channel(r.getChannel()).message(r.getMessage())
                        .emailed(r.isEmailed()).repliedByName(r.getRepliedByName()).createdAt(r.getCreatedAt()).build())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Records a follow-up. With channel EMAIL and sendEmail the text is emailed to the visitor first (a send failure
     * aborts, so nothing is logged as sent that was not). The inquiry moves NEW -> CONTACTED, and its reply summary updates.
     */
    @Transactional
    public ContactDto.InquiryResponse reply(String inquiryId, ContactDto.ReplyRequest req, String userId) {
        ContactInquiry inquiry = find(inquiryId);
        if (req.isSendEmail() && !"EMAIL".equals(req.getChannel())) {
            throw new com.cameraman.common.exception.BusinessException("Only an email reply can be sent from here", "INVALID_CHANNEL");
        }
        String text = req.getMessage().trim();
        if (req.isSendEmail()) {
            emailService.sendInquiryReply(inquiry.getEmail(), inquiry.getName(), text, inquiry.getMessage());
        }
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        String who = user == null ? null : (user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();

        replyRepository.save(ContactInquiryReply.builder()
                .inquiryId(inquiryId).channel(req.getChannel()).message(text).emailed(req.isSendEmail())
                .repliedById(userId).repliedByName(who).build());

        inquiry.setReplyCount(inquiry.getReplyCount() + 1);
        inquiry.setLastRepliedAt(java.time.LocalDateTime.now());
        inquiry.setLastRepliedBy(who);
        if ("NEW".equals(inquiry.getStatus())) inquiry.setStatus("CONTACTED");
        return toResponse(repository.save(inquiry));
    }

    private ContactInquiry find(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new com.cameraman.common.exception.ResourceNotFoundException("Contact inquiry", "id", id));
    }

    private ContactDto.InquiryResponse toResponse(ContactInquiry i) {
        return ContactDto.InquiryResponse.builder()
                .id(i.getId()).name(i.getName()).email(i.getEmail()).mobile(i.getMobile())
                .message(i.getMessage()).status(i.getStatus()).createdAt(i.getCreatedAt())
                .replyCount(i.getReplyCount()).lastRepliedAt(i.getLastRepliedAt()).lastRepliedBy(i.getLastRepliedBy())
                .build();
    }
}
