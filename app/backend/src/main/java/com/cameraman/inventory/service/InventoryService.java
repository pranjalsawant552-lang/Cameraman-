package com.cameraman.inventory.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.DuplicateResourceException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.inventory.domain.*;
import com.cameraman.inventory.dto.InventoryDto;
import com.cameraman.inventory.repository.InventoryRepository;
import com.cameraman.inventory.repository.StockTransactionRepository;
import com.cameraman.storage.service.StorageService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository transactionRepository;
    private final StorageService storageService;
    private final com.cameraman.notification.service.NotificationPublisher notificationPublisher;

    private static final List<String> STAFF_NOTIFY_ROLES = List.of("ADMIN", "SUPER_ADMIN", "MANAGER");

    public InventoryDto.ItemResponse createItem(InventoryDto.CreateItemRequest request) {
        if (inventoryRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Inventory item", "SKU", request.getSku());
        }
        if (request.getBarcode() != null && inventoryRepository.existsByBarcode(request.getBarcode())) {
            throw new DuplicateResourceException("Inventory item", "barcode", request.getBarcode());
        }

        InventoryItem item = InventoryItem.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sku(request.getSku())
                // No barcode typed in? Use the SKU: it is unique, and Code 128 labels can encode it,
                // so every item is printable and scannable from day one.
                .barcode(request.getBarcode() != null && !request.getBarcode().isBlank() ? request.getBarcode().trim() : request.getSku())
                .category(InventoryCategory.parse(request.getCategory()))
                .unit(request.getUnit() != null ? request.getUnit() : "PCS")
                .quantityOnHand(request.getInitialQuantity())
                .reorderLevel(request.getReorderLevel())
                .reorderQuantity(request.getReorderQuantity())
                .costPrice(request.getCostPrice())
                .sellingPrice(request.getSellingPrice())
                .location(request.getLocation())
                .notes(request.getNotes())
                .build();

        item = inventoryRepository.save(item);

        // Generate QR code
        try {
            String qrUrl = generateAndUploadQrCode(item.getId(), item.getSku());
            item.setQrCodeUrl(qrUrl);
            item = inventoryRepository.save(item);
        } catch (Exception e) {
            log.warn("QR code generation failed for item {}: {}", item.getSku(), e.getMessage());
        }

        // Record initial stock transaction
        if (request.getInitialQuantity() > 0) {
            recordTransaction(item, TransactionType.ADJUSTMENT_IN, request.getInitialQuantity(),
                    0, request.getInitialQuantity(), request.getCostPrice(), null, null, "Initial stock", null);
        }

        return toResponse(item);
    }

    public InventoryDto.ItemResponse updateItem(String id, InventoryDto.UpdateItemRequest request) {
        InventoryItem item = findById(id);

        if (request.getName() != null) item.setName(request.getName());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getBarcode() != null) item.setBarcode(request.getBarcode());
        if (request.getCategory() != null) item.setCategory(InventoryCategory.parse(request.getCategory()));
        if (request.getUnit() != null) item.setUnit(request.getUnit());
        if (request.getReorderLevel() != null) item.setReorderLevel(request.getReorderLevel());
        if (request.getReorderQuantity() != null) item.setReorderQuantity(request.getReorderQuantity());
        if (request.getCostPrice() != null) item.setCostPrice(request.getCostPrice());
        if (request.getSellingPrice() != null) item.setSellingPrice(request.getSellingPrice());
        if (request.getLocation() != null) item.setLocation(request.getLocation());
        if (request.getNotes() != null) item.setNotes(request.getNotes());
        if (request.getActive() != null) item.setActive(request.getActive());

        return toResponse(inventoryRepository.save(item));
    }

    public InventoryDto.ItemResponse adjustStock(String id, InventoryDto.StockAdjustmentRequest request) {
        InventoryItem item = findById(id);
        TransactionType txnType = TransactionType.parse(request.getTransactionType());
        if (txnType == null) {
            throw new BusinessException("Unknown stock movement type: " + request.getTransactionType(), "INVALID_TXN_TYPE");
        }
        int quantityBefore = item.getQuantityOnHand();
        int newQuantity;

        switch (txnType) {
            case PURCHASE, ADJUSTMENT_IN, RETURN -> newQuantity = quantityBefore + request.getQuantity();
            case SALE, ADJUSTMENT_OUT, DAMAGE -> {
                if (request.getQuantity() > quantityBefore) {
                    throw new BusinessException("Insufficient stock. Available: " + quantityBefore, "INSUFFICIENT_STOCK");
                }
                newQuantity = quantityBefore - request.getQuantity();
            }
            default -> throw new BusinessException("Invalid transaction type", "INVALID_TXN_TYPE");
        }

        boolean wasLowStock = item.isLowStock();
        item.setQuantityOnHand(newQuantity);
        item = inventoryRepository.save(item);

        recordTransaction(item, txnType, request.getQuantity(), quantityBefore, newQuantity,
                request.getUnitPrice(), request.getReferenceId(), request.getReferenceType(),
                request.getNotes(), request.getSupplier());

        // Only fire the moment stock crosses into the low/out-of-stock zone, not on every
        // subsequent adjustment while it stays there — otherwise every small sale of an
        // already-low item would spam the same alert.
        if (!wasLowStock && item.isLowStock()) {
            notificationPublisher.notifyRoles(STAFF_NOTIFY_ROLES,
                    item.isOutOfStock() ? "OUT_OF_STOCK" : "LOW_STOCK",
                    (item.isOutOfStock() ? "Out of stock: " : "Low stock: ") + item.getName(),
                    item.getName() + " is down to " + newQuantity + " unit(s) (reorder level: " + item.getReorderLevel() + ")",
                    "InventoryItem", item.getId());
        }

        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public List<InventoryDto.ItemResponse> getLowStockItems() {
        return inventoryRepository.findLowStockItems().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryDto.ItemResponse> getAllItems(String search, String category, Pageable pageable) {
        var page = (search != null && !search.isBlank())
                ? inventoryRepository.searchItems(search, pageable)
                : (category != null && !category.isBlank())
                ? inventoryRepository.findByCategory(InventoryCategory.parse(category), pageable)
                : inventoryRepository.findAll(pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public InventoryDto.ItemResponse getById(String id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public InventoryDto.ItemResponse getByBarcode(String barcode) {
        String code = barcode == null ? "" : barcode.trim();
        return toResponse(inventoryRepository.findByBarcode(code)
                .or(() -> inventoryRepository.findBySku(code))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with barcode: " + code)));
    }

    /**
     * One scan = one movement. Used by the "scan out" screen: staff scan a label and the item leaves
     * stock (or comes back in). Goes through adjustStock so the ledger, low-stock alerts and the
     * insufficient-stock check behave exactly as for a manual adjustment.
     */
    public InventoryDto.ItemResponse scanAdjust(String barcode, InventoryDto.ScanRequest request) {
        String code = barcode == null ? "" : barcode.trim();
        InventoryItem item = inventoryRepository.findByBarcode(code)
                .or(() -> inventoryRepository.findBySku(code))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with barcode: " + code));
        boolean out = request.getMode() == null || !"IN".equalsIgnoreCase(request.getMode());
        int qty = request.getQuantity() != null && request.getQuantity() > 0 ? request.getQuantity() : 1;
        InventoryDto.StockAdjustmentRequest adj = InventoryDto.StockAdjustmentRequest.builder()
                .transactionType(out ? "SALE" : "PURCHASE").quantity(qty)
                .referenceType("BARCODE_SCAN").notes(request.getNotes() != null ? request.getNotes() : "Barcode scan " + (out ? "out" : "in"))
                .build();
        return adjustStock(item.getId(), adj);
    }

    private String generateAndUploadQrCode(String itemId, String sku) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(sku + "|" + itemId, BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        byte[] qrBytes = out.toByteArray();
        var result = storageService.uploadBytes(qrBytes, "inventory/qrcodes", sku + "-qr.png", "image/png");
        return result.getUrl();
    }

    private void recordTransaction(InventoryItem item, TransactionType type, int qty, int before,
                                    int after, java.math.BigDecimal price, String refId,
                                    String refType, String notes, String supplier) {
        StockTransaction txn = StockTransaction.builder()
                .item(item).transactionType(type).quantity(qty)
                .quantityBefore(before).quantityAfter(after).unitPrice(price)
                .referenceId(refId).referenceType(refType)
                .notes(notes).supplier(supplier).build();
        transactionRepository.save(txn);
    }

    private InventoryItem findById(String id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", "id", id));
    }

    private InventoryDto.ItemResponse toResponse(InventoryItem i) {
        return InventoryDto.ItemResponse.builder()
                .id(i.getId()).name(i.getName()).description(i.getDescription())
                .sku(i.getSku()).barcode(i.getBarcode()).qrCodeUrl(i.getQrCodeUrl())
                .category(i.getCategory().name()).unit(i.getUnit())
                .quantityOnHand(i.getQuantityOnHand()).reorderLevel(i.getReorderLevel())
                .reorderQuantity(i.getReorderQuantity()).costPrice(i.getCostPrice())
                .sellingPrice(i.getSellingPrice()).location(i.getLocation())
                .active(i.isActive()).lowStock(i.isLowStock()).outOfStock(i.isOutOfStock())
                .notes(i.getNotes()).createdAt(i.getCreatedAt()).build();
    }
}
