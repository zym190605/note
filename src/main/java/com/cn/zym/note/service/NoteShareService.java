package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.config.NoteUploadProps;
import com.cn.zym.note.entity.NoteEntity;
import com.cn.zym.note.entity.NoteShareEntity;
import com.cn.zym.note.repository.NoteShareRepository;
import com.cn.zym.note.util.Times;
import com.cn.zym.note.web.dto.Payloads;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteShareService {

    private static final SecureRandom RNG = new SecureRandom();

    private final NoteShareRepository shares;
    private final NoteService noteSvc;
    private final NoteUploadProps uploadProps;

    @Transactional
    public Payloads.NoteShareCreated create(Long userId, long noteId, Integer expiresInDays) {
        NoteEntity n = noteSvc.requireActive(userId, noteId);
        shares.deleteByNote_Id(noteId);
        NoteShareEntity s = new NoteShareEntity();
        s.setUser(n.getUser());
        s.setNote(n);
        s.setToken(newToken());
        if (expiresInDays != null && expiresInDays > 0) {
            s.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        } else {
            s.setExpiresAt(null);
        }
        shares.save(s);

        String base = uploadProps.publicBaseUrl().replaceAll("/+$", "");
        String shareUrl = base + "/api/v1/public/shares/" + s.getToken();
        return new Payloads.NoteShareCreated(
                shareUrl, s.getToken(), s.getExpiresAt() != null ? Times.iso(s.getExpiresAt()) : null);
    }

    @Transactional
    public void revoke(Long userId, long noteId) {
        noteSvc.requireActive(userId, noteId);
        shares.deleteByNote_Id(noteId);
    }

    @Transactional(readOnly = true)
    public Optional<Payloads.NoteShareStatus> status(Long userId, long noteId) {
        noteSvc.requireActive(userId, noteId);
        return shares.findByNote_IdAndUser_Id(noteId, userId)
                .map(s -> new Payloads.NoteShareStatus(baseUrlOrEmpty() + "/api/v1/public/shares/" + s.getToken(),
                        s.getExpiresAt() != null ? Times.iso(s.getExpiresAt()) : null));
    }

    @Transactional(readOnly = true)
    public Payloads.PublicSharedNote resolvePublic(String token) {
        NoteShareEntity s = shares.findByToken(token.strip()).orElseThrow(NoteShareService::shareGone);
        if (s.getExpiresAt() != null && LocalDateTime.now().isAfter(s.getExpiresAt())) {
            throw shareGone();
        }
        NoteEntity n = s.getNote();
        if (n.getDeletedAt() != null) {
            throw shareGone();
        }
        return new Payloads.PublicSharedNote(n.getTitle(), n.getContentHtml(), n.getPreview());
    }

    private String baseUrlOrEmpty() {
        return uploadProps.publicBaseUrl().replaceAll("/+$", "");
    }

    private static String newToken() {
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static ApiBusinessException shareGone() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "SHARED_NOT_FOUND", "分享不存在或已过期");
    }
}
