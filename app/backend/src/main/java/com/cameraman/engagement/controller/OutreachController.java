package com.cameraman.engagement.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.engagement.dto.EngagementDto;
import com.cameraman.engagement.service.OutreachService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/outreach")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER')")
public class OutreachController {

    private final OutreachService outreachService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<EngagementDto.OutreachResponse>>> list(
            @RequestParam(required = false) String status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(outreachService.list(status, pageable)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:UPDATE')")
    public ResponseEntity<ApiResponse<EngagementDto.OutreachResponse>> approve(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Sent", outreachService.approve(id)));
    }

    @PostMapping("/{id}/send-edited")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:UPDATE')")
    public ResponseEntity<ApiResponse<EngagementDto.OutreachResponse>> sendEdited(
            @PathVariable String id, @RequestBody EngagementDto.UpdateOutreachRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Sent", outreachService.sendEdited(id, request.getEditedMessage())));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:UPDATE')")
    public ResponseEntity<ApiResponse<EngagementDto.OutreachResponse>> dismiss(
            @PathVariable String id, @RequestBody EngagementDto.DismissOutreachRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Dismissed", outreachService.dismiss(id, request.getReason())));
    }
}
