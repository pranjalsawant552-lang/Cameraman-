package com.cameraman.staff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class StaffDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateStaffRequest {
        @NotBlank private String userId;
        @Size(max = 20) private String employeeId;
        @Size(max = 60) private String department;
        @Size(max = 100) private String designation;
        private LocalDate joiningDate;
        private String specializations;
        private String bio;
        private String portfolioUrl;
        private String instagramUrl;
        private Boolean availableForBooking;
        @Min(1) private Integer maxBookingsPerDay;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateStaffRequest {
        @Size(max = 20) private String employeeId;
        @Size(max = 60) private String department;
        @Size(max = 100) private String designation;
        private LocalDate joiningDate;
        private String specializations;
        private String bio;
        private String portfolioUrl;
        private String instagramUrl;
        private Boolean availableForBooking;
        @Min(1) private Integer maxBookingsPerDay;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StaffResponse {
        private String id;
        private String userId;
        private String firstName;
        private String lastName;
        private String email;
        private String mobile;
        private String profilePhotoUrl;
        private List<String> roles;
        private String employeeId;
        private String department;
        private String designation;
        private LocalDate joiningDate;
        private String specializations;
        private String bio;
        private String portfolioUrl;
        private String instagramUrl;
        private boolean availableForBooking;
        private int maxBookingsPerDay;
        private int assignedTasksCount;
        private int completedTasksCount;
        /** ACTIVE / INACTIVE / ... of the linked login account. */
        private String userStatus;
    }

    // ───────────── Detail drawer ─────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ShootSummary {
        private String id;
        private String bookingNumber;
        private LocalDate bookingDate;
        private String packageName;
        private String shootCategory;
        private String status;
        private String clientName;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LeaveSummary {
        private String id;
        private LocalDate fromDate;
        private LocalDate toDate;
        private long dayCount;
        private String status;
        private String reason;
    }

    /** Everything the staff detail drawer shows, in one call. */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Overview {
        private StaffResponse profile;
        private long totalShoots;
        private long completedShoots;
        private java.util.List<ShootSummary> upcomingShoots;
        private java.util.List<ShootSummary> recentShoots;
        private int attendanceYear;
        private int attendanceMonth;
        private int presentDays;
        private int lateDays;
        private int halfDays;
        private int absentDays;
        private int leaveDays;
        private int workingDays;
        private long overtimeMinutes;
        private String shiftName;
        private java.util.List<LeaveSummary> recentLeaves;
    }
}
