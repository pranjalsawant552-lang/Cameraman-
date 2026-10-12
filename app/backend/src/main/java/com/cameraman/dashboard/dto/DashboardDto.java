package com.cameraman.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class DashboardDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OverviewMetrics {
        private BigDecimal monthlyRevenue;
        private BigDecimal lastMonthRevenue;
        private BigDecimal outstandingBalance;
        private long todayBookings;
        private long monthlyBookings;
        private long pendingBookings;
        private long confirmedBookings;
        private long publishedGalleries;
        private long pendingSelections;
        private long lockedSelections;
        private long draftInvoices;
        private long overdueInvoices;
        private List<MonthlyRevenue> revenueTrend;
        private LocalDateTime asOf;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MonthlyRevenue {
        private int month;
        private int year;
        private String monthName;
        private BigDecimal revenue;
        private long bookingCount;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BookingCalendarSummary {
        private LocalDate date;
        private long count;
        private Map<String, Long> statusBreakdown;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StaffProductivity {
        private List<PhotographerStats> photographerStats;
        private LocalDate fromDate;
        private LocalDate toDate;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PhotographerStats {
        private String name;
        private long bookingCount;
        private BigDecimal revenue;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PackagePopularity {
        private List<PackageStats> packageStats;
        private LocalDate fromDate;
        private LocalDate toDate;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PackageStats {
        private String packageName;
        private long bookingCount;
        private BigDecimal revenue;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Stats {
        private long totalBookings;
        private long activeBookings;
        private long pendingBookings;
        private long completedBookings;
        private BigDecimal totalRevenue;
        private BigDecimal pendingRevenue;
        private BigDecimal monthlyRevenue;
        private long totalGalleries;
        private long pendingSelections;
        private long totalClients;
        private List<Map<String, Object>> recentBookings;
        private List<Map<String, Object>> upcomingToday;
        private List<Map<String, Object>> pendingInvoices;
        private List<RevenuePoint> revenueChart;
        private List<StatusBreakdown> bookingStatusBreakdown;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RevenuePoint {
        private String month;
        private BigDecimal amount;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusBreakdown {
        private String status;
        private long count;
        private String color;
    }
}
