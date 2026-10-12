package com.cameraman.package_mgmt.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "package_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PackageItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private Package pkg;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;
}
