package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.entity.SystemSettingsEntity;
import com.cn.zym.note.entity.UserEntity;
import com.cn.zym.note.repository.NoteRepository;
import com.cn.zym.note.repository.SystemSettingsRepository;
import com.cn.zym.note.repository.UserRepository;
import com.cn.zym.note.util.Times;
import com.cn.zym.note.web.dto.Payloads;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminFacadeService {

    private final UserRepository users;
    private final NoteRepository notes;
    private final SystemSettingsRepository settingsRepo;
    private final ObjectMapper objectMapper;

    public Payloads.PlatformStats dashboard() {
        return new Payloads.PlatformStats(
                Long.toString(users.count()),
                Long.toString(notes.count()),
                Long.toString(users.sumAllStorageUsed()));
    }

    @Transactional(readOnly = true)
    public Payloads.AdminUserPage listUsers(String kw, String status, int page, int size) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<UserEntity> pg = users.findAll(p);

        var mapped = pg.getContent().stream()
                .filter(u -> kw == null || kw.isBlank() || u.getPhone().contains(kw.trim()))
                .filter(u -> status == null
                        || status.isBlank()
                        || status.equalsIgnoreCase(u.getStatus()))
                .map(u -> new Payloads.AdminUserRow(
                        Long.toString(u.getId()),
                        maskPhone(u.getPhone()),
                        u.getStatus(),
                        notes.countByUser_IdAndDeletedAtIsNull(u.getId()),
                        Long.toString(u.getStorageUsedBytes()),
                        Times.iso(u.getCreatedAt())))
                .toList();
        return new Payloads.AdminUserPage(mapped, mapped.size());
    }

    /** For accurate paging, filter kw/status via DB specs (future); list small enough for MVP. */

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    @Transactional
    public void setUserStatus(long userId, String statusStr) {
        UserEntity u = users.findById(userId).orElseThrow(() -> nf(HttpStatus.NOT_FOUND, "USER"));
        String up = statusStr.toUpperCase();
        if (!"ACTIVE".equals(up) && !"DISABLED".equals(up)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "BAD_STATUS", "无效状态");
        }
        u.setStatus("DISABLED".equals(up) ? "DISABLED" : "ACTIVE");
        u.setUpdatedAt(LocalDateTime.now());
        users.save(u);
    }

    @Transactional(readOnly = true)
    public Payloads.SystemConfigView getConfig() throws Exception {
        SystemSettingsEntity cfg = settingsRepo.findById(1L).orElseThrow();
        List<String> mimes =
                objectMapper.readValue(cfg.getAllowedImageMimeTypesJson(), new TypeReference<>() {});
        return new Payloads.SystemConfigView(
                Long.toString(cfg.getDefaultUserQuotaBytes()),
                cfg.getMaxImageSizeBytes(),
                mimes,
                cfg.getTrashRetentionDays());
    }

    @Transactional
    public Payloads.SystemConfigView patch(Payloads.SystemConfigPatch p) throws Exception {
        SystemSettingsEntity cfg = settingsRepo.findById(1L).orElseThrow();
        if (p.defaultUserQuotaBytes() != null) {
            cfg.setDefaultUserQuotaBytes(p.defaultUserQuotaBytes());
        }
        if (p.maxImageSizeBytes() != null) {
            cfg.setMaxImageSizeBytes(p.maxImageSizeBytes());
        }
        if (p.allowedImageMimeTypes() != null) {
            cfg.setAllowedImageMimeTypesJson(objectMapper.writeValueAsString(p.allowedImageMimeTypes()));
        }
        if (p.trashRetentionDays() != null) {
            cfg.setTrashRetentionDays(p.trashRetentionDays());
        }
        cfg.setUpdatedAt(LocalDateTime.now());
        settingsRepo.save(cfg);
        return getConfig();
    }

    private static ApiBusinessException nf(HttpStatus st, String c) {
        return new ApiBusinessException(st.value(), c, "");
    }
}
