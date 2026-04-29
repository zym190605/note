package com.cn.zym.note.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("note.export")
public record NoteExportProps(String pdfFontPath) {}
