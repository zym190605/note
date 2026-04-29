package com.cn.zym.note.web;

import com.cn.zym.note.service.NoteShareService;
import com.cn.zym.note.web.dto.Payloads;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/shares")
@RequiredArgsConstructor
public class PublicShareRestController {

    private final NoteShareService shares;

    @GetMapping("/{token}")
    public Payloads.PublicSharedNote get(@PathVariable String token) {
        return shares.resolvePublic(token);
    }
}
