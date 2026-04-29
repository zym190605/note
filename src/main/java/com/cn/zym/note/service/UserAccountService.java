package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.entity.UserEntity;
import com.cn.zym.note.repository.NoteRepository;
import com.cn.zym.note.repository.UserRepository;
import com.cn.zym.note.web.dto.Payloads;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository users;
    private final NoteRepository notes;
    private final PasswordEncoder passwordEncoder;

    public Payloads.UserProfile profile(Long userId) {
        UserEntity u = users.findById(userId).orElseThrow(() -> notFound());
        return new Payloads.UserProfile(
                Long.toString(u.getId()),
                maskPhone(u.getPhone()),
                u.getNickname(),
                u.getAvatarUrl(),
                com.cn.zym.note.util.Times.iso(u.getCreatedAt()),
                u.getUiTheme() != null ? u.getUiTheme() : "SYSTEM");
    }

    public Payloads.UserStats stats(Long userId) {
        UserEntity u = users.findById(userId).orElseThrow(() -> notFound());
        long total = notes.countByUser_IdAndDeletedAtIsNull(userId);
        return new Payloads.UserStats(
                Long.toString(total),
                Long.toString(u.getStorageUsedBytes()),
                Long.toString(u.getStorageQuotaBytes()));
    }

    @Transactional
    public void changePassword(Long userId, Payloads.ChangePasswordBody body) {
        UserEntity u = users.findById(userId).orElseThrow(() -> notFound());
        if (!passwordEncoder.matches(body.oldPassword(), u.getPasswordHash())) {
            throw new ApiBusinessException(HttpStatus.UNAUTHORIZED.value(), "BAD_PASSWORD", "原密码错误");
        }
        u.setPasswordHash(passwordEncoder.encode(body.newPassword()));
        users.save(u);
    }

    @Transactional
    public Payloads.UserProfile patchMe(Long userId, Payloads.PatchMeBody body) {
        UserEntity u = users.findById(userId).orElseThrow(UserAccountService::notFound);
        if (body.uiTheme() != null && !body.uiTheme().isBlank()) {
            String v = body.uiTheme().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("LIGHT", "DARK", "SYSTEM").contains(v)) {
                throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "BAD_THEME", "uiTheme 仅支持 LIGHT | DARK | SYSTEM");
            }
            u.setUiTheme(v);
        }
        users.save(u);
        return profile(userId);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static ApiBusinessException notFound() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "用户不存在");
    }
}
