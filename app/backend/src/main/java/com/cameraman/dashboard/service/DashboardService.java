package com.cameraman.dashboard.service;

import com.cameraman.billing.domain.InvoiceStatus;
import com.cameraman.billing.repository.InvoiceRepository;
import com.cameraman.booking.domain.BookingStatus;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.dashboard.dto.DashboardDto;
import com.cameraman.gallery.domain.GalleryStatus;
import com.cameraman.gallery.repository.GalleryRepository;
import com.cameraman.inventory.service.InventoryService;
import com.cameraman.photoselection.domain.SelectionStatus;
import com.cameraman.photoselection.repository.PhotoSelectionRepository;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final BookingRepository bookingRepository;
    private final InvoiceRepository invoiceRepository;
    private final GalleryRepository galleryRepository;
    private final PhotoSelectionRepository selectionRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;

    @Cacheable(value = "dashboardMetrics", key = "'overview'")
    public DashboardDto.OverviewMetrics getOverviewMetrics() {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate startOfLastMonth = startOfMonth.minusMonths(1);
        LocalDate endOfLastMonth = startOfMonth.minusDays(1);

        // Revenue
        BigDecimal monthlyRevenue = invoiceRepository.sumRevenueByDateRange(startOfMonth, today);
        BigDecimal lastMonthRevenue = invoiceRepository.sumRevenueByDateRange(startOfLastMonth, endOfLastMonth);
        BigDecimal outstandingBalance = invoiceRepository.sumOutstandingBalance();

        // Bookings
        long todayBookings = bookingRepository.countByBookingDate(today);
        long monthlyBookings = bookingRepository.countMonthlyBookings(startOfMonth, today);
        long pendingBookings = bookingRepository.countByStatus(BookingStatus.PENDING);
        long confirmedBookings = bookingRepository.countByStatus(BookingStatus.CONFIRMED);

        // Galleries & Selections
        long publishedGalleries = galleryRepository.countByStatus(GalleryStatus.PUBLISHED);
        long pendingSelections = selectionRepository.countByStatus(SelectionStatus.OPEN);
        long lockedSelections = selectionRepository.countByStatus(SelectionStatus.LOCKED);

        // Invoices
        long draftInvoices = invoiceRepository.countByStatus(InvoiceStatus.DRAFT);
        long overdueInvoices = invoiceRepository.countByStatus(InvoiceStatus.OVERDUE);

        // Revenue trend (last 6 months)
        List<DashboardDto.MonthlyRevenue> revenueTrend = getMonthlyRevenueTrend(6);

        return DashboardDto.OverviewMetrics.builder()
                .monthlyRevenue(monthlyRevenue)
                .lastMonthRevenue(lastMonthRevenue)
                .outstandingBalance(outstandingBalance)
                .todayBookings(todayBookings)
                .monthlyBookings(monthlyBookings)
                .pendingBookings(pendingBookings)
                .confirmedBookings(confirmedBookings)
                .publishedGalleries(publishedGalleries)
                .pendingSelections(pendingSelections)
                .lockedSelections(lockedSelections)
                .draftInvoices(draftInvoices)
                .overdueInvoices(overdueInvoices)
                .revenueTrend(revenueTrend)
                .asOf(LocalDateTime.now())
                .build();
    }

    public List<DashboardDto.MonthlyRevenue> getMonthlyRevenueTrend(int months) {
        List<DashboardDto.MonthlyRevenue> trend = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate monthStart = now.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            BigDecimal revenue = invoiceRepository.sumRevenueByDateRange(monthStart, monthEnd);
            long bookings = bookingRepository.countMonthlyBookings(monthStart, monthEnd);
            trend.add(DashboardDto.MonthlyRevenue.builder()
                    .month(monthStart.getMonthValue())
                    .year(monthStart.getYear())
                    .monthName(monthStart.getMonth().name())
                    .revenue(revenue)
                    .bookingCount(bookings)
                    .build());
        }
        return trend;
    }

    public List<DashboardDto.BookingCalendarSummary> getCalendarSummary(LocalDate from, LocalDate to) {
        return bookingRepository.findByBookingDateBetween(from, to).stream()
                .collect(Collectors.groupingBy(b -> b.getBookingDate()))
                .entrySet().stream()
                .map(e -> DashboardDto.BookingCalendarSummary.builder()
                        .date(e.getKey())
                        .count(e.getValue().size())
                        .statusBreakdown(e.getValue().stream()
                                .collect(Collectors.groupingBy(b -> b.getStatus().name(), Collectors.counting())))
                        .build())
                .sorted(Comparator.comparing(DashboardDto.BookingCalendarSummary::getDate))
                .collect(Collectors.toList());
    }

    public DashboardDto.StaffProductivity getStaffProductivity(LocalDate from, LocalDate to) {
        // Aggregate photographer booking counts
        List<Object[]> photographerStats = bookingRepository.findAll().stream()
                .filter(b -> b.getPhotographer() != null
                        && !b.getBookingDate().isBefore(from)
                        && !b.getBookingDate().isAfter(to)
                        && b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.groupingBy(
                        b -> b.getPhotographer().getFullName(),
                        Collectors.counting()))
                .entrySet().stream()
                .map(e -> new Object[]{e.getKey(), e.getValue()})
                .collect(Collectors.toList());

        List<DashboardDto.PhotographerStats> stats = photographerStats.stream()
                .map(o -> DashboardDto.PhotographerStats.builder()
                        .name((String) o[0])
                        .bookingCount((Long) o[1])
                        .build())
                .sorted((a, b) -> Long.compare(b.getBookingCount(), a.getBookingCount()))
                .collect(Collectors.toList());

        return DashboardDto.StaffProductivity.builder()
                .photographerStats(stats)
                .fromDate(from).toDate(to).build();
    }

    /**
     * Groups bookings in the date range by package name, so the owner can see
     * which packages actually sell (feeds the "best-selling package" widget
     * on the analytics page). Bookings with no package selected (packageName
     * null/blank, e.g. custom/ad-hoc shoots) are grouped under "Custom / No Package".
     */
    public DashboardDto.PackagePopularity getPackagePopularity(LocalDate from, LocalDate to) {
        List<com.cameraman.booking.domain.Booking> inRange = bookingRepository.findAll().stream()
                .filter(b -> !b.getBookingDate().isBefore(from)
                        && !b.getBookingDate().isAfter(to)
                        && b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.toList());

        Map<String, List<com.cameraman.booking.domain.Booking>> grouped = inRange.stream()
                .collect(Collectors.groupingBy(b -> (b.getPackageName() == null || b.getPackageName().isBlank())
                        ? "Custom / No Package" : b.getPackageName()));

        List<DashboardDto.PackageStats> stats = grouped.entrySet().stream()
                .map(e -> DashboardDto.PackageStats.builder()
                        .packageName(e.getKey())
                        .bookingCount(e.getValue().size())
                        .revenue(e.getValue().stream()
                                .map(b -> b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount())
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .sorted((a, b) -> Long.compare(b.getBookingCount(), a.getBookingCount()))
                .collect(Collectors.toList());

        return DashboardDto.PackagePopularity.builder()
                .packageStats(stats)
                .fromDate(from).toDate(to).build();
    }

    /**
     * Backs GET /dashboard/stats — the single call the main dashboard screen
     * makes. Shaped to match the frontend's DashboardStats interface exactly
     * so the UI shows real data instead of silently falling back to mocks.
     */
    public DashboardDto.Stats getStats() {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate epoch = LocalDate.of(2000, 1, 1);

        long totalBookings = bookingRepository.count();
        long activeBookings = bookingRepository.countByStatus(BookingStatus.CONFIRMED);
        long pendingBookings = bookingRepository.countByStatus(BookingStatus.PENDING);
        long completedBookings = bookingRepository.countByStatus(BookingStatus.COMPLETED);

        BigDecimal totalRevenue = invoiceRepository.sumRevenueByDateRange(epoch, today);
        BigDecimal pendingRevenue = invoiceRepository.sumOutstandingBalance();
        BigDecimal monthlyRevenue = invoiceRepository.sumRevenueByDateRange(startOfMonth, today);

        long totalGalleries = galleryRepository.count();
        long pendingSelectionsCount = selectionRepository.countByStatus(SelectionStatus.OPEN);
        long totalClients = userRepository.findByRoleName("CLIENT",
                org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();

        List<Map<String, Object>> recentBookings = bookingRepository
                .findAll(org.springframework.data.domain.PageRequest.of(0, 5,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .getContent().stream().map(this::bookingSummary).collect(Collectors.toList());

        List<Map<String, Object>> upcomingToday = bookingRepository.findByBookingDate(today).stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .limit(5)
                .map(this::bookingSummary)
                .collect(Collectors.toList());

        List<Map<String, Object>> pendingInvoices = new ArrayList<>();
        pendingInvoices.addAll(invoiceRepository
                .findByStatus(InvoiceStatus.OVERDUE, org.springframework.data.domain.PageRequest.of(0, 5))
                .getContent().stream().map(this::invoiceSummary).collect(Collectors.toList()));
        if (pendingInvoices.size() < 5) {
            pendingInvoices.addAll(invoiceRepository
                    .findByStatus(InvoiceStatus.SENT,
                            org.springframework.data.domain.PageRequest.of(0, 5 - pendingInvoices.size()))
                    .getContent().stream().map(this::invoiceSummary).collect(Collectors.toList()));
        }

        List<DashboardDto.RevenuePoint> revenueChart = getMonthlyRevenueTrend(6).stream()
                .map(m -> DashboardDto.RevenuePoint.builder()
                        .month(m.getMonthName().substring(0, 1) + m.getMonthName().substring(1, 3).toLowerCase())
                        .amount(m.getRevenue())
                        .build())
                .collect(Collectors.toList());

        List<DashboardDto.StatusBreakdown> bookingStatusBreakdown = List.of(
                DashboardDto.StatusBreakdown.builder().status("PENDING")
                        .count(bookingRepository.countByStatus(BookingStatus.PENDING)).color("#f59e0b").build(),
                DashboardDto.StatusBreakdown.builder().status("CONFIRMED")
                        .count(bookingRepository.countByStatus(BookingStatus.CONFIRMED)).color("#1a73e8").build(),
                DashboardDto.StatusBreakdown.builder().status("COMPLETED")
                        .count(bookingRepository.countByStatus(BookingStatus.COMPLETED)).color("#34a853").build(),
                DashboardDto.StatusBreakdown.builder().status("CANCELLED")
                        .count(bookingRepository.countByStatus(BookingStatus.CANCELLED)).color("#ea4335").build()
        );

        return DashboardDto.Stats.builder()
                .totalBookings(totalBookings)
                .activeBookings(activeBookings)
                .pendingBookings(pendingBookings)
                .completedBookings(completedBookings)
                .totalRevenue(totalRevenue)
                .pendingRevenue(pendingRevenue)
                .monthlyRevenue(monthlyRevenue)
                .totalGalleries(totalGalleries)
                .pendingSelections(pendingSelectionsCount)
                .totalClients(totalClients)
                .recentBookings(recentBookings)
                .upcomingToday(upcomingToday)
                .pendingInvoices(pendingInvoices)
                .revenueChart(revenueChart)
                .bookingStatusBreakdown(bookingStatusBreakdown)
                .build();
    }

    private Map<String, Object> bookingSummary(com.cameraman.booking.domain.Booking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("bookingNumber", b.getBookingNumber());
        m.put("clientName", b.getClientName());
        m.put("bookingDate", b.getBookingDate());
        m.put("startTime", b.getStartTime());
        m.put("status", b.getStatus());
        m.put("totalAmount", b.getTotalAmount());
        m.put("shootCategory", b.getShootCategory());
        return m;
    }

    private Map<String, Object> invoiceSummary(com.cameraman.billing.domain.Invoice i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("invoiceNumber", i.getInvoiceNumber());
        m.put("clientName", i.getClientName());
        m.put("dueDate", i.getDueDate());
        m.put("status", i.getStatus());
        m.put("totalAmount", i.getTotalAmount());
        m.put("balanceDue", i.getBalanceDue());
        return m;
    }
}
