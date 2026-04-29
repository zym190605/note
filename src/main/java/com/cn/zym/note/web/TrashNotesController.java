package com.cn.zym.note.web;

import com.cn.zym.note.security.JwtUserPrincipal;
import com.cn.zym.note.service.TrashService;
import com.cn.zym.note.web.dto.Payloads;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notes/trash")
@RequiredArgsConstructor
public class TrashNotesController {

    private final TrashService trashSvc;

    @GetMapping
    public Payloads.TrashPage list(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return trashSvc.list(principal.userId(), page, size);
    }

    @DeleteMapping("/empty")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void empty(@AuthenticationPrincipal JwtUserPrincipal principal) {
        trashSvc.empty(principal.userId());
    }

    @PostMapping("/{noteId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(
            @AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable long noteId) {
        trashSvc.restore(principal.userId(), noteId);
    }

    @DeleteMapping("/{noteId}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentDel(
            @AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable long noteId) {
        trashSvc.permanentDelete(principal.userId(), noteId);
    }
}
