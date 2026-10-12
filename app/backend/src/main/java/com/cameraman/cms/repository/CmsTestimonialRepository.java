package com.cameraman.cms.repository;

import com.cameraman.cms.domain.CmsTestimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CmsTestimonialRepository extends JpaRepository<CmsTestimonial, String> {
    List<CmsTestimonial> findByActiveTrueOrderBySortOrderAsc();
}
