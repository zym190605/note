package com.cn.zym.note.service;

import com.cn.zym.note.web.dto.Payloads;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoteTemplateCatalog {

    private final ObjectMapper objectMapper;

    private volatile List<Payloads.NoteTemplateEntry> cache;

    public List<Payloads.NoteTemplateEntry> templates() {
        List<Payloads.NoteTemplateEntry> c = cache;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (cache != null) {
                return cache;
            }
            try {
                ClassPathResource res = new ClassPathResource("templates/note-templates.json");
                try (InputStream in = res.getInputStream()) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    cache = objectMapper.readValue(json, new TypeReference<>() {});
                    return cache;
                }
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
