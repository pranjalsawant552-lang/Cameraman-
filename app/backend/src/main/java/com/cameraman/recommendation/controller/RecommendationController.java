package com.cameraman.recommendation.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.package_mgmt.domain.PackageCategory;
import com.cameraman.recommendation.dto.RecommendationDto;
import com.cameraman.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "Upsell/cross-sell suggestions: product add-ons and package upgrades")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER') or hasAuthority('CUSTOM:ENGAGEMENT:VIEW')")
    public ResponseEntity<ApiResponse<RecommendationDto.BookingRecommendations>> forBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(ApiResponse.success(recommendationService.getForBooking(bookingId)));
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RecommendationDto.BookingRecommendations>> forCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.success(recommendationService.getForCategory(PackageCategory.valueOf(category))));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<RecommendationDto.RuleResponse>>> listRules(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PackageCategory categoryFilter = (category != null && !category.isBlank()) ? PackageCategory.valueOf(category) : null;
        return ResponseEntity.ok(ApiResponse.success(recommendationService.listRules(
                categoryFilter, PageRequest.of(page, size, Sort.by("priority").ascending()))));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:CREATE')")
    public ResponseEntity<ApiResponse<RecommendationDto.RuleResponse>> createRule(
            @Valid @RequestBody RecommendationDto.RuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Recommendation rule created", recommendationService.createRule(request)));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:UPDATE')")
    public ResponseEntity<ApiResponse<RecommendationDto.RuleResponse>> updateRule(
            @PathVariable String id, @Valid @RequestBody RecommendationDto.RuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Recommendation rule updated", recommendationService.updateRule(id, request)));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable String id) {
        recommendationService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success("Recommendation rule deleted", null));
    }
}
