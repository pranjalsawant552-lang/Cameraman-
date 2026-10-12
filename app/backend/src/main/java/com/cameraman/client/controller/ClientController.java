package com.cameraman.client.controller;

import com.cameraman.client.dto.ClientDto;
import com.cameraman.client.service.ClientService;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Admin view of client accounts")
public class ClientController {

    private final ClientService clientService;

    @GetMapping("/{id}/overview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','ACCOUNTANT') or hasAuthority('CUSTOM:BOOKING:VIEW')")
    public ResponseEntity<ApiResponse<ClientDto.Overview>> overview(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(clientService.getOverview(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    public ResponseEntity<ApiResponse<ClientDto.Overview>> update(
            @PathVariable String id, @Valid @RequestBody ClientDto.UpdateRequest request, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Client updated", clientService.update(id, request, userId)));
    }
}
