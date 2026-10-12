package com.cameraman.dashboard.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.dashboard.dto.DashboardDto;
import com.cameraman.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard metrics and analytics")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER','ACCOUNTANT','PHOTOGRAPHER','EDITOR','RECEPTIONIST','INVENTORY_MANAGER') or hasAuthority('CUSTOM:DASHBOARD:VIEW')")
    public ResponseEntity<ApiResponse<DashboardDto.Stats>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStats()));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT') or hasAuthority('CUSTOM:DASHBOARD:VIEW')")
    public ResponseEntity<ApiResponse<DashboardDto.OverviewMetrics>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getOverviewMetrics()));
    }

    @GetMapping("/revenue-trend")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT') or hasAuthority('CUSTOM:DASHBOARD:VIEW')")
    public ResponseEntity<ApiResponse<List<DashboardDto.MonthlyRevenue>>> getRevenueTrend(
            @RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getMonthlyRevenueTrend(months)));
    }

    @GetMapping("/calendar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DashboardDto.BookingCalendarSummary>>> getCalendarSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getCalendarSummary(from, to)));
    }

    @GetMapping("/staff-productivity")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:DASHBOARD:VIEW')")
    public ResponseEntity<ApiResponse<DashboardDto.StaffProductivity>> getStaffProductivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStaffProductivity(from, to)));
    }

    @GetMapping("/package-popularity")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:DASHBOARD:VIEW')")
    public ResponseEntity<ApiResponse<DashboardDto.PackagePopularity>> getPackagePopularity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getPackagePopularity(from, to)));
    }
}
