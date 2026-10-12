package com.cameraman.recommendation.service;

import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.package_mgmt.domain.Package;
import com.cameraman.package_mgmt.domain.PackageCategory;
import com.cameraman.package_mgmt.repository.PackageRepository;
import com.cameraman.product.domain.Product;
import com.cameraman.product.repository.ProductRepository;
import com.cameraman.recommendation.domain.CategoryRecommendation;
import com.cameraman.recommendation.dto.RecommendationDto;
import com.cameraman.recommendation.repository.CategoryRecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Upsell/cross-sell suggestions surfaced to staff at booking/consult time
 * (and available to the client portal) so a wedding booking gets shown the
 * premium album add-on, a birthday booking gets shown frames, etc. Two
 * distinct sources feed into one response:
 *
 *  1. Product add-ons — from the admin-curated {@link CategoryRecommendation}
 *     mapping (see that class for why this isn't "smart"/ML-derived).
 *  2. Package upgrades — computed for real from actual package data: other
 *     active, non-archived packages in the same category priced higher than
 *     what this booking already has, cheapest upgrade first.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final CategoryRecommendationRepository recommendationRepository;
    private final ProductRepository productRepository;
    private final PackageRepository packageRepository;
    private final BookingRepository bookingRepository;

    public RecommendationDto.BookingRecommendations getForBooking(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        return build(booking.getShootCategory(), booking.getPackageId());
    }

    public RecommendationDto.BookingRecommendations getForCategory(PackageCategory category) {
        return build(category, null);
    }

    private RecommendationDto.BookingRecommendations build(PackageCategory category, String currentPackageId) {
        List<RecommendationDto.ProductSuggestion> productAddOns = category == null
                ? List.of()
                : recommendationRepository.findByCategoryAndActiveTrueOrderByPriorityAsc(category).stream()
                    .map(CategoryRecommendation::getProductId)
                    .map(productRepository::findById)
                    .flatMap(java.util.Optional::stream)
                    .filter(Product::isActive)
                    .map(p -> RecommendationDto.ProductSuggestion.builder()
                            .productId(p.getId()).productName(p.getName()).price(p.getPrice())
                            .imageUrl(p.getImageUrl()).stockQuantity(p.getStockQuantity())
                            .build())
                    .collect(Collectors.toList());

        List<RecommendationDto.PackageUpgradeSuggestion> packageUpgrades = List.of();
        if (category != null) {
            BigDecimal currentPrice = currentPackageId != null
                    ? packageRepository.findById(currentPackageId).map(Package::getEffectivePrice).orElse(BigDecimal.ZERO)
                    : BigDecimal.ZERO;

            packageUpgrades = packageRepository.findByCategoryAndActiveTrueAndArchivedFalse(category).stream()
                    .filter(p -> !p.getId().equals(currentPackageId))
                    .filter(p -> p.getEffectivePrice().compareTo(currentPrice) > 0)
                    .sorted(Comparator.comparing(Package::getEffectivePrice))
                    .map(p -> RecommendationDto.PackageUpgradeSuggestion.builder()
                            .packageId(p.getId()).packageName(p.getName())
                            .effectivePrice(p.getEffectivePrice())
                            .priceDifference(p.getEffectivePrice().subtract(currentPrice))
                            .build())
                    .collect(Collectors.toList());
        }

        return RecommendationDto.BookingRecommendations.builder()
                .category(category != null ? category.name() : null)
                .productAddOns(productAddOns)
                .packageUpgrades(packageUpgrades)
                .build();
    }

    // ---- Admin configuration of the product add-on mapping ----

    public PagedResponse<RecommendationDto.RuleResponse> listRules(PackageCategory category, Pageable pageable) {
        Page<CategoryRecommendation> page = category != null
                ? recommendationRepository.findByCategory(category, pageable)
                : recommendationRepository.findAll(pageable);
        return PagedResponse.of(page.map(this::toRuleResponse));
    }

    @Transactional
    public RecommendationDto.RuleResponse createRule(RecommendationDto.RuleRequest request) {
        PackageCategory category = PackageCategory.valueOf(request.getCategory());
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));
        if (recommendationRepository.existsByCategoryAndProductId(category, product.getId())) {
            throw new BusinessException("This product is already recommended for this category", "DUPLICATE_RECOMMENDATION");
        }
        CategoryRecommendation rule = CategoryRecommendation.builder()
                .category(category)
                .productId(product.getId())
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .active(request.getActive() == null || request.getActive())
                .build();
        return toRuleResponse(recommendationRepository.save(rule));
    }

    @Transactional
    public RecommendationDto.RuleResponse updateRule(String id, RecommendationDto.RuleRequest request) {
        CategoryRecommendation rule = recommendationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation rule", "id", id));
        if (request.getCategory() != null) rule.setCategory(PackageCategory.valueOf(request.getCategory()));
        if (request.getProductId() != null) {
            productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));
            rule.setProductId(request.getProductId());
        }
        if (request.getPriority() != null) rule.setPriority(request.getPriority());
        if (request.getActive() != null) rule.setActive(request.getActive());
        return toRuleResponse(recommendationRepository.save(rule));
    }

    @Transactional
    public void deleteRule(String id) {
        if (!recommendationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Recommendation rule", "id", id);
        }
        recommendationRepository.deleteById(id);
    }

    private RecommendationDto.RuleResponse toRuleResponse(CategoryRecommendation rule) {
        Product product = productRepository.findById(rule.getProductId()).orElse(null);
        return RecommendationDto.RuleResponse.builder()
                .id(rule.getId())
                .category(rule.getCategory().name())
                .productId(rule.getProductId())
                .productName(product != null ? product.getName() : null)
                .productPrice(product != null ? product.getPrice() : null)
                .priority(rule.getPriority())
                .active(rule.isActive())
                .build();
    }
}
