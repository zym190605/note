package com.cn.zym.note.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "note.security")
public record NoteSecurityProps(Integer passwordStrength) {}
