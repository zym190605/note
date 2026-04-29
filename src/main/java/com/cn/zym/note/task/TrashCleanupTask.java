package com.cn.zym.note.task;

import com.cn.zym.note.repository.NoteRepository;
import com.cn.zym.note.repository.SystemSettingsRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrashCleanupTask {

    private final NoteRepository notes;
    private final SystemSettingsRepository settings;

    @Scheduled(cron = "${note.trash.cron:0 0 4 * * ?}")
    public void purge() {
        int days =
                settings.findById(1L).map(s -> s.getTrashRetentionDays()).orElse(90);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        notes.purgeTrashOlderThan(cutoff);
    }
}
