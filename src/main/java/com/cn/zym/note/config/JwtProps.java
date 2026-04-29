package com.cn.zym.note.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "note.security.jwt")
public record JwtProps(
        String issuer,
        String accessTokenSecret,
        String refreshTokenSecret,
        Integer accessTokenTtlSeconds,
        Integer refreshTokenTtlSeconds,
        Integer refreshTokenTtlRememberSeconds) {}
