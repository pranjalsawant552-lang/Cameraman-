package com.cameraman.attendance.repository;

import com.cameraman.attendance.domain.WorkShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkShiftRepository extends JpaRepository<WorkShift, String> {
    List<WorkShift> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
