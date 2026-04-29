package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.entity.NoteEntity;
import com.cn.zym.note.repository.NoteRepository;
import com.cn.zym.note.repository.SystemSettingsRepository;
import com.cn.zym.note.util.Times;
import com.cn.zym.note.web.dto.Payloads;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrashService {

    private final NoteRepository notes;
    private final SystemSettingsRepository settings;
    private final NotebookService notebookSvc;

    @Transactional(readOnly = true)
    public Payloads.TrashPage list(Long userId, int page, int size) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "deletedAt"));
        Page<NoteEntity> pg = notes.findByUser_IdAndDeletedAtIsNotNullOrderByDeletedAtDesc(userId, p);
        int days = settings.findById(1L).map(s -> s.getTrashRetentionDays()).orElse(90);
        return new Payloads.TrashPage(
                pg.getContent().stream()
                        .map(n -> {
                            LocalDateTime exp = n.getDeletedAt().plusDays(days);
                            return new Payloads.TrashNoteSummary(
                                    Long.toString(n.getId()),
                                    n.getTitle(),
                                    n.getPreview(),
                                    Times.iso(n.getUpdatedAt()),
                                    Long.toString(n.getNotebook().getId()),
                                    n.isFavored(),
                                    Times.iso(n.getDeletedAt()),
                                    Times.iso(exp));
                        })
                        .toList(),
                pg.getTotalElements());
    }

    @Transactional
    public void restore(Long userId, long noteId) {
        NoteEntity n = notes.findByIdAndUser_Id(noteId, userId).orElseThrow(() -> missing());
        if (n.getDeletedAt() == null) {
            return;
        }
        n.setDeletedAt(null);
        n.setUpdatedAt(LocalDateTime.now());
        notes.save(n);
        notebookSvc.recount(n.getNotebook().getId());
    }

    @Transactional
    public void permanentDelete(Long userId, long noteId) {
        NoteEntity n = notes.findByIdAndUser_Id(noteId, userId).orElseThrow(() -> missing());
        if (n.getDeletedAt() == null) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST.value(), "NOT_IN_TRASH", "笔记不在回收站");
        }
        Long nb = n.getNotebook().getId();
        notes.delete(n);
        notebookSvc.recount(nb);
    }

    @Transactional
    public void empty(Long userId) {
        notes.deleteAllTrashOfUser(userId);
    }

    private static ApiBusinessException missing() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "笔记不存在");
    }
}
