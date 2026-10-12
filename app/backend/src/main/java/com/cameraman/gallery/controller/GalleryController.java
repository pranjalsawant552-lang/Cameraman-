package com.cameraman.gallery.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.common.security.CurrentUser;
import com.cameraman.gallery.dto.GalleryDto;
import com.cameraman.gallery.dto.PhotoDto;
import com.cameraman.gallery.service.GalleryService;
import com.cameraman.gallery.service.PhotoUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/galleries")
@RequiredArgsConstructor
@Tag(name = "Gallery", description = "Gallery and photo management")
public class GalleryController {

    private final GalleryService galleryService;
    private final PhotoUploadService photoUploadService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:CREATE')")
    @Operation(summary = "Create gallery")
    public ResponseEntity<ApiResponse<GalleryDto.GalleryResponse>> create(
            @Valid @RequestBody GalleryDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gallery created", galleryService.createGallery(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<GalleryDto.GalleryResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                galleryService.getAllGalleries(search, status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:VIEW')")
    @Operation(summary = "Galleries belonging to a booking (staff)")
    public ResponseEntity<ApiResponse<List<GalleryDto.GalleryResponse>>> getByBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(ApiResponse.success(galleryService.getGalleriesByBooking(bookingId)));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagedResponse<GalleryDto.GalleryResponse>>> getMine(
            @CurrentUser String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                galleryService.getMyGalleries(userId, PageRequest.of(page, size, Sort.by("publishedAt").descending()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:VIEW')")
    @Operation(summary = "Get a single gallery by ID (staff-facing)")
    public ResponseEntity<ApiResponse<GalleryDto.GalleryResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(galleryService.getGalleryById(id)));
    }


    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:GALLERY:UPDATE')")
    @Operation(summary = "Publish gallery and generate access link")
    public ResponseEntity<ApiResponse<GalleryDto.GalleryResponse>> publish(
            @PathVariable String id,
            @Valid @RequestBody GalleryDto.PublishRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Gallery published", galleryService.publishGallery(id, request)));
    }

    @PutMapping("/{id}/originals-expiry")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:GALLERY:UPDATE')")
    @Operation(summary = "Set (or clear, with days=null) how many days after publish unselected full-resolution originals auto-delete")
    public ResponseEntity<ApiResponse<GalleryDto.GalleryResponse>> setOriginalsExpiry(
            @PathVariable String id,
            @RequestBody GalleryDto.SetOriginalsExpiryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Originals expiry updated", galleryService.setOriginalsExpiry(id, request.getDays())));
    }

    @PostMapping("/{id}/originals-expiry/force")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:GALLERY:UPDATE')")
    @Operation(summary = "Immediately delete unselected full-resolution originals for this gallery, regardless of the configured expiry window")
    public ResponseEntity<ApiResponse<GalleryDto.PurgeResult>> forceExpireOriginals(
            @PathVariable String id,
            @RequestBody(required = false) GalleryDto.ForcePurgeRequest request) {
        boolean confirm = request != null && request.isConfirmNoSelection();
        return ResponseEntity.ok(ApiResponse.success("Originals purged", galleryService.forceExpireOriginals(id, confirm)));
    }

    // Client-facing gallery access. Requires login (see SecurityConfig - not
    // in PUBLIC_URLS) and the token must belong to the logged-in client.
    @GetMapping("/public/{token}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Access gallery by token (client-facing)")
    public ResponseEntity<ApiResponse<GalleryDto.GalleryResponse>> getByToken(
            @PathVariable String token,
            @RequestParam(required = false) String password,
            @com.cameraman.common.security.CurrentUser String userId,
            org.springframework.security.core.Authentication authentication) {
        GalleryDto.GalleryResponse gallery = galleryService.getGalleryByToken(token, password);
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_PHOTOGRAPHER")
                        || a.getAuthority().equals("ROLE_EDITOR")
                        || a.getAuthority().equals("CUSTOM:GALLERY:VIEW"));
        if (!isStaff && gallery.getClientId() != null && !galleryService.canClientView(gallery.getId(), userId)) {
            throw new com.cameraman.common.exception.ResourceNotFoundException("Gallery not found");
        }
        return ResponseEntity.ok(ApiResponse.success(gallery));
    }

