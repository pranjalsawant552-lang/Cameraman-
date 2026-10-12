package com.cameraman.package_mgmt.service;

import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.package_mgmt.domain.Package;
import com.cameraman.package_mgmt.domain.PackageCategory;
import com.cameraman.package_mgmt.domain.PackageItem;
import com.cameraman.package_mgmt.dto.PackageDto;
import com.cameraman.package_mgmt.repository.PackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PackageService {

    private final PackageRepository packageRepository;

    @Cacheable("packages")
    @Transactional(readOnly = true)
    public List<PackageDto.PackageResponse> getActivePackages() {
        return packageRepository.findByActiveTrueAndArchivedFalseOrderBySortOrderAsc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagedResponse<PackageDto.PackageResponse> getAllPackages(String search, String category, Pageable pageable) {
        var page = (search != null && !search.isBlank())
                ? packageRepository.searchPackages(search, pageable)
                : (category != null && !category.isBlank())
                ? packageRepository.findByCategory(PackageCategory.parse(category), pageable)
                : packageRepository.findByArchivedFalse(pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PackageDto.PackageResponse getById(String id) {
        return toResponse(findById(id));
    }

    @CacheEvict(value = "packages", allEntries = true)
    public PackageDto.PackageResponse createPackage(PackageDto.CreateRequest request) {
        Package pkg = Package.builder()
                .name(request.getName()).description(request.getDescription())
                .category(PackageCategory.parse(request.getCategory()))
                .basePrice(request.getBasePrice())
                .discountedPrice(request.getDiscountedPrice())
                .gstRate(request.getGstRate() != null ? request.getGstRate() : new java.math.BigDecimal("18.00"))
                .durationHours(request.getDurationHours())
                .photoCount(request.getPhotoCount())
                .editedCount(request.getEditedCount())
                .printCount(request.getPrintCount())
                .includesAlbum(request.isIncludesAlbum())
                .includesVideo(request.isIncludesVideo())
                .includesUsb(request.isIncludesUsb())
                .deliveryDays(request.getDeliveryDays())
                .sortOrder(request.getSortOrder())
                .thumbnailUrl(request.getThumbnailUrl())
                .build();

        if (request.getItems() != null) {
            request.getItems().forEach(i -> pkg.getItems().add(
                    PackageItem.builder().pkg(pkg).description(i.getDescription()).quantity(i.getQuantity()).build()));
        }
        return toResponse(packageRepository.save(pkg));
    }

    @CacheEvict(value = "packages", allEntries = true)
    public PackageDto.PackageResponse updatePackage(String id, PackageDto.UpdateRequest request) {
        Package pkg = findById(id);
        if (request.getName() != null) pkg.setName(request.getName());
        if (request.getDescription() != null) pkg.setDescription(request.getDescription());
        if (request.getBasePrice() != null) pkg.setBasePrice(request.getBasePrice());
        if (request.getDiscountedPrice() != null) pkg.setDiscountedPrice(request.getDiscountedPrice());
        if (request.getActive() != null) pkg.setActive(request.getActive());
        if (request.getSortOrder() != null) pkg.setSortOrder(request.getSortOrder());
        return toResponse(packageRepository.save(pkg));
    }

    @CacheEvict(value = "packages", allEntries = true)
    public PackageDto.PackageResponse clonePackage(String id) {
        Package src = findById(id);
        Package clone = Package.builder()
                .name("Copy of " + src.getName()).description(src.getDescription())
                .category(src.getCategory()).basePrice(src.getBasePrice())
                .discountedPrice(src.getDiscountedPrice()).gstRate(src.getGstRate())
                .durationHours(src.getDurationHours()).photoCount(src.getPhotoCount())
                .editedCount(src.getEditedCount()).printCount(src.getPrintCount())
                .includesAlbum(src.isIncludesAlbum()).includesVideo(src.isIncludesVideo())
                .includesUsb(src.isIncludesUsb()).deliveryDays(src.getDeliveryDays())
                .sortOrder(src.getSortOrder()).active(false)
                .build();
        src.getItems().forEach(i -> clone.getItems().add(
                PackageItem.builder().pkg(clone).description(i.getDescription()).quantity(i.getQuantity()).build()));
        return toResponse(packageRepository.save(clone));
    }

    @CacheEvict(value = "packages", allEntries = true)
    public PackageDto.PackageResponse archivePackage(String id) {
        Package pkg = findById(id);
        pkg.setArchived(true);
        pkg.setActive(false);
        return toResponse(packageRepository.save(pkg));
    }

    private Package findById(String id) {
        return packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package", "id", id));
    }

    private PackageDto.PackageResponse toResponse(Package p) {
        List<PackageDto.PackageItemResponse> items = p.getItems().stream()
                .map(i -> PackageDto.PackageItemResponse.builder()
                        .id(i.getId()).description(i.getDescription()).quantity(i.getQuantity()).build())
                .collect(Collectors.toList());

        return PackageDto.PackageResponse.builder()
                .id(p.getId()).name(p.getName()).description(p.getDescription())
                .category(p.getCategory().name()).basePrice(p.getBasePrice())
                .discountedPrice(p.getDiscountedPrice()).effectivePrice(p.getEffectivePrice())
                .priceWithGst(p.getPriceWithGst()).gstRate(p.getGstRate())
                .durationHours(p.getDurationHours()).photoCount(p.getPhotoCount())
                .editedCount(p.getEditedCount()).printCount(p.getPrintCount())
                .includesAlbum(p.isIncludesAlbum()).includesVideo(p.isIncludesVideo())
                .includesUsb(p.isIncludesUsb()).deliveryDays(p.getDeliveryDays())
                .active(p.isActive()).archived(p.isArchived()).sortOrder(p.getSortOrder())
                .thumbnailUrl(p.getThumbnailUrl()).items(items)
                .createdAt(p.getCreatedAt()).build();
    }
}
