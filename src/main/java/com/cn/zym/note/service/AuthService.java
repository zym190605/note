package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.config.JwtProps;
import com.cn.zym.note.entity.NotebookEntity;
import com.cn.zym.note.entity.RefreshTokenEntity;
import com.cn.zym.note.entity.UserEntity;
import com.cn.zym.note.repository.NotebookRepository;
import com.cn.zym.note.repository.RefreshTokenRepository;
import com.cn.zym.note.repository.SystemSettingsRepository;
import com.cn.zym.note.repository.UserRepository;
import com.cn.zym.note.security.JwtService;
import com.cn.zym.note.util.NotebookNames;
import com.cn.zym.note.web.dto.Payloads;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final NotebookRepository notebooks;
    private final SystemSettingsRepository settings;
    private final JwtService jwt;
    private final JwtProps jwtProps;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshes;

    @Transactional
    public Payloads.AuthResponse register(Payloads.RegisterBody body) {
        if (users.existsByPhone(body.phone())) {
            throw new ApiBusinessException(HttpStatus.CONFLICT.value(), "DUPLICATE_PHONE", "手机号已注册");
        }
        var now = LocalDateTime.now();
        Long quota =
                settings.findById(1L).map(s -> s.getDefaultUserQuotaBytes()).orElse(5368709120L);

        UserEntity u = new UserEntity();
        u.setPhone(body.phone());
        u.setPasswordHash(passwordEncoder.encode(body.password()));
        u.setNickname(body.phone());
        u.setStatus("ACTIVE");
        u.setRole("USER");
        u.setStorageQuotaBytes(quota);
        u.setStorageUsedBytes(0L);
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        u.setUiTheme("SYSTEM");
        users.save(u);

        NotebookEntity nb = new NotebookEntity();
        nb.setUser(u);
        nb.setName(NotebookNames.DEFAULT_NOTEBOOK_CN);
        nb.setDefaultNotebook(true);
        nb.setNoteCount(0);
        nb.setSortOrder(0);
        nb.setCreatedAt(now);
        nb.setUpdatedAt(now);
        notebooks.save(nb);

        return tokensFor(u, false);
    }

    @Transactional
    public Payloads.AuthResponse login(Payloads.LoginBody body) {
        UserEntity u = users.findByPhone(body.phone()).orElseThrow(this::loginFailed);

        if (!passwordEncoder.matches(body.password(), u.getPasswordHash())) {
            throw loginFailed();
        }
        boolean rememberMe = Boolean.TRUE.equals(body.rememberMe());
        return tokensFor(u, rememberMe);
    }

    @Transactional
    public Payloads.TokenPair refresh(Payloads.RefreshBody body) {
        if (body.refreshToken() == null || body.refreshToken().isBlank()) {
            throw new ApiBusinessException(HttpStatus.UNAUTHORIZED.value(), "INVALID_REFRESH", "Refresh 无效");
        }
        String hash = jwt.hashRefresh(body.refreshToken());
        RefreshTokenEntity rt = refreshes
                .findByTokenHashAndRevokedAtIsNull(hash)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.UNAUTHORIZED.value(), "INVALID_REFRESH", "Refresh 无效或已吊销"));
        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiBusinessException(HttpStatus.UNAUTHORIZED.value(), "INVALID_REFRESH", "Refresh 已过期");
        }
        UserEntity u = rt.getUser();
        rt.setRevokedAt(LocalDateTime.now());
        refreshes.save(rt);

        return toTokenPair(tokensFor(u, rt.isRememberMe()));
    }

    @Transactional
    public void logout(Long userId) {
        var tokens = refreshes.findAllByUser_Id(userId);
        var now = LocalDateTime.now();
        for (RefreshTokenEntity rt : tokens) {
            rt.setRevokedAt(now);
        }
        refreshes.saveAll(tokens);
    }

    private Payloads.TokenPair toTokenPair(Payloads.AuthResponse ar) {
        return new Payloads.TokenPair(ar.accessToken(), ar.expiresIn(), ar.refreshToken(), ar.refreshExpiresIn());
    }

    private Payloads.AuthResponse tokensFor(UserEntity u, boolean rememberMe) {
        String access = jwt.accessToken(u);
        int accessSec =
                jwtProps.accessTokenTtlSeconds() != null && jwtProps.accessTokenTtlSeconds() > 0 ? jwtProps.accessTokenTtlSeconds() : 900;

        String rawRefresh = jwt.newOpaqueRefreshValue();
        String hash = jwt.hashRefresh(rawRefresh);
        Instant expiry = jwt.refreshExpiry(rememberMe);
        LocalDateTime expiryLdt = expiry.atZone(ZoneId.systemDefault()).toLocalDateTime();

        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUser(u);
        rt.setJti(java.util.UUID.randomUUID().toString());
        rt.setTokenHash(hash);
        rt.setExpiresAt(expiryLdt);
        rt.setRememberMe(rememberMe);
        rt.setCreatedAt(LocalDateTime.now());
        refreshes.save(rt);

        long refreshSecs = expiry.getEpochSecond() - Instant.now().getEpochSecond();
        refreshSecs = Math.max(refreshSecs, 60);

        return new Payloads.AuthResponse(
                access,
                accessSec,
                rawRefresh,
                (int) refreshSecs,
                Long.toString(u.getId()));
    }

    private ApiBusinessException loginFailed() {
        return new ApiBusinessException(HttpStatus.UNAUTHORIZED.value(), "LOGIN_FAILED", "手机号或密码错误");
    }
}
