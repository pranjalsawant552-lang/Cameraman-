package com.cameraman.engagement.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.security.CurrentUser;
import org.springframework.security.core.Authentication;
import com.cameraman.engagement.dto.EngagementDto;
import com.cameraman.engagement.service.IntakeQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/intake-questions")
@RequiredArgsConstructor
public class IntakeQuestionController {

    private final IntakeQuestionService questionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:INTAKE:CREATE')")
    public ResponseEntity<ApiResponse<EngagementDto.QuestionResponse>> create(@Valid @RequestBody EngagementDto.QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question created", questionService.createQuestion(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:INTAKE:UPDATE')")
    public ResponseEntity<ApiResponse<EngagementDto.QuestionResponse>> update(
            @PathVariable String id, @Valid @RequestBody EngagementDto.QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question updated", questionService.updateQuestion(id, request)));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:INTAKE:UPDATE')")
    public ResponseEntity<ApiResponse<Void>> setActive(@PathVariable String id, @RequestParam boolean active) {
        questionService.setActive(id, active);
        return ResponseEntity.ok(ApiResponse.success("Updated"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:INTAKE:VIEW')")
    public ResponseEntity<ApiResponse<List<EngagementDto.QuestionResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(questionService.listAll()));
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<EngagementDto.QuestionResponse>>> listForCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.success(questionService.listForCategory(category)));
    }

    private static final java.util.Set<String> STAFF_ROLES = java.util.Set.of(
            "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST", "ROLE_PHOTOGRAPHER", "ROLE_EDITOR");

    private boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> STAFF_ROLES.contains(a.getAuthority())
                || a.getAuthority().equals("CUSTOM:INTAKE:VIEW"));
    }

    // Staff may answer for any booking; a client may answer only for their own
    // (ownership is enforced in the service from the JWT user id, never from the body).
    @PostMapping("/bookings/{bookingId}/answers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> submitAnswers(@PathVariable String bookingId,
            @RequestBody EngagementDto.SubmitAnswersRequest request,
            @CurrentUser String userId, Authentication authentication) {
        questionService.submitAnswers(bookingId, request.getAnswers(), userId, isStaff(authentication));
        return ResponseEntity.ok(ApiResponse.success("Answers saved"));
    }

    @GetMapping("/bookings/{bookingId}/answers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAnswers(@PathVariable String bookingId,
            @CurrentUser String userId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(questionService.getAnswers(bookingId, userId, isStaff(authentication))));
    }
}
