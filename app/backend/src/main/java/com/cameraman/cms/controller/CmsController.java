package com.cameraman.cms.controller;

import com.cameraman.cms.dto.CmsDto;
import com.cameraman.cms.service.CmsService;
import com.cameraman.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/cms")
@RequiredArgsConstructor
@Tag(name = "CMS", description = "Website content management")
public class CmsController {

    private final CmsService cmsService;

    // ── PUBLIC ENDPOINTS ────────────────────────────────────

    @GetMapping("/public/slides")
    public ResponseEntity<ApiResponse<List<CmsDto.SlideResponse>>> getActiveSlides() {
        return ResponseEntity.ok(ApiResponse.success(cmsService.getActiveSlides()));
    }

    @GetMapping("/public/testimonials")
    public ResponseEntity<ApiResponse<List<CmsDto.TestimonialResponse>>> getActiveTestimonials() {
        return ResponseEntity.ok(ApiResponse.success(cmsService.getActiveTestimonials()));
    }

    @GetMapping("/public/pages/{key}")
    public ResponseEntity<ApiResponse<CmsDto.PageResponse>> getPage(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.success(cmsService.getPageByKey(key)));
    }

    // ── ADMIN ENDPOINTS ─────────────────────────────────────

    @PostMapping(value = "/media/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:CMS:CREATE')")
    @Operation(summary = "Upload an image for use as a hero slide, logo, or watermark")
    public ResponseEntity<ApiResponse<com.cameraman.storage.dto.StorageDto.UploadResponse>> uploadMedia(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(cmsService.uploadMedia(file)));
    }

    @GetMapping("/slides")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:CMS:VIEW')")
    public ResponseEntity<ApiResponse<List<CmsDto.SlideResponse>>> getAllSlides() {
        return ResponseEntity.ok(ApiResponse.success(cmsService.getAllSlides()));
    }

    @PostMapping("/slides")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:CMS:CREATE')")
    public ResponseEntity<ApiResponse<CmsDto.SlideResponse>> createSlide(@RequestBody CmsDto.SlideRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Slide created", cmsService.createSlide(req)));
    }

    @PutMapping("/slides/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:CMS:UPDATE')")
    public ResponseEntity<ApiResponse<CmsDto.SlideResponse>> updateSlide(
            @PathVariable String id, @RequestBody CmsDto.SlideRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Slide updated", cmsService.updateSlide(id, req)));
    }

    @DeleteMapping("/slides/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:CMS:DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteSlide(@PathVariable String id) {
        cmsService.deleteSlide(id);
        return ResponseEntity.ok(ApiResponse.success("Slide deleted"));
    }

    @PostMapping("/testimonials")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:CMS:CREATE')")
    public ResponseEntity<ApiResponse<CmsDto.TestimonialResponse>> createTestimonial(
            @RequestBody CmsDto.TestimonialRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Testimonial created", cmsService.createTestimonial(req)));
    }

    @PutMapping("/testimonials/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:CMS:UPDATE')")
    public ResponseEntity<ApiResponse<CmsDto.TestimonialResponse>> updateTestimonial(
            @PathVariable String id, @RequestBody CmsDto.TestimonialRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Testimonial updated", cmsService.updateTestimonial(id, req)));
    }

    @GetMapping("/pages")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:CMS:VIEW')")
    public ResponseEntity<ApiResponse<List<CmsDto.PageResponse>>> getAllPages() {
        return ResponseEntity.ok(ApiResponse.success(cmsService.getAllPages()));
    }

    @PostMapping("/pages")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:CMS:CREATE')")
    public ResponseEntity<ApiResponse<CmsDto.PageResponse>> createPage(@RequestBody CmsDto.PageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Page created", cmsService.createPage(req)));
    }

    @PutMapping("/pages/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:CMS:UPDATE')")
    public ResponseEntity<ApiResponse<CmsDto.PageResponse>> updatePage(
            @PathVariable String id, @RequestBody CmsDto.PageRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Page updated", cmsService.updatePageById(id, req)));
    }

    @DeleteMapping("/pages/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('CUSTOM:CMS:DELETE')")
    public ResponseEntity<ApiResponse<Void>> deletePage(@PathVariable String id) {
        cmsService.deletePageById(id);
        return ResponseEntity.ok(ApiResponse.success("Page deleted"));
    }
}
