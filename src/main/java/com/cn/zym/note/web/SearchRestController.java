package com.cn.zym.note.web;

import com.cn.zym.note.security.JwtUserPrincipal;
import com.cn.zym.note.service.AppSearchService;
import com.cn.zym.note.web.dto.Payloads;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchRestController {

    private final AppSearchService search;

    @GetMapping
    public Payloads.SearchPage search(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam String q,
            @RequestParam(required = false) String notebookId,
            @RequestParam(required = false) String tagId,
            @RequestParam(required = false) Boolean favoriteOnly,
            @RequestParam(defaultValue = "20") int limit) {
        Long nb = notebookId != null ? Long.parseLong(notebookId) : null;
        Long tg = tagId != null ? Long.parseLong(tagId) : null;
        return search.search(principal.userId(), q, nb, tg, favoriteOnly, limit);
    }
}
