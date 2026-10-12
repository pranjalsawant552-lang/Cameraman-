package com.cameraman.report.controller;

import com.cameraman.report.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Business reports and exports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/bookings/export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT') or hasAuthority('CUSTOM:REPORT:EXPORT')")
    public ResponseEntity<byte[]> exportBookings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] data = reportService.generateBookingsReport(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bookings-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/revenue/export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT') or hasAuthority('CUSTOM:REPORT:EXPORT')")
    public ResponseEntity<byte[]> exportRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] data = reportService.generateRevenueReport(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=revenue-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/inventory/export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','INVENTORY_MANAGER') or hasAuthority('CUSTOM:REPORT:EXPORT')")
    public ResponseEntity<byte[]> exportInventory() {
        byte[] data = reportService.generateInventoryReport();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventory-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    // The three exports below were entirely missing — the frontend's Reports page has
    // listed and called all six of these since it was built, but only three controller
    // methods ever existed. Every click on Invoices/Galleries/Clients fell through to
    // Spring's default static-resource handler (no controller matched the path) and
    // produced a NoResourceFoundException, not a report.

    @GetMapping("/invoices/export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT') or hasAuthority('CUSTOM:REPORT:EXPORT')")
    public ResponseEntity<byte[]> exportInvoices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] data = reportService.generateInvoicesReport(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoices-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/galleries/export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:REPORT:EXPORT')")
    public ResponseEntity<byte[]> exportGalleries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] data = reportService.generateGalleriesReport(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=galleries-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/clients/export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:REPORT:EXPORT')")
    public ResponseEntity<byte[]> exportClients() {
        // No date range — this is a full client-directory snapshot, not a period report;
        // the frontend still sends from/to on every export call regardless of report type,
        // which is harmless here since Spring ignores query params no @RequestParam binds.
        byte[] data = reportService.generateClientsReport();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=clients-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
