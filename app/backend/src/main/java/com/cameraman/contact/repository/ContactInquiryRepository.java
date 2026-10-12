package com.cameraman.contact.repository;

import com.cameraman.contact.domain.ContactInquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactInquiryRepository extends JpaRepository<ContactInquiry, String> {
    Page<ContactInquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
