package com.cameraman.engagement.repository;

import com.cameraman.engagement.domain.IntakeQuestion;
import com.cameraman.package_mgmt.domain.PackageCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntakeQuestionRepository extends JpaRepository<IntakeQuestion, String> {
    List<IntakeQuestion> findByCategoryAndActiveTrueOrderBySortOrderAsc(PackageCategory category);
    List<IntakeQuestion> findAllByOrderByCategoryAscSortOrderAsc();
}
