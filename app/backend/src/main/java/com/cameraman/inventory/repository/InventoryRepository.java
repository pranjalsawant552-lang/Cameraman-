package com.cameraman.inventory.repository;

import com.cameraman.inventory.domain.InventoryCategory;
import com.cameraman.inventory.domain.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {
    Optional<InventoryItem> findBySku(String sku);
    Optional<InventoryItem> findByBarcode(String barcode);

    @Query("SELECT i FROM InventoryItem i WHERE i.quantityOnHand <= i.reorderLevel AND i.active = true")
    List<InventoryItem> findLowStockItems();

    @Query("SELECT i FROM InventoryItem i WHERE i.quantityOnHand <= 0 AND i.active = true")
    List<InventoryItem> findOutOfStockItems();

    Page<InventoryItem> findByCategory(InventoryCategory category, Pageable pageable);

    @Query("SELECT i FROM InventoryItem i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR i.sku LIKE CONCAT('%',:q,'%') OR i.barcode LIKE CONCAT('%',:q,'%')")
    Page<InventoryItem> searchItems(@Param("q") String query, Pageable pageable);

    Page<InventoryItem> findByActiveTrueOrderByNameAsc(Pageable pageable);
    boolean existsBySku(String sku);
    boolean existsByBarcode(String barcode);
}
