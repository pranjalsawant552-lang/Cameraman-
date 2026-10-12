package com.cameraman.product.repository;

import com.cameraman.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    Page<Product> findByActiveTrue(Pageable pageable);
    java.util.Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    Page<Product> findByCategoryId(String categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR p.sku LIKE CONCAT('%',:q,'%')")
    Page<Product> searchProducts(@Param("q") String query, Pageable pageable);
}
