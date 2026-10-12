package com.cameraman.album.controller;

import com.cameraman.album.dto.AlbumDto;
import com.cameraman.album.service.AlbumService;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/albums")
@RequiredArgsConstructor
@Tag(name = "Albums", description = "Album management and workflow")
public class AlbumController {

    private final AlbumService albumService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:ALBUM:CREATE')")
    public ResponseEntity<ApiResponse<AlbumDto.AlbumResponse>> create(@Valid @RequestBody AlbumDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Album created", albumService.createAlbum(req)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:ALBUM:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<AlbumDto.AlbumResponse>>> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(albumService.getAll(status, PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AlbumDto.AlbumResponse>> getById(
            @PathVariable String id, @CurrentUser String userId, Authentication authentication) {
        AlbumDto.AlbumResponse album = albumService.getById(id);
        requireStaffOrOwner(album.getClientId(), userId, authentication, id);
        return ResponseEntity.ok(ApiResponse.success(album));
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AlbumDto.AlbumResponse>>> getByBooking(
            @PathVariable String bookingId, @CurrentUser String userId, Authentication authentication) {
        List<AlbumDto.AlbumResponse> albums = albumService.getByBooking(bookingId);
        boolean isStaff = isStaff(authentication);
        if (!isStaff) {
            // Filter to only the requester's own albums rather than 404ing the whole
            // list — a booking can only have one client anyway, but this keeps the
            // check self-contained and fails closed if that ever changes.
            albums = albums.stream().filter(a -> userId.equals(a.getClientId())).toList();
        }
        return ResponseEntity.ok(ApiResponse.success(albums));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:ALBUM:UPDATE')")
    public ResponseEntity<ApiResponse<AlbumDto.AlbumResponse>> update(
            @PathVariable String id, @Valid @RequestBody AlbumDto.UpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Album updated", albumService.updateAlbum(id, req)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:ALBUM:UPDATE')")
    public ResponseEntity<ApiResponse<AlbumDto.AlbumResponse>> updateStatus(
            @PathVariable String id, @RequestParam String status,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(ApiResponse.success("Status updated", albumService.updateStatus(id, status, notes)));
    }

    @PostMapping("/{id}/revision")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AlbumDto.AlbumResponse>> requestRevision(
            @PathVariable String id, @RequestParam String notes,
            @CurrentUser String userId, Authentication authentication) {
        AlbumDto.AlbumResponse album = albumService.getById(id);
        requireStaffOrOwner(album.getClientId(), userId, authentication, id);
        return ResponseEntity.ok(ApiResponse.success("Revision requested", albumService.requestRevision(id, notes)));
    }

    @GetMapping("/{id}/photos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AlbumDto.AlbumPhotoResponse>>> getPhotos(
            @PathVariable String id, @CurrentUser String userId, Authentication authentication) {
        requireStaffOrOwner(albumService.getById(id).getClientId(), userId, authentication, id);
        return ResponseEntity.ok(ApiResponse.success(albumService.getPhotos(id)));
    }

    @PutMapping("/{id}/photos")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:ALBUM:UPDATE')")
    public ResponseEntity<ApiResponse<List<AlbumDto.AlbumPhotoResponse>>> setPhotos(
            @PathVariable String id, @Valid @RequestBody AlbumDto.SetPhotosRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Album photos saved", albumService.setPhotos(id, req.getPhotoIds())));
    }

    @PostMapping("/{id}/photos/from-selection/{selectionId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:ALBUM:UPDATE')")
    public ResponseEntity<ApiResponse<List<AlbumDto.AlbumPhotoResponse>>> addFromSelection(
            @PathVariable String id, @PathVariable String selectionId) {
        return ResponseEntity.ok(ApiResponse.success("Selected photos added", albumService.addFromSelection(id, selectionId)));
    }

    @PatchMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:ALBUM:UPDATE')")
    public ResponseEntity<ApiResponse<AlbumDto.AlbumPhotoResponse>> updatePhoto(
            @PathVariable String id, @PathVariable String photoId, @Valid @RequestBody AlbumDto.UpdatePhotoRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Photo updated", albumService.updatePhoto(id, photoId, req)));
    }

    @PutMapping("/{id}/cover")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:ALBUM:UPDATE')")
    public ResponseEntity<ApiResponse<AlbumDto.AlbumResponse>> setCover(
            @PathVariable String id, @RequestParam(required = false) String photoId) {
        return ResponseEntity.ok(ApiResponse.success("Cover updated", albumService.setCover(id, photoId)));
    }

    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_EDITOR")
                        || a.getAuthority().equals("CUSTOM:ALBUM:VIEW"));
    }

    private void requireStaffOrOwner(String clientId, String userId, Authentication authentication, String albumId) {
        if (!isStaff(authentication) && !userId.equals(clientId)) {
            throw new ResourceNotFoundException("Album", "id", albumId);
        }
    }
}
