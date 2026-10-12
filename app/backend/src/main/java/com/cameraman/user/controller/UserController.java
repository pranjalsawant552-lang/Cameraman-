package com.cameraman.user.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.common.security.CurrentUser;
import com.cameraman.user.dto.UserDto;
import com.cameraman.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management")
public class UserController {

    private final UserService userService;
    private final com.cameraman.user.service.UserAdminService userAdminService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> create(@Valid @RequestBody UserDto.CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User created", userService.createUser(req)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<PagedResponse<UserDto.UserResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getAllUsers(search, status, role, PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/clients/search")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<PagedResponse<UserDto.UserResponse>>> searchClients(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.searchClients(search, PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or #id == authentication.principal.id")
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> update(
            @PathVariable String id, @Valid @RequestBody UserDto.UpdateUserRequest req,
            @CurrentUser String actorId) {
        return ResponseEntity.ok(ApiResponse.success("User updated", userAdminService.update(id, req, actorId)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> activate(@PathVariable String id, @CurrentUser String actorId) {
        return ResponseEntity.ok(ApiResponse.success("User activated", userAdminService.activate(id, actorId)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> deactivate(@PathVariable String id, @CurrentUser String actorId) {
        return ResponseEntity.ok(ApiResponse.success("User deactivated", userAdminService.deactivate(id, actorId)));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto.UserProfile>> getProfile(@CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(userId)));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto.UserProfile>> updateProfile(
            @CurrentUser String userId, @RequestBody UserDto.UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated", userService.updateProfile(userId, req)));
    }

    @PutMapping("/me/marketing-preference")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto.UserProfile>> updateMarketingPreference(
            @CurrentUser String userId, @RequestParam boolean optIn) {
        return ResponseEntity.ok(ApiResponse.success(
                optIn ? "You'll now receive reminders and suggestions" : "You've been opted out of reminders and suggestions",
                userService.updateMarketingPreference(userId, optIn)));
    }
}
