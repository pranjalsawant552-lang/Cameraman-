package com.cameraman.contact.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.common.security.CurrentUser;
import com.cameraman.contact.dto.ContactDto;
import com.cameraman.contact.service.ContactService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Contact", description = "Public contact form")
public class ContactController {

    private final ContactService contactService;

    /**
     * Public contact form submission. Mapped under /public/** which is
     * permitAll in SecurityConfig. This endpoint previously did not exist at
     * all — the frontend posted to /contact and silently told every visitor
     * "message sent" even though the request 404'd.
     */
    @PostMapping("/public/contact")
    public ResponseEntity<ApiResponse<ContactDto.InquiryResponse>> submit(
            @Valid @RequestBody ContactDto.InquiryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thanks for reaching out — we'll get back to you soon.",
                        contactService.submit(request)));
    }

    @GetMapping("/contact")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:CONTACT:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<ContactDto.InquiryResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(
                contactService.getAll(PageRequest.of(page, size, Sort.by("createdAt").descending())))));
    }

    @PatchMapping("/contact/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:CONTACT:UPDATE')")
    public ResponseEntity<ApiResponse<ContactDto.InquiryResponse>> updateStatus(
            @PathVariable String id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(contactService.updateStatus(id, status)));
    }

    @GetMapping("/contact/{id}/replies")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:CONTACT:VIEW')")
    public ResponseEntity<ApiResponse<java.util.List<ContactDto.ReplyResponse>>> replies(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(contactService.getReplies(id)));
    }

    @PostMapping("/contact/{id}/replies")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:CONTACT:UPDATE')")
    public ResponseEntity<ApiResponse<ContactDto.InquiryResponse>> reply(
            @PathVariable String id, @Valid @RequestBody ContactDto.ReplyRequest request, @CurrentUser String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reply recorded", contactService.reply(id, request, userId)));
    }
}
