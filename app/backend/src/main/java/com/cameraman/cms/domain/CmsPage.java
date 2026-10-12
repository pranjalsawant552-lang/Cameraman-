package com.cameraman.cms.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cms_pages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CmsPage extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "page_key", nullable = false, unique = true, length = 50)
    private String pageKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_published")
    @Builder.Default
    private boolean published = false;

    @Column(name = "meta_title", length = 200)
    private String metaTitle;

    @Column(name = "meta_description", length = 500)
    private String metaDescription;
}
