package com.cn.zym.note.web;

import com.cn.zym.note.security.JwtUserPrincipal;
import com.cn.zym.note.service.AuthService;
import com.cn.zym.note.web.dto.Payloads;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Payloads.AuthResponse register(@Valid @RequestBody Payloads.RegisterBody body) {
        return auth.register(body);
    }

    @PostMapping("/login")
    public Payloads.AuthResponse login(@Valid @RequestBody Payloads.LoginBody body) {
        return auth.login(body);
    }

    @PostMapping("/refresh")
    public Payloads.TokenPair refresh(@Valid @RequestBody Payloads.RefreshBody body) {
        return auth.refresh(body);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal JwtUserPrincipal principal) {
        auth.logout(principal.userId());
    }
}
