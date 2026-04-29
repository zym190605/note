package com.cn.zym.note.bootstrap;

import com.cn.zym.note.entity.SystemSettingsEntity;
import com.cn.zym.note.repository.SystemSettingsRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class SystemSettingsBootstrap implements ApplicationRunner {

    private final SystemSettingsRepository repo;

    @Override
    public void run(ApplicationArguments args) {
        if (repo.findById(1L).isEmpty()) {
            SystemSettingsEntity s = new SystemSettingsEntity();
            s.setId(1L);
            s.setDefaultUserQuotaBytes(5368709120L);
            s.setMaxImageSizeBytes(10485760);
            s.setAllowedImageMimeTypesJson("[\"image/jpeg\",\"image/png\",\"image/gif\"]");
            s.setTrashRetentionDays(90);
            s.setUpdatedAt(LocalDateTime.now());
            repo.save(s);
        }
    }
}
