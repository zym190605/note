package com.cn.zym.note.web;

import com.cn.zym.note.security.JwtUserPrincipal;
import com.cn.zym.note.service.UserAccountService;
import com.cn.zym.note.web.dto.Payloads;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountService accounts;

    @GetMapping
    public Payloads.UserProfile me(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return accounts.profile(principal.userId());
    }

    @GetMapping("/stats")
    public Payloads.UserStats stats(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return accounts.stats(principal.userId());
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal JwtUserPrincipal principal, @Valid @RequestBody Payloads.ChangePasswordBody body) {
        accounts.changePassword(principal.userId(), body);
    }

    @PatchMapping
    public Payloads.UserProfile patchMe(
            @AuthenticationPrincipal JwtUserPrincipal principal, @RequestBody(required = false) Payloads.PatchMeBody body) {
        Payloads.PatchMeBody b = body != null ? body : new Payloads.PatchMeBody(null);
        return accounts.patchMe(principal.userId(), b);
    }
}
