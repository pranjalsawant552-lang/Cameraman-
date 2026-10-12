package com.cameraman.cms.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cms_slides")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CmsSlide extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(length = 200)
    private String title;

    @Column(length = 400)
    private String subtitle;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "cta_text", length = 100)
    private String ctaText;

    @Column(name = "cta_url", length = 300)
    private String ctaUrl;

    @Column(name = "slide_type", length = 20)
    @Builder.Default
    private String slideType = "IMAGE";

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
