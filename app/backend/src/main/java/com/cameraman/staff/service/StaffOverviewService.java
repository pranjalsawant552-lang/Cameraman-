package com.cameraman.staff.service;

import com.cameraman.attendance.domain.LeaveRequest;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.repository.LeaveRequestRepository;
import com.cameraman.attendance.service.AttendanceService;
import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.domain.BookingStatus;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.staff.dto.StaffDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/** Read-only roll-up for the admin staff drawer: profile, shoots, this month's attendance and leave. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffOverviewService {

    private final StaffService staffService;
    private final BookingRepository bookingRepository;
    private final LeaveRequestRepository leaveRepository;
    private final AttendanceService attendanceService;

    public StaffDto.Overview overview(String staffId) {
        StaffDto.StaffResponse profile = staffService.getById(staffId);
        String userId = profile.getUserId();
        LocalDate today = LocalDate.now();

        List<Booking> upcoming = bookingRepository
                .findTop10ByPhotographerIdAndBookingDateGreaterThanEqualOrderByBookingDateAsc(userId, today);
        List<Booking> recent = bookingRepository
                .findTop10ByPhotographerIdAndBookingDateLessThanOrderByBookingDateDesc(userId, today);

        AttendanceDto.MonthlyReport month = attendanceService.monthlyReport(staffId, today.getYear(), today.getMonthValue());
        List<LeaveRequest> leaves = leaveRepository.findByStaffIdOrderByFromDateDesc(staffId);

        return StaffDto.Overview.builder()
                .profile(profile)
                .totalShoots(bookingRepository.countByPhotographerId(userId))
                .completedShoots(bookingRepository.countByPhotographerIdAndStatus(userId, BookingStatus.COMPLETED))
                .upcomingShoots(upcoming.stream().map(this::shoot).collect(Collectors.toList()))
                .recentShoots(recent.stream().map(this::shoot).collect(Collectors.toList()))
                .attendanceYear(month.getYear()).attendanceMonth(month.getMonth())
                .presentDays(month.getPresentDays()).lateDays(month.getLateDays()).halfDays(month.getHalfDays())
                .absentDays(month.getAbsentDays()).leaveDays(month.getLeaveDays()).workingDays(month.getWorkingDays())
                .overtimeMinutes(month.getOvertimeMinutes()).shiftName(month.getShiftName())
                .recentLeaves(leaves.stream().limit(5).map(l -> StaffDto.LeaveSummary.builder()
                        .id(l.getId()).fromDate(l.getFromDate()).toDate(l.getToDate())
                        .dayCount(ChronoUnit.DAYS.between(l.getFromDate(), l.getToDate()) + 1)
                        .status(l.getStatus().name()).reason(l.getReason()).build()).collect(Collectors.toList()))
                .build();
    }

    private StaffDto.ShootSummary shoot(Booking b) {
        String client = b.getClient() == null ? null
                : (b.getClient().getFirstName() + " " + (b.getClient().getLastName() != null ? b.getClient().getLastName() : "")).trim();
        return StaffDto.ShootSummary.builder()
                .id(b.getId()).bookingNumber(b.getBookingNumber()).bookingDate(b.getBookingDate())
                .packageName(b.getPackageName())
                .shootCategory(b.getShootCategory() != null ? b.getShootCategory().name() : null)
                .status(b.getStatus().name()).clientName(client).build();
    }
}
