package com.cameraman.product.repository;

import com.cameraman.product.domain.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, String> {
    List<ProductCategory> findByActiveTrue();
}
