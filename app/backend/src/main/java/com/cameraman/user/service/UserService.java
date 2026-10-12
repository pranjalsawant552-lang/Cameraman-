package com.cameraman.user.service;

import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.billing.repository.InvoiceRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.DuplicateResourceException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.role.domain.Role;
import com.cameraman.role.repository.RoleRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.domain.UserStatus;
import com.cameraman.user.dto.UserDto;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BookingRepository bookingRepository;
    private final InvoiceRepository invoiceRepository;

    public UserDto.UserResponse createUser(UserDto.CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (request.getMobile() != null && userRepository.existsByMobile(request.getMobile())) {
            throw new DuplicateResourceException("User", "mobile", request.getMobile());
        }

        Set<Role> roles = new HashSet<>();
        if (request.getRoleNames() != null && !request.getRoleNames().isEmpty()) {
            for (String roleName : request.getRoleNames()) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
                roles.add(role);
            }
        } else {
            roleRepository.findByName("CLIENT").ifPresent(roles::add);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword() != null
                        ? request.getPassword() : "TempPass@" + System.currentTimeMillis()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .mobile(request.getMobile())
                .address(request.getAddress())
                .notes(request.getNotes())
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(roles)
                .build();

        return toResponse(userRepository.save(user));
    }

    public UserDto.UserResponse updateUser(String id, UserDto.UpdateUserRequest request) {
        User user = findById(id);

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getMobile() != null) {
            if (!request.getMobile().equals(user.getMobile()) && userRepository.existsByMobile(request.getMobile())) {
                throw new DuplicateResourceException("User", "mobile", request.getMobile());
            }
            user.setMobile(request.getMobile());
        }
        if (request.getAddress() != null) user.setAddress(request.getAddress());
        if (request.getNotes() != null) user.setNotes(request.getNotes());
        if (request.getStatus() != null) user.setStatus(UserStatus.valueOf(request.getStatus()));

        if (request.getRoleNames() != null && !request.getRoleNames().isEmpty()) {
            Set<Role> roles = request.getRoleNames().stream()
                    .map(rn -> roleRepository.findByName(rn)
                            .orElseThrow(() -> new ResourceNotFoundException("Role", "name", rn)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        return toResponse(userRepository.save(user));
    }

    public UserDto.UserResponse activateUser(String id) {
        User user = findById(id);
        user.setStatus(UserStatus.ACTIVE);
        return toResponse(userRepository.save(user));
    }

    public UserDto.UserResponse deactivateUser(String id) {
        User user = findById(id);
        user.setStatus(UserStatus.INACTIVE);
        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserDto.UserResponse getUserById(String id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserDto.UserResponse> getAllUsers(String search, String status,
                                                            String role, Pageable pageable) {
        Page<User> page;
        if (search != null && !search.isBlank() && role != null && !role.isBlank()) {
            page = userRepository.searchUsersByRole(search, role, pageable);
        } else if (search != null && !search.isBlank()) {
            page = userRepository.searchUsers(search, pageable);
        } else if (role != null && !role.isBlank()) {
            page = userRepository.findByRoleName(role, pageable);
        } else if (status != null && !status.isBlank()) {
            page = userRepository.findByStatus(UserStatus.valueOf(status), pageable);
        } else {
            page = userRepository.findAll(pageable);
        }
        return PagedResponse.of(page.map(this::toResponse));
    }

    /** Narrow, purpose-built alternative to getAllUsers() for the "search existing clients"
     *  picker used on the billing and gallery forms — always forces role=CLIENT server-side
     *  (a caller can't widen it to search staff by passing a different role) so it's safe
     *  to expose to Photographer/Editor, who shouldn't see the full staff directory that
     *  GET /users returns. */
    @Transactional(readOnly = true)
    public PagedResponse<UserDto.UserResponse> searchClients(String search, Pageable pageable) {
        Page<User> page = (search != null && !search.isBlank())
                ? userRepository.searchUsersByRole(search, "CLIENT", pageable)
                : userRepository.findByRoleName("CLIENT", pageable);

        // Previously every client always showed 0 bookings / 0 spend on the
        // frontend — there was no aggregate query at all. Batched into two
        // grouped queries for the whole page (not one query per client) so
        // this stays cheap regardless of page size.
        List<String> clientIds = page.getContent().stream().map(User::getId).collect(Collectors.toList());
        Map<String, Long> bookingCounts = new HashMap<>();
        Map<String, BigDecimal> totalSpend = new HashMap<>();
        if (!clientIds.isEmpty()) {
            for (Object[] row : bookingRepository.countByClientIdIn(clientIds)) {
                bookingCounts.put((String) row[0], (Long) row[1]);
            }
            for (Object[] row : invoiceRepository.sumAmountPaidByClientIdIn(clientIds)) {
                totalSpend.put((String) row[0], (BigDecimal) row[1]);
            }
        }

        return PagedResponse.of(page.map(u -> toResponse(u).toBuilder()
                .totalBookings(bookingCounts.getOrDefault(u.getId(), 0L))
                .totalSpend(totalSpend.getOrDefault(u.getId(), BigDecimal.ZERO))
                .build()));
    }

    @Transactional(readOnly = true)
    public UserDto.UserProfile getProfile(String userId) {
        User user = findById(userId);
        return UserDto.UserProfile.builder()
                .id(user.getId()).email(user.getEmail()).mobile(user.getMobile())
                .firstName(user.getFirstName()).lastName(user.getLastName())
                .profilePhotoUrl(user.getProfilePhotoUrl()).status(user.getStatus().name())
                .emailVerified(user.isEmailVerified()).mobileVerified(user.isMobileVerified())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .permissions(user.getCustomPermissionCodes())
                .createdAt(user.getCreatedAt())
                .marketingOptIn(user.isMarketingOptIn())
                .build();
    }

    public UserDto.UserProfile updateProfile(String userId, UserDto.UpdateProfileRequest request) {
        User user = findById(userId);
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getMobile() != null) user.setMobile(request.getMobile());
        if (request.getAddress() != null) user.setAddress(request.getAddress());
        if (request.getProfilePhotoUrl() != null) user.setProfilePhotoUrl(request.getProfilePhotoUrl());
        return getProfile(userRepository.save(user).getId());
    }

    /** Account-wide opt in/out of re-engagement suggestions (birthday reminders,
     *  anniversary shoot suggestions, review requests, etc). Checked by
     *  SuggestionEngineScheduler before generating or auto-sending any suggestion
     *  for this client. */
    public UserDto.UserProfile updateMarketingPreference(String userId, boolean optIn) {
        User user = findById(userId);
        user.setMarketingOptIn(optIn);
        return getProfile(userRepository.save(user).getId());
    }

    private User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private UserDto.UserResponse toResponse(User u) {
        return UserDto.UserResponse.builder()
                .id(u.getId()).email(u.getEmail()).mobile(u.getMobile())
                .firstName(u.getFirstName()).lastName(u.getLastName())
                .profilePhotoUrl(u.getProfilePhotoUrl()).status(u.getStatus().name())
                .emailVerified(u.isEmailVerified()).mobileVerified(u.isMobileVerified())
                .roles(u.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .createdAt(u.getCreatedAt()).updatedAt(u.getUpdatedAt())
                .build();
    }
}
