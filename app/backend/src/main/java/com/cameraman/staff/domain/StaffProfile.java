package com.cameraman.staff.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "staff_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffProfile extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "employee_id", unique = true, length = 20)
    private String employeeId;

    @Column(name = "department", length = 60)
    private String department;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "specializations", length = 500)
    private String specializations;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "available_for_booking")
    @Builder.Default
    private boolean availableForBooking = true;

    @Column(name = "max_bookings_per_day")
    @Builder.Default
    private int maxBookingsPerDay = 2;
}
