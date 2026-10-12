package com.cameraman.recommendation.repository;

import com.cameraman.package_mgmt.domain.PackageCategory;
import com.cameraman.recommendation.domain.CategoryRecommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRecommendationRepository extends JpaRepository<CategoryRecommendation, String> {
    List<CategoryRecommendation> findByCategoryAndActiveTrueOrderByPriorityAsc(PackageCategory category);
    Page<CategoryRecommendation> findByCategory(PackageCategory category, Pageable pageable);
    boolean existsByCategoryAndProductId(PackageCategory category, String productId);
}
