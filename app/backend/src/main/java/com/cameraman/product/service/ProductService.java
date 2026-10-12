package com.cameraman.product.service;

import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.product.domain.Product;
import com.cameraman.product.domain.ProductCategory;
import com.cameraman.product.dto.ProductDto;
import com.cameraman.product.repository.ProductCategoryRepository;
import com.cameraman.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    public ProductDto.ProductResponse create(ProductDto.CreateRequest request) {
        ProductCategory category = null;
        if (request.getCategoryId() != null && !request.getCategoryId().isBlank()) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
        }
        Product product = Product.builder()
                .category(category).name(request.getName()).description(request.getDescription())
                .sku(request.getSku() != null && !request.getSku().isBlank() ? request.getSku().trim() : generateSku())
                .price(request.getPrice())
                .gstRate(request.getGstRate() != null ? request.getGstRate() : new java.math.BigDecimal("18.00"))
                .stockQuantity(request.getStockQuantity()).imageUrl(request.getImageUrl())
                .build();
        return toResponse(productRepository.save(product));
    }

    public ProductDto.ProductResponse update(String id, ProductDto.UpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        if (request.getName() != null) {
            if (request.getName().isBlank()) throw new com.cameraman.common.exception.BusinessException("A product needs a name", "INVALID_PRODUCT");
            product.setName(request.getName().trim());
        }
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getGstRate() != null) product.setGstRate(request.getGstRate());
        if (request.getStockQuantity() != null) product.setStockQuantity(request.getStockQuantity());
        if (request.getActive() != null) product.setActive(request.getActive());
        if (request.getImageUrl() != null) product.setImageUrl(request.getImageUrl());
        if (request.getCategoryId() != null) {
            // Blank means "no category"; an unknown id is an error rather than a silent no-op.
            if (request.getCategoryId().isBlank()) {
                product.setCategory(null);
            } else {
                product.setCategory(categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId())));
            }
        }
        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductDto.ProductResponse getById(String id) {
        return toResponse(productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id)));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductDto.ProductResponse> getAll(String search, Pageable pageable) {
        // This is the staff management view (there's no separate public product catalog),
        // so inactive products must stay visible here — otherwise deactivating one makes
        // it permanently unreachable (can't find it to review or reactivate).
        var page = (search != null && !search.isBlank())
                ? productRepository.searchProducts(search, pageable)
                : productRepository.findAll(pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    public ProductDto.CategoryResponse createCategory(ProductDto.CategoryRequest request) {
        ProductCategory category = ProductCategory.builder()
                .name(request.getName()).description(request.getDescription()).build();
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<ProductDto.CategoryResponse> getAllCategories() {
        return categoryRepository.findByActiveTrue().stream().map(this::toCategoryResponse).collect(Collectors.toList());
    }

    /** Products double as barcode labels: the SKU is what gets printed and scanned. */
    private String generateSku() {
        String sku;
        do {
            sku = "PRD-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        } while (productRepository.existsBySku(sku));
        return sku;
    }

    @Transactional(readOnly = true)
    public ProductDto.ProductResponse getBySku(String code) {
        return toResponse(productRepository.findBySku(code == null ? "" : code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "barcode", code)));
    }

    /** Scan in/out: changes stock by +quantity or -quantity, never below zero. */
    public ProductDto.ProductResponse scanAdjust(String code, ProductDto.ScanRequest request) {
        Product p = productRepository.findBySku(code == null ? "" : code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "barcode", code));
        int qty = request.getQuantity() != null && request.getQuantity() > 0 ? request.getQuantity() : 1;
        boolean in = request.getMode() != null && "IN".equalsIgnoreCase(request.getMode());
        int next = p.getStockQuantity() + (in ? qty : -qty);
        if (next < 0) {
            throw new com.cameraman.common.exception.BusinessException(
                    "Insufficient stock. Available: " + p.getStockQuantity(), "INSUFFICIENT_STOCK");
        }
        p.setStockQuantity(next);
        return toResponse(productRepository.save(p));
    }

    private ProductDto.ProductResponse toResponse(Product p) {
        return ProductDto.ProductResponse.builder()
                .id(p.getId()).categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .name(p.getName()).description(p.getDescription()).sku(p.getSku())
                .price(p.getPrice()).gstRate(p.getGstRate()).stockQuantity(p.getStockQuantity())
                .active(p.isActive()).imageUrl(p.getImageUrl()).build();
    }

    private ProductDto.CategoryResponse toCategoryResponse(ProductCategory c) {
        return ProductDto.CategoryResponse.builder()
                .id(c.getId()).name(c.getName()).description(c.getDescription()).active(c.isActive()).build();
    }
}
