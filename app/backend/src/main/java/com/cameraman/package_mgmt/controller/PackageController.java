package com.cameraman.package_mgmt.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.package_mgmt.dto.PackageDto;
import com.cameraman.package_mgmt.service.PackageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/packages")
@RequiredArgsConstructor
@Tag(name = "Packages", description = "Photography package management")
public class PackageController {

    private final PackageService packageService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<PackageDto.PackageResponse>>> getActivePackages() {
        return ResponseEntity.ok(ApiResponse.success(packageService.getActivePackages()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:PACKAGE:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<PackageDto.PackageResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(packageService.getAllPackages(search, category,
                PageRequest.of(page, size, Sort.by("sortOrder").ascending()))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageDto.PackageResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(packageService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:PACKAGE:CREATE')")
    public ResponseEntity<ApiResponse<PackageDto.PackageResponse>> create(
            @Valid @RequestBody PackageDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Package created", packageService.createPackage(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:PACKAGE:UPDATE')")
    public ResponseEntity<ApiResponse<PackageDto.PackageResponse>> update(
            @PathVariable String id, @RequestBody PackageDto.UpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Package updated", packageService.updatePackage(id, req)));
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:PACKAGE:UPDATE')")
    public ResponseEntity<ApiResponse<PackageDto.PackageResponse>> clone(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Package cloned", packageService.clonePackage(id)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:PACKAGE:UPDATE')")
    public ResponseEntity<ApiResponse<PackageDto.PackageResponse>> archive(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Package archived", packageService.archivePackage(id)));
    }
}
