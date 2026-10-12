package com.cameraman.package_mgmt.repository;

import com.cameraman.package_mgmt.domain.Package;
import com.cameraman.package_mgmt.domain.PackageCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PackageRepository extends JpaRepository<Package, String> {
    List<Package> findByActiveTrueAndArchivedFalseOrderBySortOrderAsc();
    Page<Package> findByCategory(PackageCategory category, Pageable pageable);
    Page<Package> findByArchivedFalse(Pageable pageable);

    @Query("SELECT p FROM Package p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) AND p.archived = false")
    Page<Package> searchPackages(@Param("search") String search, Pageable pageable);

    List<Package> findByCategoryAndActiveTrueAndArchivedFalse(PackageCategory category);
}
