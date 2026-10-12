package com.cameraman.staff.service;

import com.cameraman.booking.domain.BookingStatus;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.common.exception.DuplicateResourceException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.dto.StaffDto;
import com.cameraman.staff.repository.StaffRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StaffService {

    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public StaffDto.StaffResponse createStaff(StaffDto.CreateStaffRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        if (staffRepository.existsByUserId(user.getId())) {
            throw new DuplicateResourceException("Staff profile", "user", user.getId());
        }

        assertEmployeeIdFree(request.getEmployeeId(), null);

        StaffProfile profile = StaffProfile.builder()
                .userId(user.getId())
                .employeeId(request.getEmployeeId())
                .department(request.getDepartment())
                .designation(request.getDesignation())
                .joiningDate(request.getJoiningDate())
                .specializations(request.getSpecializations())
                .bio(request.getBio())
                .portfolioUrl(request.getPortfolioUrl())
                .instagramUrl(request.getInstagramUrl())
                .availableForBooking(request.getAvailableForBooking() == null || request.getAvailableForBooking())
                .maxBookingsPerDay(request.getMaxBookingsPerDay() != null ? request.getMaxBookingsPerDay() : 2)
                .build();

        profile = staffRepository.save(profile);
        return toResponse(profile, user);
    }

    @Transactional(readOnly = true)
    public PagedResponse<StaffDto.StaffResponse> getAllStaff(String department, Boolean availableOnly, Pageable pageable) {
        Page<StaffProfile> page;
        if (Boolean.TRUE.equals(availableOnly)) {
            List<StaffProfile> available = staffRepository.findByAvailableForBookingTrue();
            page = new org.springframework.data.domain.PageImpl<>(available, pageable, available.size());
        } else {
            page = staffRepository.findAll(pageable);
        }

        List<StaffDto.StaffResponse> content = page.getContent().stream()
                .filter(p -> department == null || department.equalsIgnoreCase(p.getDepartment()))
                .map(p -> toResponse(p, userRepository.findById(p.getUserId()).orElse(null)))
                .collect(Collectors.toList());

        return PagedResponse.<StaffDto.StaffResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .build();
    }

    @Transactional(readOnly = true)
    public StaffDto.StaffResponse getById(String id) {
        StaffProfile profile = findById(id);
        User user = userRepository.findById(profile.getUserId()).orElse(null);
        return toResponse(profile, user);
    }

    /** Self-service lookup — any authenticated staff member can find their own staff
     *  record (e.g. to apply for leave) without needing the broader staff-directory
     *  permission that GET /staff and GET /staff/{id} require. */
    @Transactional(readOnly = true)
    public StaffDto.StaffResponse getByUserId(String userId) {
        StaffProfile profile = staffRepository.findByUserId(userId)
                .orElseThrow(() -> new com.cameraman.common.exception.ResourceNotFoundException(
                        "No staff profile is linked to this account"));
        User user = userRepository.findById(profile.getUserId()).orElse(null);
        return toResponse(profile, user);
    }

    @Transactional(readOnly = true)
    public List<StaffDto.StaffResponse> getAvailableStaff() {
        return staffRepository.findByAvailableForBookingTrue().stream()
                .map(p -> toResponse(p, userRepository.findById(p.getUserId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public StaffDto.StaffResponse updateStaff(String id, StaffDto.UpdateStaffRequest request) {
        StaffProfile profile = findById(id);

        if (request.getEmployeeId() != null) {
            String eid = request.getEmployeeId().trim();
            assertEmployeeIdFree(eid, profile.getId());
            profile.setEmployeeId(eid.isEmpty() ? null : eid);
        }
        if (request.getDepartment() != null) profile.setDepartment(request.getDepartment());
        if (request.getDesignation() != null) profile.setDesignation(request.getDesignation());
        if (request.getJoiningDate() != null) profile.setJoiningDate(request.getJoiningDate());
        if (request.getSpecializations() != null) profile.setSpecializations(request.getSpecializations());
        if (request.getBio() != null) profile.setBio(request.getBio());
        if (request.getPortfolioUrl() != null) profile.setPortfolioUrl(request.getPortfolioUrl());
        if (request.getInstagramUrl() != null) profile.setInstagramUrl(request.getInstagramUrl());
        if (request.getAvailableForBooking() != null) profile.setAvailableForBooking(request.getAvailableForBooking());
        if (request.getMaxBookingsPerDay() != null) profile.setMaxBookingsPerDay(request.getMaxBookingsPerDay());

        profile = staffRepository.save(profile);
        User user = userRepository.findById(profile.getUserId()).orElse(null);
        return toResponse(profile, user);
    }

    public StaffDto.StaffResponse toggleAvailability(String id) {
        StaffProfile profile = findById(id);
        profile.setAvailableForBooking(!profile.isAvailableForBooking());
        profile = staffRepository.save(profile);
        User user = userRepository.findById(profile.getUserId()).orElse(null);
        return toResponse(profile, user);
    }

    public void deleteStaff(String id) {
        StaffProfile profile = findById(id);
        staffRepository.delete(profile);
    }

    /** The employee id is what attendance machines punch with, so two staff must never share one. */
    void assertEmployeeIdFree(String employeeId, String selfProfileId) {
        if (employeeId == null || employeeId.isBlank()) return;
        staffRepository.findByEmployeeId(employeeId.trim()).ifPresent(other -> {
            if (!other.getId().equals(selfProfileId)) {
                throw new DuplicateResourceException("Staff profile", "employeeId", employeeId.trim());
            }
        });
    }

    private StaffProfile findById(String id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff profile", "id", id));
    }

    private StaffDto.StaffResponse toResponse(StaffProfile profile, User user) {
        long assigned = bookingRepository.countByPhotographerId(profile.getUserId());
        long completed = bookingRepository.countByPhotographerIdAndStatus(profile.getUserId(), BookingStatus.COMPLETED);

        StaffDto.StaffResponse.StaffResponseBuilder builder = StaffDto.StaffResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .employeeId(profile.getEmployeeId())
                .department(profile.getDepartment())
                .designation(profile.getDesignation())
                .joiningDate(profile.getJoiningDate())
                .specializations(profile.getSpecializations())
                .bio(profile.getBio())
                .portfolioUrl(profile.getPortfolioUrl())
                .instagramUrl(profile.getInstagramUrl())
                .availableForBooking(profile.isAvailableForBooking())
                .maxBookingsPerDay(profile.getMaxBookingsPerDay())
                .assignedTasksCount((int) assigned)
                .completedTasksCount((int) completed);

        if (user != null) {
            builder.userStatus(user.getStatus() != null ? user.getStatus().name() : null)
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .mobile(user.getMobile())
                    .profilePhotoUrl(user.getProfilePhotoUrl())
                    .roles(user.getRoles().stream().map(com.cameraman.role.domain.Role::getName).collect(Collectors.toList()));
        }

        return builder.build();
    }
}
