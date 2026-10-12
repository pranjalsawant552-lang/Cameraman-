package com.cameraman.studio.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.studio.dto.StudioSessionDto;
import com.cameraman.studio.service.StudioSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/studio-sessions")
@RequiredArgsConstructor
public class StudioSessionController {

    private final StudioSessionService studioSessionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:STUDIO:CREATE')")
    public ResponseEntity<ApiResponse<StudioSessionDto.StudioSessionResponse>> create(
            @Valid @RequestBody StudioSessionDto.CreateRequest request,
            @com.cameraman.common.security.CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Studio session created", studioSessionService.create(request, userId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:STUDIO:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<StudioSessionDto.StudioSessionResponse>>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String studioRoom,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(studioSessionService.search(status, studioRoom, search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StudioSessionDto.StudioSessionResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(studioSessionService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:STUDIO:UPDATE')")
    public ResponseEntity<ApiResponse<StudioSessionDto.StudioSessionResponse>> update(
            @PathVariable String id, @RequestBody StudioSessionDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Studio session updated", studioSessionService.update(id, request)));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:STUDIO:UPDATE')")
    public ResponseEntity<ApiResponse<StudioSessionDto.StudioSessionResponse>> reschedule(
            @PathVariable String id, @Valid @RequestBody StudioSessionDto.RescheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Session rescheduled", studioSessionService.reschedule(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:STUDIO:UPDATE')")
    public ResponseEntity<ApiResponse<StudioSessionDto.StudioSessionResponse>> cancel(
            @PathVariable String id, @RequestBody StudioSessionDto.CancelRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Session cancelled", studioSessionService.cancel(id, request)));
    }

    @GetMapping("/calendar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<StudioSessionDto.StudioSessionResponse>>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(studioSessionService.getByDateRange(from, to)));
    }

    @PostMapping("/availability")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check studio room/slot availability for a given date")
    public ResponseEntity<ApiResponse<List<StudioSessionDto.SlotAvailability>>> checkAvailability(
            @Valid @RequestBody StudioSessionDto.AvailabilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(studioSessionService.checkAvailability(request)));
    }
}
