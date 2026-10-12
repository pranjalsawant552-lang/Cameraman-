package com.cameraman.user.service;

import com.cameraman.common.response.PagedResponse;
import com.cameraman.user.repository.UserRepository;
import com.cameraman.role.repository.RoleRepository;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.billing.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * getAllUsers() previously ignored the role filter entirely whenever a search term was
 * also supplied — searchUsers(search, pageable) never looked at role at all. That meant
 * every "search existing clients" box in the app (billing, galleries, selections,
 * bookings, studio sessions, outdoor shoots) could just as easily surface a staff
 * member's account as a client's. These tests lock in the fix: role is now honored
 * whenever both search and role are present.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceSearchTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    // Added when searchClients() started returning real per-client booking-count/
    // total-spend aggregates instead of hardcoded zeros — UserService now depends on
    // both repositories to compute them. Unused by the tests below (they all return
    // an empty page, so the aggregate lookups are short-circuited), but still required
    // for UserService's constructor to resolve at all.
    @Mock private BookingRepository bookingRepository;
    @Mock private InvoiceRepository invoiceRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository, passwordEncoder, bookingRepository, invoiceRepository);
    }

    @Test
    void getAllUsers_searchWithRole_usesRoleAwareQuery_notPlainSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.searchUsersByRole(eq("priya"), eq("CLIENT"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getAllUsers("priya", null, "CLIENT", pageable);

        // The bug, concretely: this must be the role-aware query, and the plain
        // (role-blind) searchUsers() must never be called when a role was specified.
        verify(userRepository).searchUsersByRole("priya", "CLIENT", pageable);
        verify(userRepository, never()).searchUsers(any(), any());
    }

    @Test
    void getAllUsers_searchWithoutRole_stillUsesPlainSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.searchUsers(eq("priya"), any())).thenReturn(new PageImpl<>(List.of()));

        service.getAllUsers("priya", null, null, pageable);

        verify(userRepository).searchUsers("priya", pageable);
        verify(userRepository, never()).searchUsersByRole(any(), any(), any());
    }

    @Test
    void searchClients_alwaysForcesClientRole_regardlessOfCaller() {
        Pageable pageable = PageRequest.of(0, 8);
        when(userRepository.searchUsersByRole(eq("rahul"), eq("CLIENT"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        PagedResponse<?> result = service.searchClients("rahul", pageable);

        verify(userRepository).searchUsersByRole("rahul", "CLIENT", pageable);
        // No path in searchClients() ever calls the unfiltered/staff-visible queries.
        verify(userRepository, never()).findAll(any(Pageable.class));
        verify(userRepository, never()).searchUsers(any(), any());
        assertThat(result).isNotNull();
    }

    @Test
    void searchClients_blankSearch_fallsBackToRoleOnlyListing_stillClientScoped() {
        Pageable pageable = PageRequest.of(0, 8);
        when(userRepository.findByRoleName(eq("CLIENT"), any())).thenReturn(new PageImpl<>(List.of()));

        service.searchClients("  ", pageable);

        verify(userRepository).findByRoleName("CLIENT", pageable);
        verify(userRepository, never()).searchUsersByRole(any(), any(), any());
    }
}
