package com.cameraman.broadcast.controller;

import com.cameraman.broadcast.dto.BroadcastDto;
import com.cameraman.broadcast.service.BroadcastService;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk WhatsApp messaging. Restricted to admin-level roles only — this sends
 * real messages to every client with a mobile number on file in one click,
 * so it's treated with the same care as export/delete-class actions elsewhere.
 */
@RestController
@RequestMapping("/broadcasts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER')")
@Tag(name = "Broadcasts", description = "Bulk WhatsApp messaging to all clients")
public class BroadcastController {

    private final BroadcastService broadcastService;

    @GetMapping("/audience-preview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:BROADCAST:VIEW')")
    public ResponseEntity<ApiResponse<BroadcastDto.AudiencePreviewResponse>> previewAudience() {
        return ResponseEntity.ok(ApiResponse.success(broadcastService.previewAudience()));
    }

    @PostMapping(value = "/media", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:BROADCAST:SEND')")
    public ResponseEntity<ApiResponse<BroadcastDto.MediaUploadResponse>> uploadMedia(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(broadcastService.uploadMedia(file)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:BROADCAST:SEND')")
    public ResponseEntity<ApiResponse<BroadcastDto.CampaignResponse>> create(
            @Valid @RequestBody BroadcastDto.CreateCampaignRequest request,
            @CurrentUser String userId, Authentication authentication) {
        String name = authentication.getName();
        BroadcastDto.CampaignResponse response = broadcastService.createAndSend(request, userId, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Broadcast queued — sending to " + response.getTotalRecipients() + " clients", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:BROADCAST:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<BroadcastDto.CampaignResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return ResponseEntity.ok(ApiResponse.success(broadcastService.getAll(PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:BROADCAST:VIEW')")
    public ResponseEntity<ApiResponse<BroadcastDto.CampaignResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(broadcastService.getById(id)));
    }

    @GetMapping("/{id}/recipients")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:BROADCAST:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<BroadcastDto.RecipientResponse>>> getRecipients(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(broadcastService.getRecipients(id, PageRequest.of(page, size))));
    }
}
