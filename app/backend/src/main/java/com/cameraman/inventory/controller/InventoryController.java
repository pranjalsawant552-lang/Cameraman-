package com.cameraman.inventory.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.inventory.dto.InventoryDto;
import com.cameraman.inventory.service.InventoryService;
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
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','INVENTORY_MANAGER') or hasAuthority('CUSTOM:INVENTORY:CREATE')")
    public ResponseEntity<ApiResponse<InventoryDto.ItemResponse>> create(
            @Valid @RequestBody InventoryDto.CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Item created", inventoryService.createItem(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','INVENTORY_MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:INVENTORY:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryDto.ItemResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getAllItems(search, category,
                        PageRequest.of(page, size, Sort.by(sortBy).ascending()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','INVENTORY_MANAGER') or hasAuthority('CUSTOM:INVENTORY:VIEW')")
    public ResponseEntity<ApiResponse<InventoryDto.ItemResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getById(id)));
    }

    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InventoryDto.ItemResponse>> getByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getByBarcode(barcode)));
    }

    @PostMapping("/barcode/{barcode}/scan")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','INVENTORY_MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:INVENTORY:ADJUST')")
    public ResponseEntity<ApiResponse<InventoryDto.ItemResponse>> scan(
            @PathVariable String barcode, @Valid @RequestBody(required = false) InventoryDto.ScanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stock updated",
                inventoryService.scanAdjust(barcode, request != null ? request : new InventoryDto.ScanRequest())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','INVENTORY_MANAGER') or hasAuthority('CUSTOM:INVENTORY:UPDATE')")
    public ResponseEntity<ApiResponse<InventoryDto.ItemResponse>> update(
            @PathVariable String id, @Valid @RequestBody InventoryDto.UpdateItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Item updated", inventoryService.updateItem(id, request)));
    }

    @PostMapping("/{id}/adjust-stock")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','INVENTORY_MANAGER') or hasAuthority('CUSTOM:INVENTORY:ADJUST')")
    public ResponseEntity<ApiResponse<InventoryDto.ItemResponse>> adjustStock(
            @PathVariable String id, @Valid @RequestBody InventoryDto.StockAdjustmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted", inventoryService.adjustStock(id, request)));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','INVENTORY_MANAGER') or hasAuthority('CUSTOM:INVENTORY:VIEW')")
    public ResponseEntity<ApiResponse<List<InventoryDto.ItemResponse>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getLowStockItems()));
    }
}
