package com.cn.zym.note.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "note.file")
public record NoteUploadProps(String storageDir, String publicBaseUrl) {}
