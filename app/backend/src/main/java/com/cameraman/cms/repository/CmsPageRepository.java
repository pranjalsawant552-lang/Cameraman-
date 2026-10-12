package com.cameraman.cms.repository;

import com.cameraman.cms.domain.CmsPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CmsPageRepository extends JpaRepository<CmsPage, String> {
    Optional<CmsPage> findByPageKey(String key);
    List<CmsPage> findByPublishedTrue();
}
