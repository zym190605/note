package com.cn.zym.note.web;

import com.cn.zym.note.security.JwtUserPrincipal;
import com.cn.zym.note.service.NotebookService;
import com.cn.zym.note.web.dto.Payloads;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notebooks")
@RequiredArgsConstructor
public class NotebookController {

    private final NotebookService notebooks;

    @GetMapping
    public Payloads.NotebookCollection list(@AuthenticationPrincipal JwtUserPrincipal p) {
        return notebooks.list(p.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payloads.NotebookView create(
            @AuthenticationPrincipal JwtUserPrincipal p, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        return notebooks.create(p.userId(), name);
    }

    @PatchMapping("/{notebookId}")
    public Payloads.NotebookView rename(
            @AuthenticationPrincipal JwtUserPrincipal p,
            @PathVariable long notebookId,
            @RequestBody Map<String, String> body) {
        return notebooks.rename(p.userId(), notebookId, body.get("name"));
    }

    @DeleteMapping("/{notebookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void del(
            @AuthenticationPrincipal JwtUserPrincipal p,
            @PathVariable long notebookId,
            @RequestBody Payloads.DeleteNotebookBody body) {
        notebooks.delete(p.userId(), notebookId, body);
    }
}
