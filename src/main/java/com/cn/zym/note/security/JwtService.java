package com.cn.zym.note.security;

import com.cn.zym.note.config.JwtProps;
import com.cn.zym.note.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtProps jwtProps;

    public String accessToken(UserEntity user) {
        Instant now = Instant.now();
        int ttl = ttl(jwtProps.accessTokenTtlSeconds(), 900);
        SecretKey key = accessKey();
        Date nowDt = Date.from(now);
        Instant exp = now.plus(ttl, ChronoUnit.SECONDS);
        Date expDt = Date.from(exp);
        return Jwts.builder()
                .issuer(require(jwtProps.issuer()))
                .subject(Long.toString(user.getId()))
                .claim("role", user.getRole())
                .issuedAt(nowDt)
                .expiration(expDt)
                .signWith(key)
                .compact();
    }

    public Claims parseAccess(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(accessKey())
                .requireIssuer(require(jwtProps.issuer()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String newOpaqueRefreshValue() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public String hashRefresh(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Instant refreshExpiry(boolean rememberMe) {
        int base = ttl(jwtProps.refreshTokenTtlSeconds(), 86400);
        int remember = ttl(jwtProps.refreshTokenTtlRememberSeconds(), 604800);
        int sec = rememberMe ? remember : base;
        return Instant.now().plus(sec, ChronoUnit.SECONDS);
    }

    private SecretKey accessKey() {
        return deriveKey(require(jwtProps.accessTokenSecret()));
    }

    private static SecretKey deriveKey(String cfg) {
        byte[] raw = cfg.getBytes(StandardCharsets.UTF_8);
        byte[] hashed;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            hashed = md.digest(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return Keys.hmacShaKeyFor(hashed);
    }

    private static int ttl(Integer configured, int fallback) {
        return configured != null && configured > 0 ? configured : fallback;
    }

    private static String require(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalStateException("JWT configuration missing");
        }
        return s;
    }
}
