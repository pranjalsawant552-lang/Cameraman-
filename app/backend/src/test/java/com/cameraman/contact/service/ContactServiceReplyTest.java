package com.cameraman.contact.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.contact.domain.ContactInquiry;
import com.cameraman.contact.domain.ContactInquiryReply;
import com.cameraman.contact.dto.ContactDto;
import com.cameraman.contact.repository.ContactInquiryReplyRepository;
import com.cameraman.contact.repository.ContactInquiryRepository;
import com.cameraman.notification.service.EmailService;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactServiceReplyTest {

    @Mock private ContactInquiryRepository repository;
    @Mock private EmailService emailService;
    @Mock private ContactInquiryReplyRepository replyRepository;
    @Mock private UserRepository userRepository;

    private ContactService service;
    private ContactInquiry inquiry;

    @BeforeEach
    void setUp() {
        service = new ContactService(repository, emailService, replyRepository, userRepository);
        inquiry = ContactInquiry.builder().id("i1").name("Meera").email("meera@example.com").message("Hello").status("NEW").build();
        when(repository.findById("i1")).thenReturn(Optional.of(inquiry));
        when(repository.save(any(ContactInquiry.class))).thenAnswer(a -> a.getArgument(0));
    }

    private ContactDto.ReplyRequest req(String channel, String message, boolean send) {
        return new ContactDto.ReplyRequest(channel, message, send);
    }

    @Test
    void emailReplyIsSentLoggedAndMovesInquiryToContacted() {
        ContactDto.InquiryResponse r = service.reply("i1", req("EMAIL", "Yes we do", true), null);

        verify(emailService).sendInquiryReply("meera@example.com", "Meera", "Yes we do", "Hello");
        ArgumentCaptor<ContactInquiryReply> saved = ArgumentCaptor.forClass(ContactInquiryReply.class);
        verify(replyRepository).save(saved.capture());
        assertThat(saved.getValue().isEmailed()).isTrue();
        assertThat(r.getStatus()).isEqualTo("CONTACTED");
        assertThat(r.getReplyCount()).isEqualTo(1);
        assertThat(r.getLastRepliedAt()).isNotNull();
    }

    @Test
    void loggedPhoneCallSendsNoEmailAndKeepsAClosedInquiryClosed() {
        inquiry.setStatus("CLOSED");
        ContactDto.InquiryResponse r = service.reply("i1", req("PHONE", "Called back", false), null);

        verify(emailService, never()).sendInquiryReply(any(), any(), any(), any());
        assertThat(r.getStatus()).isEqualTo("CLOSED");
        assertThat(r.getReplyCount()).isEqualTo(1);
    }

    @Test
    void failedEmailRecordsNothing() {
        doThrow(new BusinessException("The email could not be sent.", "EMAIL_SEND_FAILED"))
                .when(emailService).sendInquiryReply(any(), any(), any(), any());

        assertThatThrownBy(() -> service.reply("i1", req("EMAIL", "Hi", true), null))
                .isInstanceOf(BusinessException.class);
        verify(replyRepository, never()).save(any());
        assertThat(inquiry.getReplyCount()).isZero();
    }

    @Test
    void onlyTheEmailChannelCanBeSentFromHere() {
        assertThatThrownBy(() -> service.reply("i1", req("PHONE", "Hi", true), null))
                .isInstanceOf(BusinessException.class);
        verify(emailService, never()).sendInquiryReply(any(), any(), any(), any());
    }
}
