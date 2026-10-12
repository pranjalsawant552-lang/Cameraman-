package com.cameraman.cms.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cms_testimonials")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CmsTestimonial extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @Column(name = "client_photo_url", columnDefinition = "TEXT")
    private String clientPhotoUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String review;

    @Column(nullable = false)
    @Builder.Default
    private int rating = 5;

    @Column(name = "package_type", length = 50)
    private String packageType;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;
}
