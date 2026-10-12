package com.cameraman.contact.repository;

import com.cameraman.contact.domain.ContactInquiryReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactInquiryReplyRepository extends JpaRepository<ContactInquiryReply, String> {
    List<ContactInquiryReply> findByInquiryIdOrderByCreatedAtAsc(String inquiryId);
}
