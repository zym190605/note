package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.config.NoteUploadProps;
import com.cn.zym.note.entity.NoteEntity;
import com.cn.zym.note.entity.UserEntity;
import com.cn.zym.note.entity.UserFileEntity;
import com.cn.zym.note.repository.NoteRepository;
import com.cn.zym.note.repository.SystemSettingsRepository;
import com.cn.zym.note.repository.UserFileRepository;
import com.cn.zym.note.repository.UserRepository;
import com.cn.zym.note.web.dto.Payloads;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final UserFileRepository files;
    private final UserRepository users;
    private final NoteRepository notes;
    private final NoteUploadProps props;
    private final SystemSettingsRepository settingsRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public Payloads.ImageUploadResult upload(MultipartFile mf, Long userId, Long noteDbIdOrNull)
            throws IOException, NoSuchAlgorithmException {
        if (mf.isEmpty()) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "EMPTY", "文件为空");
        }
        UserEntity owner = users.findById(userId).orElseThrow(FileStorageService::missingUser);
        var cfg =
                settingsRepo.findById(1L).orElseThrow(() -> new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "CFG",
                        "系统未初始化"));

        List<String> mimes =
                objectMapper.readValue(cfg.getAllowedImageMimeTypesJson(), new TypeReference<>() {});
        String ctypeRaw = mf.getContentType();
        String ctype = ctypeRaw != null ? ctypeRaw.toLowerCase(Locale.ROOT) : "";
        String mimeBase = ctype.split(";")[0].trim();
        if (!mimes.stream().map(String::toLowerCase).toList().contains(mimeBase)) {
            throw new ApiBusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "MIME", "不支持的图片类型");
        }
        if (mf.getSize() > cfg.getMaxImageSizeBytes()) {
            throw new ApiBusinessException(HttpStatus.PAYLOAD_TOO_LARGE.value(), "TOO_LARGE", "文件超出大小限制");
        }
        long used = owner.getStorageUsedBytes();
        long quota = owner.getStorageQuotaBytes();
        if (used + mf.getSize() > quota) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "QUOTA", "存储空间不足");
        }

        String ext =
                mimeBase.contains("jpeg") || mimeBase.contains("jpg") ? "jpg" : mimeBase.contains("png") ? "png" : mimeBase.contains(
                                "gif")
                        ? "gif"
                        : "bin";

        Path root = Path.of(props.storageDir()).toAbsolutePath().normalize();
        Path userDir = root.resolve(Long.toString(userId)).normalize();
        if (!userDir.startsWith(root)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "PATH", "非法路径");
        }
        Files.createDirectories(userDir);
        String name = UUID.randomUUID() + "." + ext;
        Path dest = userDir.resolve(name);
        Files.copy(mf.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        String key = Long.toString(userId) + "/" + name;

        UserFileEntity f = new UserFileEntity();
        f.setUser(owner);
        NoteEntity nt = noteDbIdOrNull != null
                ? notes.findByIdAndUser_Id(noteDbIdOrNull, userId).orElse(null)
                : null;
        f.setNote(nt);
        f.setStorageKey(key);
        f.setMimeType(mimeBase);
        f.setSizeBytes(mf.getSize());

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(key.getBytes());
        f.setSha256(hex(md.digest()));
        f.setPublicUrl("");

        files.save(f);
        String pub = props.publicBaseUrl() + "/api/v1/files/images/" + f.getId();
        f.setPublicUrl(pub);

        owner.setStorageUsedBytes(used + mf.getSize());
        users.save(owner);
        files.save(f);

        return new Payloads.ImageUploadResult(
                Long.toString(f.getId()), pub, null, null, Long.toString(f.getSizeBytes()), f.getMimeType());
    }

    @Transactional(readOnly = true)
    public Resource readImage(long userId, long fileId) throws IOException {
        UserFileEntity f = files.findByIdAndUser_Id(fileId, userId).orElseThrow(FileStorageService::missingFile);
        Path root = Path.of(props.storageDir()).toAbsolutePath();
        Path p = root.resolve(f.getStorageKey()).normalize();
        if (!p.startsWith(root)) {
            throw missingFile();
        }
        return new ByteArrayResource(Files.readAllBytes(p));
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static ApiBusinessException missingFile() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "文件不存在");
    }

    private static ApiBusinessException missingUser() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "用户不存在");
    }
}
