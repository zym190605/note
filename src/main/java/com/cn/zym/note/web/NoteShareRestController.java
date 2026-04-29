package com.cn.zym.note.web;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.security.JwtUserPrincipal;
import com.cn.zym.note.service.NoteShareService;
import com.cn.zym.note.web.dto.Payloads;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notes/{noteId}/share")
@RequiredArgsConstructor
public class NoteShareRestController {

    private final NoteShareService shares;

    @PostMapping
    public Payloads.NoteShareCreated create(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable long noteId,
            @RequestBody(required = false) Payloads.NoteShareBody body) {
        Payloads.NoteShareBody b = body != null ? body : new Payloads.NoteShareBody(null);
        return shares.create(principal.userId(), noteId, b.expiresInDays());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable long noteId) {
        shares.revoke(principal.userId(), noteId);
    }

    @GetMapping
    public Payloads.NoteShareStatus status(
            @AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable long noteId) {
        return shares.status(principal.userId(), noteId)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "当前笔记未启用分享链接"));
    }
}
