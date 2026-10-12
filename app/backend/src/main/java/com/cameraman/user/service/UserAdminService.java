package com.cameraman.user.service;

import com.cameraman.audit.service.AuditService;
import com.cameraman.auth.repository.RefreshTokenRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.user.domain.User;
import com.cameraman.user.domain.UserStatus;
import com.cameraman.user.dto.UserDto;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Account-administration actions with the safety rules the plain {@link UserService} does not
 * enforce: nobody can deactivate themselves, the last active SUPER_ADMIN can never be removed or
 * stripped of the role, only a SUPER_ADMIN can grant or touch SUPER_ADMIN, deactivation revokes the
 * person's refresh tokens immediately, and every change is audit-logged.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserAdminService {

    static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserRepository userRepository;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    public UserDto.UserResponse deactivate(String id, String actorId) {
        User target = find(id);
        User actor = find(actorId);
        guardRemoval(target, actor, "deactivate");
        UserDto.UserResponse res = userService.deactivateUser(id);
        refreshTokenRepository.revokeAllUserTokens(id);
        auditService.log("USER_DEACTIVATE", "User", id, target.getStatus().name(), UserStatus.INACTIVE.name(), actorId);
        return res;
    }

    public UserDto.UserResponse activate(String id, String actorId) {
        User target = find(id);
        UserDto.UserResponse res = userService.activateUser(id);
        auditService.log("USER_ACTIVATE", "User", id, target.getStatus().name(), UserStatus.ACTIVE.name(), actorId);
        return res;
    }

    public UserDto.UserResponse update(String id, UserDto.UpdateUserRequest req, String actorId) {
        User target = find(id);
        User actor = find(actorId);
        String oldRoles = roleNames(target).toString();

        boolean leavingActive = req.getStatus() != null
                && target.getStatus() == UserStatus.ACTIVE
                && !UserStatus.ACTIVE.name().equals(req.getStatus());
        if (leavingActive) {
            guardRemoval(target, actor, "deactivate");
        }

        if (req.getRoleNames() != null && !req.getRoleNames().isEmpty()) {
            Set<String> next = new HashSet<>(req.getRoleNames());
            boolean hadSuper = roleNames(target).contains(SUPER_ADMIN);
            boolean willHaveSuper = next.contains(SUPER_ADMIN);
            if (hadSuper && !willHaveSuper) {
                guardRemoval(target, actor, "remove the super admin role from");
            }
            if (!hadSuper && willHaveSuper && !roleNames(actor).contains(SUPER_ADMIN)) {
                throw new BusinessException("Only a super admin can grant the super admin role", "FORBIDDEN_ROLE_GRANT");
            }
            if (target.getId().equals(actor.getId()) && !next.equals(roleNames(target))) {
                throw new BusinessException("You cannot change your own roles", "SELF_ROLE_CHANGE");
            }
        }

        UserDto.UserResponse res = userService.updateUser(id, req);
        if (leavingActive) {
            refreshTokenRepository.revokeAllUserTokens(id);
        }
        auditService.log("USER_UPDATE", "User", id, oldRoles,
                req.getRoleNames() != null ? req.getRoleNames().toString() : oldRoles, actorId);
        return res;
    }

    /** Shared rule for deactivating a user or taking SUPER_ADMIN away from them. */
    void guardRemoval(User target, User actor, String verb) {
        if (target.getId().equals(actor.getId())) {
            throw new BusinessException("You cannot " + verb + " your own account", "SELF_DEACTIVATION");
        }
        if (roleNames(target).contains(SUPER_ADMIN)) {
            if (!roleNames(actor).contains(SUPER_ADMIN)) {
                throw new BusinessException("Only a super admin can " + verb + " a super admin", "FORBIDDEN_SUPER_ADMIN_CHANGE");
            }
            if (target.getStatus() == UserStatus.ACTIVE && userRepository.countActiveByRoleName(SUPER_ADMIN) <= 1) {
                throw new BusinessException("This is the last active super admin and cannot be changed", "LAST_SUPER_ADMIN");
            }
        }
    }

    private Set<String> roleNames(User u) {
        Set<String> names = new HashSet<>();
        u.getRoles().forEach(r -> names.add(r.getName()));
        return names;
    }

    private User find(String id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}
