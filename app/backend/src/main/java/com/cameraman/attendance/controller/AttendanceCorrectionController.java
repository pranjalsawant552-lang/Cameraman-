package com.cameraman.attendance.controller;

import com.cameraman.attendance.domain.AttendanceCorrection;
import com.cameraman.attendance.service.AttendanceCorrectionService;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/attendance-corrections")
@RequiredArgsConstructor
public class AttendanceCorrectionController {

    private static final String STAFF = "hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR','ACCOUNTANT','INVENTORY_MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:SELF')";
    private static final String MANAGERS = "hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:UPDATE')";

    private final AttendanceCorrectionService service;

    public record CreateBody(LocalDateTime proposedTime, String direction, String reason) {}
    public record ReviewBody(String notes) {}

    @PostMapping
    @PreAuthorize(STAFF)
    public ResponseEntity<ApiResponse<AttendanceCorrection>> request(@RequestBody CreateBody body, @CurrentUser String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Request sent",
                service.request(userId, body.proposedTime(), body.direction(), body.reason())));
    }

    @GetMapping("/mine")
    @PreAuthorize(STAFF)
    public ResponseEntity<ApiResponse<List<AttendanceCorrection>>> mine(@CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success(service.mine(userId)));
    }

    @GetMapping
    @PreAuthorize(MANAGERS)
    public ResponseEntity<ApiResponse<List<AttendanceCorrection>>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(service.byStatus(status)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(MANAGERS)
    public ResponseEntity<ApiResponse<AttendanceCorrection>> approve(@PathVariable String id, @CurrentUser String userId,
            @RequestBody(required = false) ReviewBody body) {
        return ResponseEntity.ok(ApiResponse.success("Approved", service.approve(id, userId, body != null ? body.notes() : null)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(MANAGERS)
    public ResponseEntity<ApiResponse<AttendanceCorrection>> reject(@PathVariable String id, @CurrentUser String userId,
            @RequestBody(required = false) ReviewBody body) {
        return ResponseEntity.ok(ApiResponse.success("Rejected", service.reject(id, userId, body != null ? body.notes() : null)));
    }
}