    @GetMapping("/{id}/photos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get gallery photos with signed URLs")
    public ResponseEntity<ApiResponse<List<PhotoDto.PhotoResponse>>> getPhotos(
            @PathVariable String id,
            @RequestParam(defaultValue = "true") boolean includeUrls,
            @RequestParam(defaultValue = "false") boolean includePending,
            @com.cameraman.common.security.CurrentUser String userId,
            org.springframework.security.core.Authentication authentication) {
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_PHOTOGRAPHER")
                        || a.getAuthority().equals("ROLE_EDITOR")
                        || a.getAuthority().equals("CUSTOM:GALLERY:VIEW"));
        if (!isStaff) {
            if (!galleryService.canClientView(id, userId)) {
                throw new com.cameraman.common.exception.ResourceNotFoundException("Gallery", "id", id);
            }
        }
        // Photos still processing / failed are only ever shown to staff
        return ResponseEntity.ok(ApiResponse.success(galleryService.getGalleryPhotos(id, includeUrls, isStaff && includePending, !isStaff)));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download every photo in a gallery as a single ZIP")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> downloadZip(
            @PathVariable String id,
            @CurrentUser String userId,
            org.springframework.security.core.Authentication authentication) {
        // Same ownership check as GET /{id}/photos — a client can only ZIP
        // their own gallery, staff can ZIP any.
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_PHOTOGRAPHER")
                        || a.getAuthority().equals("ROLE_EDITOR")
                        || a.getAuthority().equals("CUSTOM:GALLERY:VIEW"));
        if (!isStaff) {
            if (!galleryService.canClientView(id, userId)) {
                throw new com.cameraman.common.exception.ResourceNotFoundException("Gallery", "id", id);
            }
        }
        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody body =
                out -> galleryService.streamGalleryZip(id, out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header("Content-Disposition", "attachment; filename=\"gallery-" + id + ".zip\"")
                .body(body);
    }

    @PostMapping(value = "/{id}/photos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:UPLOAD')")
    @Operation(summary = "Upload photos to gallery")
    public ResponseEntity<ApiResponse<List<PhotoDto.PhotoResponse>>> uploadPhotos(
            @PathVariable String id,
            @RequestParam("files") List<MultipartFile> files,
            @CurrentUser String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Photos uploaded", photoUploadService.uploadPhotos(id, files, userId)));
    }

    @PostMapping(value = "/{id}/photos/upload-single", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:UPLOAD')")
    @Operation(summary = "Upload a single photo")
    public ResponseEntity<ApiResponse<PhotoDto.PhotoResponse>> uploadSingle(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String fingerprint) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Photo uploaded", photoUploadService.uploadSinglePhoto(id, file, fingerprint)));
    }

    @PostMapping("/{id}/photos/import-from-drive")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:UPLOAD')")
    @Operation(summary = "Bulk-import every image in a Google Drive folder into this gallery (folder must be shared as 'Anyone with the link can view')")
    public ResponseEntity<ApiResponse<GalleryDto.DriveImportResponse>> importFromDrive(
            @PathVariable String id,
            @Valid @RequestBody GalleryDto.DriveImportRequest request) {
        int queued = photoUploadService.startDriveImport(id, request.getDriveFolderUrl());
        return ResponseEntity.ok(ApiResponse.success(
                queued + " photo(s) found — importing in the background, they'll appear here as they finish processing",
                GalleryDto.DriveImportResponse.builder().filesQueued(queued).build()));
    }

    @GetMapping("/drive-import/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:VIEW')")
    @Operation(summary = "Is Google Drive import configured (API key present)?")
    public ResponseEntity<ApiResponse<GalleryDto.DriveStatus>> driveStatus() {
        return ResponseEntity.ok(ApiResponse.success(
                GalleryDto.DriveStatus.builder().configured(photoUploadService.isDriveConfigured()).build()));
    }

    @GetMapping("/{id}/photos/upload-status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:VIEW')")
    @Operation(summary = "Processing counters and Drive-import progress for a gallery")
    public ResponseEntity<ApiResponse<GalleryDto.UploadStatus>> uploadStatus(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(photoUploadService.getUploadStatus(id)));
    }

    @PostMapping("/{id}/photos/retry-failed")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:UPLOAD')")
    @Operation(summary = "Re-run processing for photos that failed")
    public ResponseEntity<ApiResponse<Integer>> retryFailed(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Retrying failed photos", photoUploadService.retryFailed(id)));
    }

    @PostMapping("/{id}/photos/presigned-url")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:UPLOAD')")
    @Operation(summary = "Start an upload. mode=DIRECT: PUT to uploadUrl (same Content-Type) then confirm-upload. "
            + "mode=CHUNKED: send chunkSize-sized pieces to /photos/{photoId}/chunk. mode=EXISTS: already uploaded, skip.")
    public ResponseEntity<ApiResponse<GalleryDto.PresignedUploadResponse>> getPresignedUrl(
            @PathVariable String id,
            @RequestParam String filename,
            @RequestParam String contentType,
            @RequestParam(required = false) Long size,
            @RequestParam(required = false) String fingerprint) {
        return ResponseEntity.ok(ApiResponse.success(
                photoUploadService.initUpload(id, filename, contentType, size, fingerprint)));
    }

    @PutMapping(value = "/photos/{photoId}/chunk", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:UPLOAD')")
    @Operation(summary = "Upload one chunk of a CHUNKED upload (raw bytes). The last chunk completes the upload.")
    public ResponseEntity<ApiResponse<GalleryDto.ChunkResponse>> uploadChunk(
            @PathVariable String photoId,
            @RequestParam long offset,
            @RequestParam long total,
            @RequestBody byte[] data) {
        return ResponseEntity.ok(ApiResponse.success(photoUploadService.uploadChunk(photoId, offset, total, data)));
    }

    @GetMapping("/photos/{photoId}/upload-progress")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:UPLOAD')")
    @Operation(summary = "How many bytes of a CHUNKED upload storage already has (to resume after an error)")
    public ResponseEntity<ApiResponse<GalleryDto.ChunkResponse>> uploadProgress(@PathVariable String photoId) {
        return ResponseEntity.ok(ApiResponse.success(photoUploadService.getUploadProgress(photoId)));
    }

    @PostMapping("/photos/{photoId}/confirm-upload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:GALLERY:UPLOAD')")
    @Operation(summary = "Confirm a direct upload completed; the server checks the object exists and queues processing")
    public ResponseEntity<ApiResponse<Void>> confirmUpload(
            @PathVariable String photoId,
            @RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height) {
        photoUploadService.confirmUpload(photoId, width, height);
        return ResponseEntity.ok(ApiResponse.success("Upload confirmed"));
    }

    @GetMapping("/photos/{photoId}/download-url")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get signed download URL for a photo (clients only get the clean file once the gallery is unlocked)")
    public ResponseEntity<ApiResponse<String>> getDownloadUrl(
            @PathVariable String photoId,
            @RequestParam(defaultValue = "false") boolean original,
            @CurrentUser String userId,
            org.springframework.security.core.Authentication authentication) {
        boolean isStaff = isStaff(authentication);
        if (!isStaff && !galleryService.canClientView(galleryService.galleryIdOfPhoto(photoId), userId)) {
            throw new com.cameraman.common.exception.ResourceNotFoundException("Photo", "id", photoId);
        }
        return ResponseEntity.ok(ApiResponse.success(galleryService.getSignedPhotoUrl(photoId, !original, isStaff)));
    }

    @PutMapping("/{id}/downloads")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:GALLERY:UPDATE')")
    @Operation(summary = "Manually unlock (clean photos + downloads) or re-lock a gallery, independent of payment")
    public ResponseEntity<ApiResponse<GalleryDto.GalleryResponse>> setDownloads(
            @PathVariable String id,
            @RequestBody GalleryDto.SetDownloadsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                request.isAllowed() ? "Downloads unlocked" : "Downloads locked",
                galleryService.setDownloadsAllowed(id, request.isAllowed())));
    }

    private static boolean isStaff(org.springframework.security.core.Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER") || a.getAuthority().equals("ROLE_PHOTOGRAPHER")
                        || a.getAuthority().equals("ROLE_EDITOR") || a.getAuthority().equals("CUSTOM:GALLERY:VIEW"));
    }

    @GetMapping("/{id}/viewers")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR','RECEPTIONIST') or hasAuthority('CUSTOM:GALLERY:VIEW')")
    @Operation(summary = "Clients who can see this gallery and who takes part in photo selection")
    public ResponseEntity<ApiResponse<List<GalleryDto.ViewerResponse>>> getViewers(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(galleryService.getViewers(id)));
    }

    @PutMapping("/{id}/viewers")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:GALLERY:UPDATE')")
    @Operation(summary = "Choose which clients can see this gallery and which can do the photo selection")
    public ResponseEntity<ApiResponse<List<GalleryDto.ViewerResponse>>> setViewers(
            @PathVariable String id, @Valid @RequestBody GalleryDto.ViewersRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Sharing updated", galleryService.setViewers(id, request)));
    }

    @DeleteMapping("/photos/{photoId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','EDITOR') or hasAuthority('CUSTOM:GALLERY:DELETE')")
    @Operation(summary = "Delete a photo")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(@PathVariable String photoId) {
        galleryService.deletePhoto(photoId);
        return ResponseEntity.ok(ApiResponse.success("Photo deleted"));
    }
}
