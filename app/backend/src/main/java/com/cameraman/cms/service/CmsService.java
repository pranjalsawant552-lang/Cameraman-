package com.cameraman.cms.service;

import com.cameraman.cms.domain.*;
import com.cameraman.cms.dto.CmsDto;
import com.cameraman.cms.repository.*;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.storage.dto.StorageDto;
import com.cameraman.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CmsService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024; // 10MB

    private final CmsSlideRepository slideRepository;
    private final CmsTestimonialRepository testimonialRepository;
    private final CmsPageRepository pageRepository;
    private final StorageService storageService;

    /** Backs the admin CMS media-upload control (hero slide images, logo,
     *  watermark) so "paste an image URL" isn't the only option — staff can
     *  upload a file directly and get back a real, hosted URL to use in any
     *  of the other CMS/settings fields that take an image URL. */
    public StorageDto.UploadResponse uploadMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("No file provided", "FILE_REQUIRED");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("File exceeds the 10MB limit", "FILE_TOO_LARGE");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException("Only JPEG, PNG, WebP or GIF images are allowed", "INVALID_FILE_TYPE");
        }
        try {
            return storageService.uploadFile(file.getInputStream(), "cms",
                    file.getOriginalFilename(), contentType, file.getSize());
        } catch (IOException e) {
            throw new BusinessException("Could not read the uploaded file: " + e.getMessage(), "UPLOAD_FAILED");
        }
    }

    // ── SLIDES ──────────────────────────────────────────────

    @Cacheable("cmsSlides")
    @Transactional(readOnly = true)
    public List<CmsDto.SlideResponse> getActiveSlides() {
        return slideRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream().map(this::toSlideResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CmsDto.SlideResponse> getAllSlides() {
        return slideRepository.findAll().stream().map(this::toSlideResponse).collect(Collectors.toList());
    }

    @CacheEvict(value = "cmsSlides", allEntries = true)
    public CmsDto.SlideResponse createSlide(CmsDto.SlideRequest request) {
        CmsSlide slide = CmsSlide.builder()
                .title(request.getTitle()).subtitle(request.getSubtitle())
                .imageUrl(request.getImageUrl()).videoUrl(request.getVideoUrl())
                .ctaText(request.getCtaText()).ctaUrl(request.getCtaUrl())
                .slideType(request.getSlideType() != null ? request.getSlideType() : "IMAGE")
                .sortOrder(request.getSortOrder()).active(request.isActive())
                .build();
        return toSlideResponse(slideRepository.save(slide));
    }

    @CacheEvict(value = "cmsSlides", allEntries = true)
    public CmsDto.SlideResponse updateSlide(String id, CmsDto.SlideRequest request) {
        CmsSlide slide = slideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Slide", "id", id));
        if (request.getTitle() != null) slide.setTitle(request.getTitle());
        if (request.getSubtitle() != null) slide.setSubtitle(request.getSubtitle());
        if (request.getImageUrl() != null) slide.setImageUrl(request.getImageUrl());
        if (request.getVideoUrl() != null) slide.setVideoUrl(request.getVideoUrl());
        if (request.getCtaText() != null) slide.setCtaText(request.getCtaText());
        if (request.getCtaUrl() != null) slide.setCtaUrl(request.getCtaUrl());
        slide.setSortOrder(request.getSortOrder());
        slide.setActive(request.isActive());
        return toSlideResponse(slideRepository.save(slide));
    }

    @CacheEvict(value = "cmsSlides", allEntries = true)
    public void deleteSlide(String id) {
        slideRepository.deleteById(id);
    }

    // ── TESTIMONIALS ─────────────────────────────────────────

    @Cacheable("cmsTestimonials")
    @Transactional(readOnly = true)
    public List<CmsDto.TestimonialResponse> getActiveTestimonials() {
        return testimonialRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream().map(this::toTestimonialResponse).collect(Collectors.toList());
    }

    @CacheEvict(value = "cmsTestimonials", allEntries = true)
    public CmsDto.TestimonialResponse createTestimonial(CmsDto.TestimonialRequest request) {
        CmsTestimonial t = CmsTestimonial.builder()
                .clientName(request.getClientName()).clientPhotoUrl(request.getClientPhotoUrl())
                .review(request.getReview()).rating(request.getRating())
                .packageType(request.getPackageType()).sortOrder(request.getSortOrder())
                .active(request.isActive()).build();
        return toTestimonialResponse(testimonialRepository.save(t));
    }

    @CacheEvict(value = "cmsTestimonials", allEntries = true)
    public CmsDto.TestimonialResponse updateTestimonial(String id, CmsDto.TestimonialRequest request) {
        CmsTestimonial t = testimonialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Testimonial", "id", id));
        if (request.getClientName() != null) t.setClientName(request.getClientName());
        if (request.getReview() != null) t.setReview(request.getReview());
        if (request.getRating() > 0) t.setRating(request.getRating());
        if (request.getPackageType() != null) t.setPackageType(request.getPackageType());
        t.setSortOrder(request.getSortOrder());
        t.setActive(request.isActive());
        return toTestimonialResponse(testimonialRepository.save(t));
    }

    // ── PAGES ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CmsDto.PageResponse getPageByKey(String key) {
        CmsPage page = pageRepository.findByPageKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Page", "key", key));
        return toPageResponse(page);
    }

    public CmsDto.PageResponse savePage(String key, CmsDto.PageRequest request) {
        CmsPage page = pageRepository.findByPageKey(key)
                .orElse(CmsPage.builder().pageKey(key).build());
        page.setTitle(request.getTitle());
        page.setContent(request.getContent());
        page.setPublished(request.isPublished());
        page.setMetaTitle(request.getMetaTitle());
        page.setMetaDescription(request.getMetaDescription());
        return toPageResponse(pageRepository.save(page));
    }

    // ── ADMIN PAGES LIST (id-based, used by the CMS Pages screen) ──────

    @Transactional(readOnly = true)
    public List<CmsDto.PageResponse> getAllPages() {
        return pageRepository.findAll().stream()
                .map(this::toPageResponse)
                .collect(Collectors.toList());
    }

    public CmsDto.PageResponse createPage(CmsDto.PageRequest request) {
        String key = request.getSlug() != null && !request.getSlug().isBlank()
                ? request.getSlug() : slugify(request.getTitle());
        CmsPage page = CmsPage.builder()
                .pageKey(key)
                .title(request.getTitle())
                .content(request.getContent())
                .published(request.isActive())
                .metaTitle(request.getMetaTitle())
                .metaDescription(request.getMetaDescription())
                .build();
        return toPageResponse(pageRepository.save(page));
    }

    public CmsDto.PageResponse updatePageById(String id, CmsDto.PageRequest request) {
        CmsPage page = pageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Page", "id", id));
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            page.setPageKey(request.getSlug());
        }
        page.setTitle(request.getTitle());
        page.setContent(request.getContent());
        page.setPublished(request.isActive());
        if (request.getMetaDescription() != null) {
            page.setMetaDescription(request.getMetaDescription());
        }
        if (request.getMetaTitle() != null) {
            page.setMetaTitle(request.getMetaTitle());
        }
        return toPageResponse(pageRepository.save(page));
    }

    public void deletePageById(String id) {
        if (!pageRepository.existsById(id)) {
            throw new ResourceNotFoundException("Page", "id", id);
        }
        pageRepository.deleteById(id);
    }

    private String slugify(String title) {
        if (title == null) return "page-" + System.currentTimeMillis();
        return title.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    // ── MAPPERS ─────────────────────────────────────────────

    private CmsDto.SlideResponse toSlideResponse(CmsSlide s) {
        return CmsDto.SlideResponse.builder()
                .id(s.getId()).title(s.getTitle()).subtitle(s.getSubtitle())
                .imageUrl(s.getImageUrl()).videoUrl(s.getVideoUrl())
                .ctaText(s.getCtaText()).ctaUrl(s.getCtaUrl())
                .slideType(s.getSlideType()).sortOrder(s.getSortOrder()).active(s.isActive())
                .build();
    }

    private CmsDto.TestimonialResponse toTestimonialResponse(CmsTestimonial t) {
        return CmsDto.TestimonialResponse.builder()
                .id(t.getId()).clientName(t.getClientName()).clientPhotoUrl(t.getClientPhotoUrl())
                .review(t.getReview()).rating(t.getRating()).packageType(t.getPackageType())
                .sortOrder(t.getSortOrder()).active(t.isActive()).build();
    }

    private CmsDto.PageResponse toPageResponse(CmsPage p) {
        return CmsDto.PageResponse.builder()
                .id(p.getId()).pageKey(p.getPageKey()).title(p.getTitle())
                .content(p.getContent()).published(p.isPublished())
                .metaTitle(p.getMetaTitle()).metaDescription(p.getMetaDescription())
                .updatedAt(p.getUpdatedAt()).createdAt(p.getCreatedAt())
                .slug(p.getPageKey()).active(p.isPublished())
                .build();
    }
}
