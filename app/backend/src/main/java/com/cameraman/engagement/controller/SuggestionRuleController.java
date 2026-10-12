package com.cameraman.engagement.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.engagement.dto.EngagementDto;
import com.cameraman.engagement.service.SuggestionRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/suggestion-rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER')")
public class SuggestionRuleController {

    private final SuggestionRuleService ruleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:CREATE')")
    public ResponseEntity<ApiResponse<EngagementDto.RuleResponse>> create(@Valid @RequestBody EngagementDto.RuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Rule created", ruleService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:UPDATE')")
    public ResponseEntity<ApiResponse<EngagementDto.RuleResponse>> update(
            @PathVariable String id, @Valid @RequestBody EngagementDto.RuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Rule updated", ruleService.update(id, request)));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:UPDATE')")
    public ResponseEntity<ApiResponse<Void>> setActive(@PathVariable String id, @RequestParam boolean active) {
        ruleService.setActive(id, active);
        return ResponseEntity.ok(ApiResponse.success("Updated"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:ENGAGEMENT:VIEW')")
    public ResponseEntity<ApiResponse<List<EngagementDto.RuleResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(ruleService.listAll()));
    }
}
