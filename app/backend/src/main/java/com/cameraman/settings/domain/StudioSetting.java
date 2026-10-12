package com.cameraman.settings.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "studio_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudioSetting extends BaseAuditEntity {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "setting_type", length = 30)
    @Builder.Default
    private String type = "STRING"; // STRING, JSON, BOOLEAN, NUMBER, HTML

    @Column(name = "category", length = 60)
    private String category;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "is_public")
    @Builder.Default
    private boolean isPublic = false;

    @Column(name = "is_editable")
    @Builder.Default
    private boolean isEditable = true;
}
