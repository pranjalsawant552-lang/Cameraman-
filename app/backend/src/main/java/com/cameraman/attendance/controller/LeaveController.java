package com.cameraman.attendance.controller;

import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.service.LeaveService;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
@Tag(name = "Leave", description = "Staff leave requests — feeds the attendance monthly report's ON_LEAVE vs ABSENT distinction")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR','ACCOUNTANT','INVENTORY_MANAGER')")
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR','ACCOUNTANT','INVENTORY_MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:SELF')")
    public ResponseEntity<ApiResponse<AttendanceDto.LeaveResponse>> apply(@Valid @RequestBody AttendanceDto.LeaveApplyRequest request,
            @CurrentUser String userId, org.springframework.security.core.Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Leave request submitted", leaveService.apply(request, userId, isManager(auth))));
    }

    private boolean isManager(org.springframework.security.core.Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_SUPER_ADMIN") || a.getAuthority().equals("ROLE_MANAGER")
                || a.getAuthority().equals("CUSTOM:ATTENDANCE:UPDATE"));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR','ACCOUNTANT','INVENTORY_MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:SELF')")
    public ResponseEntity<ApiResponse<List<AttendanceDto.LeaveResponse>>> mine(@CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.listMine(userId)));
    }

    @GetMapping("/staff/{staffId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR','ACCOUNTANT','INVENTORY_MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:SELF')")
    public ResponseEntity<ApiResponse<List<AttendanceDto.LeaveResponse>>> forStaff(@PathVariable String staffId,
            @CurrentUser String userId, org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.listForStaff(staffId, userId, isManager(auth))));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:VIEW')")
    public ResponseEntity<ApiResponse<List<AttendanceDto.LeaveResponse>>> byStatus(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.listByStatus(status)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:UPDATE')")
    public ResponseEntity<ApiResponse<AttendanceDto.LeaveResponse>> approve(
            @PathVariable String id, @CurrentUser String userId,
            @RequestBody(required = false) AttendanceDto.LeaveReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Leave approved", leaveService.approve(id, userId, request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:UPDATE')")
    public ResponseEntity<ApiResponse<AttendanceDto.LeaveResponse>> reject(
            @PathVariable String id, @CurrentUser String userId,
            @RequestBody(required = false) AttendanceDto.LeaveReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Leave rejected", leaveService.reject(id, userId, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR','ACCOUNTANT','INVENTORY_MANAGER') or hasAuthority('CUSTOM:ATTENDANCE:SELF')")
    public ResponseEntity<ApiResponse<AttendanceDto.LeaveResponse>> cancel(@PathVariable String id,
            @CurrentUser String userId, org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Leave request cancelled", leaveService.cancel(id, userId, isManager(auth))));
    }
}
