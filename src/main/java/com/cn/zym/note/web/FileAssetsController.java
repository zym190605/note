package com.cn.zym.note.web;

import com.cn.zym.note.security.JwtUserPrincipal;
import com.cn.zym.note.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileAssetsController {

    private final FileStorageService files;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam("file") MultipartFile mf,
            @RequestParam(required = false) Long noteId)
            throws Exception {
        Long nid = noteId;
        var r = files.upload(mf, principal.userId(), nid);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(r);
    }

    @GetMapping("/images/{fileId}")
    public ResponseEntity<Resource> fetch(
            @AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable long fileId) throws java.io.IOException {
        Resource rs = files.readImage(principal.userId(), fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(rs);
    }
}
