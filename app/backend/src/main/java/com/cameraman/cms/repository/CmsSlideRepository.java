package com.cameraman.cms.repository;

import com.cameraman.cms.domain.CmsSlide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CmsSlideRepository extends JpaRepository<CmsSlide, String> {
    List<CmsSlide> findByActiveTrueOrderBySortOrderAsc();
}
