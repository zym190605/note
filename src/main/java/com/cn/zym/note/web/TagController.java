package com.cn.zym.note.web;

import com.cn.zym.note.security.JwtUserPrincipal;
import com.cn.zym.note.service.TagService;
import com.cn.zym.note.web.dto.Payloads;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tags;

    @GetMapping
    public Payloads.TagsCollection list(@AuthenticationPrincipal JwtUserPrincipal p) {
        return tags.list(p.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payloads.TagView create(
            @AuthenticationPrincipal JwtUserPrincipal principal, @Valid @RequestBody Payloads.TagCreateBody body) {
        return tags.create(principal.userId(), body);
    }

    @PatchMapping("/{tagId}")
    public Payloads.TagView patch(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable long tagId,
            @RequestBody java.util.Map<String, String> patch) {
        return tags.rename(
                principal.userId(),
                tagId,
                patch.getOrDefault("name", null),
                patch.getOrDefault("color", null));
    }

    @DeleteMapping("/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void del(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable long tagId) {
        tags.delete(principal.userId(), tagId);
    }
}
