package com.cameraman.role.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Role extends BaseAuditEntity {

    /** Roles that ship with the app. Their endpoint access is fixed by role name in the
     *  controllers; only roles NOT in this set (custom roles) gain access through the
     *  permissions assigned to them, as {@code CUSTOM:MODULE:ACTION} authorities. */
    public static final java.util.Set<String> BUILT_IN = java.util.Set.of(
            "SUPER_ADMIN", "ADMIN", "MANAGER", "RECEPTIONIST", "PHOTOGRAPHER", "EDITOR",
            "ACCOUNTANT", "INVENTORY_MANAGER", "CLIENT");

    public boolean isBuiltIn() {
        return name != null && BUILT_IN.contains(name);
    }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
}
