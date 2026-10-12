package com.cameraman.staff.repository;

import com.cameraman.staff.domain.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<StaffProfile, String> {
    Optional<StaffProfile> findByUserId(String userId);
    Optional<StaffProfile> findByEmployeeId(String employeeId);
    List<StaffProfile> findByAvailableForBookingTrue();
    boolean existsByUserId(String userId);
}
