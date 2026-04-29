package com.cn.zym.note.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class Payloads {

    private Payloads() {}

    public record RegisterBody(
            @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
            @Size(min = 6, max = 16) String password) {}

    public record LoginBody(
            @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
            @Size(min = 6, max = 16) String password,
            Boolean rememberMe) {}

    public record RefreshBody(String refreshToken) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenPair(String accessToken, Integer expiresIn, String refreshToken, Integer refreshExpiresIn) {}

    public record AuthResponse(String accessToken, Integer expiresIn, String refreshToken, Integer refreshExpiresIn, String userId) {}

    public record ChangePasswordBody(String oldPassword, String newPassword) {}

    public record UserProfile(
            String userId,
            String phoneMasked,
            String nickname,
            String avatarUrl,
            String registeredAt,
            /** LIGHT | DARK | SYSTEM */
            String uiTheme) {}

    /** 可为空字段：仅更新非空项 */
    public record PatchMeBody(String uiTheme) {}

    public record NoteShareBody(Integer expiresInDays) {}

    public record NoteShareCreated(String shareUrl, String token, String expiresAt) {}

    public record NoteShareStatus(String shareUrl, String expiresAt) {}

    public record PublicSharedNote(String title, String contentHtml, String preview) {}

    public record NoteTemplateEntry(
            String id, String name, String description, String title, String contentHtml) {}

    public record UserStats(String noteTotal, String storageUsedBytes, String storageQuotaBytes) {}

    public record NotebookView(
            String id, String name, int noteCount, boolean isDefault, String updatedAt) {}

    public record NotebookCollection(List<NotebookView> items, long totalCount) {}

    public record DeleteNotebookBody(String mode, String targetNotebookId) {}

    public record TagView(String id, String name, String color, Long noteCount) {}

    public record TagsCollection(List<TagView> items) {}

    public record TagCreateBody(String name, String color) {}

    public record NoteSummary(
            String id,
            String title,
            String preview,
            String updatedAt,
            String notebookId,
            boolean favored) {}

    public record TrashNoteSummary(
            String id,
            String title,
            String preview,
            String updatedAt,
            String notebookId,
            boolean favored,
            String deletedAt,
            String expireAt) {}

    public record NotesPage(List<NoteSummary> items, long totalCount) {}

    public record TrashPage(List<TrashNoteSummary> items, long totalCount) {}

    public record CreateNoteBody(
            String notebookId, String title, String contentHtml, List<String> tagIds) {}

    public record PatchNoteBody(
            String title, String contentHtml, String notebookId, List<String> tagIds, Integer version) {}

    public record NoteDetail(
            String id,
            String title,
            String preview,
            String updatedAt,
            String notebookId,
            boolean favored,
            String contentHtml,
            int version,
            String createdAt,
            List<TagView> tags) {}

    public record FavoriteBody(boolean favored) {}

    public record MoveNoteBody(String targetNotebookId) {}

    public record Problem(String code, String message, Object details) {}

    public record SearchHit(
            String noteId,
            String title,
            String snippet,
            String notebookId,
            String updatedAt,
            List<String> matchedFields) {}

    public record SearchPage(List<SearchHit> items, String nextCursor) {}

    public record ImageUploadResult(String fileId, String url, Integer width, Integer height, String sizeBytes, String mimeType) {}

    public record AdminUserRow(
            String userId,
            String phoneMasked,
            String status,
            long noteCount,
            String storageUsedBytes,
            String registeredAt) {}

    public record AdminUserPage(List<AdminUserRow> items, long totalCount) {}

    public record UserStatusPatch(String status) {}

    public record PlatformStats(String userTotal, String noteTotal, String storageUsedBytes) {}

    public record SystemConfigView(
            String defaultUserQuotaBytes,
            Integer maxImageSizeBytes,
            List<String> allowedImageMimeTypes,
            Integer trashRetentionDays) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SystemConfigPatch(
            Long defaultUserQuotaBytes, Integer maxImageSizeBytes, List<String> allowedImageMimeTypes, Integer trashRetentionDays) {}

    public record ConflictBody(String code, String message, Integer currentVersion) {}

    public record TagListBody(List<String> tagIds) {}
}
