package com.cn.zym.note.web;

import com.cn.zym.note.service.AdminFacadeService;
import com.cn.zym.note.web.dto.Payloads;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminRestController {

    private final AdminFacadeService admin;

    @GetMapping("/stats")
    public Payloads.PlatformStats stats() {
        return admin.dashboard();
    }

    @GetMapping("/users")
    public Payloads.AdminUserPage users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return admin.listUsers(keyword, status, page, size);
    }

    @PatchMapping("/users/{userId}/status")
    public void status(@PathVariable long userId, @RequestBody Payloads.UserStatusPatch body) {
        admin.setUserStatus(userId, body.status());
    }

    @GetMapping("/config")
    public Payloads.SystemConfigView getCfg() throws Exception {
        return admin.getConfig();
    }

    @PatchMapping("/config")
    public Payloads.SystemConfigView patchCfg(@RequestBody Payloads.SystemConfigPatch p) throws Exception {
        return admin.patch(p);
    }
}
