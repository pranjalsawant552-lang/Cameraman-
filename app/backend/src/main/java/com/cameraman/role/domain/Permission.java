package com.cameraman.role.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "permissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"module", "action"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Permission extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 60)
    private String module;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
