package com.cameraman.settings.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.settings.dto.SettingsDto;
import com.cameraman.settings.service.StudioSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Studio settings and branding")
public class SettingsController {

    private final StudioSettingsService settingsService;

    @GetMapping("/studio")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SettingsDto.StudioSettingsResponse>> getStudioSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getStudioSettings()));
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPublicSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getPublicSettings()));
    }

    @GetMapping("/public-info")
    @Operation(summary = "Safe-subset studio info for the public marketing site (footer, contact, about)")
    public ResponseEntity<ApiResponse<SettingsDto.PublicSiteInfo>> getPublicSiteInfo() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getPublicSiteInfo()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:SETTINGS:UPDATE')")
    public ResponseEntity<ApiResponse<Void>> updateSettings(@RequestBody SettingsDto.UpdateSettingsRequest request) {
        settingsService.updateSettings(request.getSettings());
        return ResponseEntity.ok(ApiResponse.success("Settings updated successfully"));
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:SETTINGS:VIEW')")
    public ResponseEntity<ApiResponse<List<SettingsDto.SettingResponse>>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getByCategory(category)));
    }
}
