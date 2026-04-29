package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.entity.NoteEntity;
import com.cn.zym.note.entity.NotebookEntity;
import com.cn.zym.note.entity.UserEntity;
import com.cn.zym.note.repository.NoteRepository;
import com.cn.zym.note.repository.NotebookRepository;
import com.cn.zym.note.repository.UserRepository;
import com.cn.zym.note.util.NotebookNames;
import com.cn.zym.note.util.Times;
import com.cn.zym.note.web.dto.Payloads;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotebookService {

    private final NotebookRepository notebooks;
    private final NoteRepository notes;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Payloads.NotebookCollection list(Long userId) {
        List<NotebookEntity> list = notebooks.findAllByUser_IdOrderBySortOrderAscUpdatedAtDesc(userId);
        var items = list.stream().map(this::toView).toList();
        return new Payloads.NotebookCollection(items, items.size());
    }

    @Transactional
    public Payloads.NotebookView create(Long userId, String name) {
        if (NotebookNames.DEFAULT_NOTEBOOK_CN.equals(name)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "NAME_RESERVED", "名称与默认笔记本冲突");
        }
        UserEntity u = users.findById(userId).orElseThrow(() -> notFoundUser());
        var now = LocalDateTime.now();
        NotebookEntity nb = new NotebookEntity();
        nb.setUser(u);
        nb.setName(name);
        nb.setDefaultNotebook(false);
        nb.setNoteCount(0);
        nb.setSortOrder(0);
        nb.setCreatedAt(now);
        nb.setUpdatedAt(now);
        notebooks.save(nb);
        return toView(nb);
    }

    @Transactional
    public Payloads.NotebookView rename(Long userId, Long notebookId, String newName) {
        if (NotebookNames.DEFAULT_NOTEBOOK_CN.equals(newName)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "NAME_RESERVED", "名称与默认笔记本冲突");
        }
        NotebookEntity nb = notebooks.findByIdAndUser_Id(notebookId, userId).orElseThrow(() -> notFoundNb());
        if (nb.isDefaultNotebook()) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN.value(), "DEFAULT_READ_ONLY", "默认笔记本不可重命名");
        }
        nb.setName(newName);
        nb.setUpdatedAt(LocalDateTime.now());
        notebooks.save(nb);
        return toView(nb);
    }

    @Transactional
    public void delete(Long userId, Long notebookId, Payloads.DeleteNotebookBody body) {
        NotebookEntity nb = notebooks.findByIdAndUser_Id(notebookId, userId).orElseThrow(() -> notFoundNb());
        if (nb.isDefaultNotebook()) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN.value(), "DEFAULT_READ_ONLY", "默认笔记本不可删除");
        }
        NotebookEntity def = notebooks
                .findByUser_IdAndDefaultNotebookTrue(userId)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.CONFLICT.value(), "NO_DEFAULT", "缺少默认笔记本"));

        if ("MIGRATE_NOTES".equals(body.mode())) {
            if (body.targetNotebookId() == null) {
                throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "TARGET_REQUIRED", "请指定目标笔记本");
            }
            long targetId = Long.parseLong(body.targetNotebookId());
            NotebookEntity target = notebooks
                    .findByIdAndUser_Id(targetId, userId)
                    .orElseThrow(() -> notFoundNb());
            if (target.getId().equals(nb.getId())) {
                throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "BAD_TARGET", "目标无效");
            }
            List<NoteEntity> all = notes.findByUser_IdAndNotebook_Id(userId, notebookId);
            for (NoteEntity n : all) {
                n.setNotebook(target);
                n.setUpdatedAt(LocalDateTime.now());
            }
            notes.saveAll(all);
            recount(target.getId());
            notebooks.delete(nb);
            return;
        }
        if ("DELETE_WITH_NOTES".equals(body.mode())) {
            var now = LocalDateTime.now();
            List<NoteEntity> all = notes.findByUser_IdAndNotebook_Id(userId, notebookId);
            for (NoteEntity n : all) {
                if (n.getDeletedAt() == null) {
                    n.setDeletedAt(now);
                }
                n.setNotebook(def);
                n.setUpdatedAt(now);
            }
            notes.saveAll(all);
            notebooks.delete(nb);
            recount(def.getId());
            return;
        }
        throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "BAD_MODE", "未知删除模式");
    }

    public void recount(Long notebookId) {
        NotebookEntity nb = notebooks.findById(notebookId).orElse(null);
        if (nb == null) {
            return;
        }
        long c = notes.countByUser_IdAndNotebook_IdAndDeletedAtIsNull(nb.getUser().getId(), notebookId);
        nb.setNoteCount((int) Math.min(Integer.MAX_VALUE, c));
        notebooks.save(nb);
    }

    public void bumpCount(Long notebookId, int delta) {
        notebooks.findById(notebookId).ifPresent(nb -> {
            nb.setNoteCount(Math.max(0, nb.getNoteCount() + delta));
            nb.setUpdatedAt(LocalDateTime.now());
            notebooks.save(nb);
        });
    }

    private Payloads.NotebookView toView(NotebookEntity nb) {
        return new Payloads.NotebookView(
                Long.toString(nb.getId()),
                nb.getName(),
                nb.getNoteCount(),
                nb.isDefaultNotebook(),
                Times.iso(nb.getUpdatedAt()));
    }

    private static ApiBusinessException notFoundNb() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "笔记本不存在");
    }

    private static ApiBusinessException notFoundUser() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "用户不存在");
    }
}
