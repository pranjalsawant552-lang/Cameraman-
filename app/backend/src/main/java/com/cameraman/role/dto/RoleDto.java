package com.cameraman.role.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.List;

public class RoleDto {
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRoleRequest {
        @NotBlank private String name;
        private String description;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRoleRequest {
        private String description;
        private Boolean active;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreatePermissionRequest {
        @NotBlank private String module;
        @NotBlank private String action;
        private String description;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RoleResponse {
        private String id;
        private String name;
        private String description;
        private boolean active;
        private List<PermissionResponse> permissions;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PermissionResponse {
        private String id;
        private String module;
        private String action;
        private String description;
        private boolean active;
    }
}
