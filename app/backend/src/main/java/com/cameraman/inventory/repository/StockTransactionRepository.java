package com.cameraman.inventory.repository;

import com.cameraman.inventory.domain.StockTransaction;
import com.cameraman.inventory.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, String> {
    Page<StockTransaction> findByItemId(String itemId, Pageable pageable);
    List<StockTransaction> findByItemIdOrderByCreatedAtDesc(String itemId);
    Page<StockTransaction> findByTransactionType(TransactionType type, Pageable pageable);
}
