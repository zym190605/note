package com.cn.zym.note.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "system_settings")
public class SystemSettingsEntity {

    @Id
    private Long id = 1L;

    @Column(name = "default_user_quota_bytes", nullable = false)
    private Long defaultUserQuotaBytes = 5368709120L;

    @Column(name = "max_image_size_bytes", nullable = false)
    private Integer maxImageSizeBytes = 10485760;

    @Column(name = "allowed_image_mime_types", nullable = false, columnDefinition = "json")
    private String allowedImageMimeTypesJson;

    @Column(name = "trash_retention_days", nullable = false)
    private Integer trashRetentionDays = 90;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
