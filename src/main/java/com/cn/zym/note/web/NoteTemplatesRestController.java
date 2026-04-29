package com.cn.zym.note.web;

import com.cn.zym.note.service.NoteTemplateCatalog;
import com.cn.zym.note.web.dto.Payloads;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/note-templates")
@RequiredArgsConstructor
public class NoteTemplatesRestController {

    private final NoteTemplateCatalog catalog;

    @GetMapping
    public List<Payloads.NoteTemplateEntry> list() {
        return catalog.templates();
    }
}
