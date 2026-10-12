package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.AttendanceCorrection;
import com.cameraman.attendance.domain.LeaveStatus;
import com.cameraman.attendance.domain.PunchDirection;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.repository.AttendanceCorrectionRepository;
import com.cameraman.audit.service.AuditService;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.notification.service.NotificationPublisher;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.repository.StaffRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Missing-punch corrections: staff request, a manager approves (which adds a MANUAL punch) or rejects. */
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceCorrectionService {

    private static final List<String> APPROVER_ROLES = List.of("ADMIN", "SUPER_ADMIN", "MANAGER");
    static final int MAX_AGE_DAYS = 31;

    private final AttendanceCorrectionRepository repository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final AttendanceService attendanceService;
    private final NotificationPublisher notificationPublisher;
    private final AuditService auditService;

    public AttendanceCorrection request(String actorUserId, LocalDateTime proposedTime, String direction, String reason) {
        StaffProfile staff = staffRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new BusinessException("You don't have a staff profile yet", "NO_STAFF_PROFILE"));
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Please explain what happened", "REASON_REQUIRED");
        }
        if (reason.length() > 500) {
            throw new BusinessException("Please keep the reason under 500 characters", "REASON_TOO_LONG");
        }
        if (proposedTime == null || proposedTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException("The punch time can't be in the future", "TIME_IN_FUTURE");
        }
        if (proposedTime.isBefore(LocalDateTime.now().minusDays(MAX_AGE_DAYS))) {
            throw new BusinessException("Corrections can only be requested for the last " + MAX_AGE_DAYS + " days", "TIME_TOO_OLD");
        }
        if (repository.existsByStaffIdAndProposedTimeAndStatus(staff.getId(), proposedTime, LeaveStatus.PENDING)) {
            throw new BusinessException("You already have a pending request for that time", "DUPLICATE_REQUEST");
        }
        String name = userRepository.findById(actorUserId).map(u -> u.getFirstName() + " " + u.getLastName()).orElse(null);
        AttendanceCorrection c = repository.save(AttendanceCorrection.builder()
                .staffId(staff.getId()).staffName(name).proposedTime(proposedTime)
                .direction(parse(direction)).reason(reason.trim()).build());
        notificationPublisher.notifyRoles(APPROVER_ROLES, "ATTENDANCE_CORRECTION_REQUESTED",
                "Attendance correction: " + (name != null ? name : "staff"),
                "Missing punch at " + proposedTime + ": " + reason.trim(), "AttendanceCorrection", c.getId());
        return c;
    }

    public AttendanceCorrection approve(String id, String reviewerId, String notes) {
        AttendanceCorrection c = pending(id);
        AttendanceDto.ManualEntryRequest entry = new AttendanceDto.ManualEntryRequest();
        entry.setStaffId(c.getStaffId());
        entry.setPunchTime(c.getProposedTime());
        entry.setDirection(c.getDirection().name());
        entry.setNotes("Correction approved: " + abbreviate(c.getReason()));
        AttendanceDto.RecordResponse record = attendanceService.manualEntry(entry);
        c.setStatus(LeaveStatus.APPROVED);
        c.setRecordId(record.getId());
        stamp(c, reviewerId, notes);
        auditService.log("ATTENDANCE_CORRECTION_APPROVED", "AttendanceCorrection", c.getId(), "PENDING", "APPROVED", reviewerId);
        notifyStaff(c, "approved");
        return repository.save(c);
    }

    public AttendanceCorrection reject(String id, String reviewerId, String notes) {
        AttendanceCorrection c = pending(id);
        c.setStatus(LeaveStatus.REJECTED);
        stamp(c, reviewerId, notes);
        auditService.log("ATTENDANCE_CORRECTION_REJECTED", "AttendanceCorrection", c.getId(), "PENDING", "REJECTED", reviewerId);
        notifyStaff(c, "rejected");
        return repository.save(c);
    }

    @Transactional(readOnly = true)
    public List<AttendanceCorrection> mine(String actorUserId) {
        return staffRepository.findByUserId(actorUserId)
                .map(s -> repository.findByStaffIdOrderByCreatedAtDesc(s.getId())).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<AttendanceCorrection> byStatus(String status) {
        LeaveStatus s = status == null || status.isBlank() ? LeaveStatus.PENDING : LeaveStatus.valueOf(status.toUpperCase());
        return repository.findByStatusOrderByCreatedAtAsc(s);
    }

    private AttendanceCorrection pending(String id) {
        AttendanceCorrection c = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Correction request not found"));
        if (c.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException("This request has already been reviewed", "ALREADY_REVIEWED");
        }
        return c;
    }

    private void stamp(AttendanceCorrection c, String reviewerId, String notes) {
        User reviewer = reviewerId != null ? userRepository.findById(reviewerId).orElse(null) : null;
        c.setReviewedBy(reviewerId);
        c.setReviewedByName(reviewer != null ? reviewer.getFirstName() + " " + reviewer.getLastName() : null);
        c.setReviewedAt(LocalDateTime.now());
        c.setReviewNotes(notes != null && notes.length() > 500 ? notes.substring(0, 500) : notes);
    }

    private void notifyStaff(AttendanceCorrection c, String decision) {
        staffRepository.findById(c.getStaffId()).ifPresent(s -> notificationPublisher.notifyUser(s.getUserId(),
                "LEAVE_CORRECTION_" + decision.toUpperCase(), "Your attendance correction was " + decision,
                "Missing punch at " + c.getProposedTime() + " was " + decision + ".", "AttendanceCorrection", c.getId()));
    }

    private static PunchDirection parse(String d) {
        try { return d == null ? PunchDirection.UNKNOWN : PunchDirection.valueOf(d.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return PunchDirection.UNKNOWN; }
    }

    private static String abbreviate(String s) { return s.length() > 200 ? s.substring(0, 200) : s; }
}
