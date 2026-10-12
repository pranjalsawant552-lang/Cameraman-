package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.LeaveRequest;
import com.cameraman.attendance.domain.LeaveStatus;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.repository.LeaveRequestRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.repository.StaffRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final com.cameraman.notification.service.NotificationPublisher notificationPublisher;

    private static final List<String> APPROVER_ROLES = List.of("ADMIN", "SUPER_ADMIN", "MANAGER");

    static final int MAX_LEAVE_DAYS = 60;

    public AttendanceDto.LeaveResponse apply(AttendanceDto.LeaveApplyRequest request) {
        return apply(request, null, true);
    }

    /**
     * @param actorUserId the signed-in user (from the JWT)
     * @param isManager   managers/admins may file leave on behalf of anyone; everyone else only for themselves
     */
    public AttendanceDto.LeaveResponse apply(AttendanceDto.LeaveApplyRequest request, String actorUserId, boolean isManager) {
        StaffProfile staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
        if (!isManager && (actorUserId == null || !actorUserId.equals(staff.getUserId()))) {
            throw new ResourceNotFoundException("Staff member not found");
        }
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new BusinessException("End date can't be before the start date", "INVALID_LEAVE_RANGE");
        }
        if (ChronoUnit.DAYS.between(request.getFromDate(), request.getToDate()) + 1 > MAX_LEAVE_DAYS) {
            throw new BusinessException("A single leave request can cover at most " + MAX_LEAVE_DAYS + " days", "LEAVE_TOO_LONG");
        }
        if (!isManager && request.getToDate().isBefore(java.time.LocalDate.now())) {
            throw new BusinessException("Leave can't be requested for dates that have already passed", "LEAVE_IN_PAST");
        }
        if (request.getReason() != null && request.getReason().length() > 500) {
            throw new BusinessException("Please keep the reason under 500 characters", "REASON_TOO_LONG");
        }
        if (leaveRepository.existsActiveOverlapping(staff.getId(), request.getFromDate(), request.getToDate())) {
            throw new BusinessException("There is already a pending or approved leave covering some of these dates", "LEAVE_OVERLAP");
        }
        User user = userRepository.findById(staff.getUserId()).orElse(null);
        String staffName = user != null ? user.getFirstName() + " " + user.getLastName() : null;

        LeaveRequest leave = LeaveRequest.builder()
                .staffId(staff.getId())
                .staffName(staffName)
                .fromDate(request.getFromDate())
                .toDate(request.getToDate())
                .reason(request.getReason())
                .status(LeaveStatus.PENDING)
                .build();

        leave = leaveRepository.save(leave);

        notificationPublisher.notifyRoles(APPROVER_ROLES, "LEAVE_REQUESTED",
                "New leave request: " + (staffName != null ? staffName : "A staff member"),
                (staffName != null ? staffName : "A staff member") + " requested leave from "
                        + request.getFromDate() + " to " + request.getToDate(),
                "LeaveRequest", leave.getId());

        return toResponse(leave);
    }

    public AttendanceDto.LeaveResponse approve(String id, String reviewerUserId, AttendanceDto.LeaveReviewRequest request) {
        LeaveRequest leave = findById(id);
        requirePending(leave);
        leave.setStatus(LeaveStatus.APPROVED);
        applyReview(leave, reviewerUserId, request);
        leave = leaveRepository.save(leave);
        notifyStaffOfDecision(leave, "approved");
        return toResponse(leave);
    }

    public AttendanceDto.LeaveResponse reject(String id, String reviewerUserId, AttendanceDto.LeaveReviewRequest request) {
        LeaveRequest leave = findById(id);
        requirePending(leave);
        leave.setStatus(LeaveStatus.REJECTED);
        applyReview(leave, reviewerUserId, request);
        leave = leaveRepository.save(leave);
        notifyStaffOfDecision(leave, "rejected");
        return toResponse(leave);
    }

    private void notifyStaffOfDecision(LeaveRequest leave, String decision) {
        staffRepository.findById(leave.getStaffId()).ifPresent(staff ->
                notificationPublisher.notifyUser(staff.getUserId(), "LEAVE_" + decision.toUpperCase(),
                        "Your leave request was " + decision,
                        "Leave from " + leave.getFromDate() + " to " + leave.getToDate() + " was " + decision + ".",
                        "LeaveRequest", leave.getId()));
    }

    public AttendanceDto.LeaveResponse cancel(String id) {
        return cancel(id, null, true);
    }

    /** Owners may cancel their own request (an approved one only before it starts); managers may cancel any. */
    public AttendanceDto.LeaveResponse cancel(String id, String actorUserId, boolean isManager) {
        LeaveRequest leave = findById(id);
        if (!isManager) {
            boolean mine = staffRepository.findById(leave.getStaffId())
                    .map(st -> st.getUserId().equals(actorUserId)).orElse(false);
            if (!mine) throw new ResourceNotFoundException("Leave request not found");
            if (leave.getStatus() == LeaveStatus.APPROVED && !leave.getFromDate().isAfter(java.time.LocalDate.now())) {
                throw new BusinessException("Approved leave that has already started can only be cancelled by a manager", "LEAVE_STARTED");
            }
        }
        if (leave.getStatus() == LeaveStatus.REJECTED || leave.getStatus() == LeaveStatus.CANCELLED) {
            throw new BusinessException("This leave request is already " + leave.getStatus().name().toLowerCase(), "INVALID_LEAVE_STATE");
        }
        leave.setStatus(LeaveStatus.CANCELLED);
        return toResponse(leaveRepository.save(leave));
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.LeaveResponse> listForStaff(String staffId, String actorUserId, boolean isManager) {
        if (!isManager) {
            boolean mine = staffRepository.findById(staffId).map(st -> st.getUserId().equals(actorUserId)).orElse(false);
            if (!mine) throw new ResourceNotFoundException("Staff member not found");
        }
        return listForStaff(staffId);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.LeaveResponse> listMine(String actorUserId) {
        return staffRepository.findByUserId(actorUserId).map(st -> listForStaff(st.getId())).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.LeaveResponse> listForStaff(String staffId) {
        return leaveRepository.findByStaffIdOrderByFromDateDesc(staffId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.LeaveResponse> listByStatus(String status) {
        LeaveStatus statusEnum = (status != null && !status.isBlank())
                ? LeaveStatus.valueOf(status.toUpperCase()) : LeaveStatus.PENDING;
        return leaveRepository.findByStatusOrderByFromDateAsc(statusEnum).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    private void requirePending(LeaveRequest leave) {
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException("This leave request has already been reviewed", "ALREADY_REVIEWED");
        }
    }

    private void applyReview(LeaveRequest leave, String reviewerUserId, AttendanceDto.LeaveReviewRequest request) {
        leave.setReviewedBy(reviewerUserId);
        User reviewer = reviewerUserId != null ? userRepository.findById(reviewerUserId).orElse(null) : null;
        leave.setReviewedByName(reviewer != null ? reviewer.getFirstName() + " " + reviewer.getLastName() : null);
        leave.setReviewedAt(LocalDateTime.now());
        leave.setReviewNotes(request != null ? request.getNotes() : null);
    }

    private LeaveRequest findById(String id) {
        return leaveRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
    }

    private AttendanceDto.LeaveResponse toResponse(LeaveRequest l) {
        return AttendanceDto.LeaveResponse.builder()
                .id(l.getId())
                .staffId(l.getStaffId())
                .staffName(l.getStaffName())
                .fromDate(l.getFromDate())
                .toDate(l.getToDate())
                .dayCount(ChronoUnit.DAYS.between(l.getFromDate(), l.getToDate()) + 1)
                .reason(l.getReason())
                .status(l.getStatus().name())
                .reviewedByName(l.getReviewedByName())
                .reviewedAt(l.getReviewedAt())
                .reviewNotes(l.getReviewNotes())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
