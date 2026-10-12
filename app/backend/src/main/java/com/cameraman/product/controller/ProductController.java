package com.cameraman.product.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.product.dto.ProductDto;
import com.cameraman.product.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductDto.ProductResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(productService.getAll(search, PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto.ProductResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getById(id)));
    }

    @GetMapping("/barcode/{code}")
    public ResponseEntity<ApiResponse<ProductDto.ProductResponse>> getByBarcode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(productService.getBySku(code)));
    }

    @PostMapping("/barcode/{code}/scan")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:PRODUCT:UPDATE')")
    public ResponseEntity<ApiResponse<ProductDto.ProductResponse>> scan(
            @PathVariable String code, @RequestBody(required = false) ProductDto.ScanRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Stock updated",
                productService.scanAdjust(code, req != null ? req : new ProductDto.ScanRequest())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:PRODUCT:CREATE')")
    public ResponseEntity<ApiResponse<ProductDto.ProductResponse>> create(@Valid @RequestBody ProductDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Product created", productService.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:PRODUCT:UPDATE')")
    public ResponseEntity<ApiResponse<ProductDto.ProductResponse>> update(
            @PathVariable String id, @jakarta.validation.Valid @RequestBody ProductDto.UpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Product updated", productService.update(id, req)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<ProductDto.CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(productService.getAllCategories()));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:PRODUCT:CREATE')")
    public ResponseEntity<ApiResponse<ProductDto.CategoryResponse>> createCategory(
            @Valid @RequestBody ProductDto.CategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created", productService.createCategory(req)));
    }
}
