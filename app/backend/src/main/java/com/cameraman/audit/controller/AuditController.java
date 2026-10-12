package com.cameraman.audit.controller;

import com.cameraman.audit.dto.AuditDto;
import com.cameraman.audit.service.AuditService;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuditLog (the entity), AuditLogRepository, and AuditService's write-side methods all
 * already existed and were already being called throughout the app to record actions —
 * but nothing ever exposed a way to read them back over HTTP. The frontend's Audit Logs
 * page (admin/super-admin only) has been calling GET /audit-logs since it was built, with
 * no controller anywhere to answer it — a guaranteed, unconditional fetch failure on that
 * one page, regardless of anything else in the app.
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Read-only history of actions performed in the system")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:AUDIT:VIEW')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AuditDto.LogResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                auditService.search(search, PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }
}
