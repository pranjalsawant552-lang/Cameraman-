package com.cameraman.photoselection.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.common.security.CurrentUser;
import com.cameraman.photoselection.dto.SelectionDto;
import com.cameraman.photoselection.service.PhotoSelectionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Client photo selection portal.
 *
 * Every action here requires the user to be logged in, and the service
 * layer verifies the authenticated user is actually the client the selection
 * belongs to - a selection link/token alone is never sufficient to view or
 * act on someone else's photos.
 */
@RestController
@RequestMapping("/selections")
@RequiredArgsConstructor
@Tag(name = "Photo Selection", description = "Client photo selection portal")
public class PhotoSelectionController {

    private final PhotoSelectionService selectionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR','PHOTOGRAPHER') or hasAuthority('CUSTOM:SELECTION:CREATE')")
    public ResponseEntity<ApiResponse<SelectionDto.SelectionResponse>> create(
            @Valid @RequestBody SelectionDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Selection created", selectionService.createSelection(request)));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SelectionDto.SelectionResponse>>> getMySelections(
            @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success(selectionService.getMySelections(userId)));
    }

    @GetMapping("/token/{token}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SelectionDto.SelectionResponse>> getByToken(
            @PathVariable String token, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success(selectionService.getByToken(token, userId)));
    }

    @PostMapping("/token/{token}/select/{photoId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SelectionDto.SelectionResponse>> selectPhoto(
            @PathVariable String token, @PathVariable String photoId,
            @RequestParam(required = false) String notes, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Photo selected",
                selectionService.selectPhoto(token, photoId, notes, userId)));
    }

    @PutMapping("/token/{token}/draft")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SelectionDto.SelectionResponse>> saveDraft(
            @PathVariable String token, @RequestBody SelectionDto.SaveDraftRequest request, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Draft saved", selectionService.saveDraft(token, request, userId)));
    }

    @DeleteMapping("/token/{token}/deselect/{photoId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SelectionDto.SelectionResponse>> deselectPhoto(
            @PathVariable String token, @PathVariable String photoId, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Photo deselected",
                selectionService.deselectPhoto(token, photoId, userId)));
    }

    @PostMapping("/token/{token}/lock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SelectionDto.SelectionResponse>> lockSelection(
            @PathVariable String token,
            @RequestParam(required = false) String clientNotes, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Selection locked",
                selectionService.lockSelection(token, clientNotes, userId)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:SELECTION:UPDATE')")
    public ResponseEntity<ApiResponse<SelectionDto.SelectionResponse>> approve(@PathVariable String id, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Selection approved", selectionService.approveSelection(id, userId)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:SELECTION:UPDATE')")
    public ResponseEntity<ApiResponse<SelectionDto.SelectionResponse>> reopen(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Selection re-opened", selectionService.reopenSelection(id)));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:SELECTION:UPDATE')")
    public ResponseEntity<ApiResponse<SelectionDto.SelectionResponse>> lockByStaff(
            @PathVariable String id, @RequestParam(required = false) String staffNotes) {
        return ResponseEntity.ok(ApiResponse.success("Selection locked", selectionService.lockSelectionByStaff(id, staffNotes)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR','PHOTOGRAPHER') or hasAuthority('CUSTOM:SELECTION:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<SelectionDto.SelectionResponse>>> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                selectionService.getAll(status, PageRequest.of(page, size))));
    }

    @GetMapping("/{id}/photos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SelectionDto.SelectedPhotoResponse>>> getSelectedPhotos(
            @PathVariable String id, @CurrentUser String userId, Authentication authentication) {
        boolean isStaff = authentication.getAuthorities().stream().anyMatch(a -> {
            String auth = a.getAuthority();
            return auth.equals("ROLE_ADMIN") || auth.equals("ROLE_SUPER_ADMIN")
                    || auth.equals("ROLE_MANAGER") || auth.equals("ROLE_EDITOR") || auth.equals("ROLE_PHOTOGRAPHER")
                    || auth.equals("CUSTOM:SELECTION:VIEW");
        });
        return ResponseEntity.ok(ApiResponse.success(selectionService.getSelectedPhotos(id, userId, isStaff)));
    }
}
