package com.cameraman.outdoor.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.outdoor.dto.OutdoorShootDto;
import com.cameraman.outdoor.service.OutdoorShootService;
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
@RequestMapping("/outdoor-shoots")
@RequiredArgsConstructor
public class OutdoorShootController {

    private final OutdoorShootService outdoorShootService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:OUTDOOR:CREATE')")
    public ResponseEntity<ApiResponse<OutdoorShootDto.OutdoorShootResponse>> create(
            @Valid @RequestBody OutdoorShootDto.CreateRequest request,
            @com.cameraman.common.security.CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Outdoor shoot created", outdoorShootService.create(request, userId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:OUTDOOR:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<OutdoorShootDto.OutdoorShootResponse>>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(outdoorShootService.search(status, category, search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OutdoorShootDto.OutdoorShootResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(outdoorShootService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:OUTDOOR:UPDATE')")
    public ResponseEntity<ApiResponse<OutdoorShootDto.OutdoorShootResponse>> update(
            @PathVariable String id, @RequestBody OutdoorShootDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Outdoor shoot updated", outdoorShootService.update(id, request)));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:OUTDOOR:UPDATE')")
    public ResponseEntity<ApiResponse<OutdoorShootDto.OutdoorShootResponse>> reschedule(
            @PathVariable String id, @Valid @RequestBody OutdoorShootDto.RescheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Shoot rescheduled", outdoorShootService.reschedule(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:OUTDOOR:UPDATE')")
    public ResponseEntity<ApiResponse<OutdoorShootDto.OutdoorShootResponse>> cancel(
            @PathVariable String id, @RequestBody OutdoorShootDto.CancelRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Shoot cancelled", outdoorShootService.cancel(id, request)));
    }

    @GetMapping("/calendar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OutdoorShootDto.OutdoorShootResponse>>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(outdoorShootService.getByDateRange(from, to)));
    }
}
