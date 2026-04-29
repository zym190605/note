package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.entity.NoteEntity;
import com.cn.zym.note.entity.NoteTagEntity;
import com.cn.zym.note.entity.NoteTagId;
import com.cn.zym.note.entity.NotebookEntity;
import com.cn.zym.note.entity.TagEntity;
import com.cn.zym.note.entity.UserEntity;
import com.cn.zym.note.repository.NoteRepository;
import com.cn.zym.note.repository.NoteTagRepository;
import com.cn.zym.note.repository.NotebookRepository;
import com.cn.zym.note.repository.TagRepository;
import com.cn.zym.note.repository.UserRepository;
import com.cn.zym.note.util.HtmlTexts;
import com.cn.zym.note.util.Times;
import com.cn.zym.note.web.dto.Payloads;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
public class NoteService {

    private final NoteRepository notes;
    private final NoteTagRepository noteTags;
    private final NotebookRepository notebooks;
    private final TagRepository tags;
    private final UserRepository users;
    private final NotebookService notebookSvc;
    private final NoteSearchQuery noteSearchQuery;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Payloads.NotesPage list(
            Long userId,
            String notebookIdStr,
            List<Long> tagIds,
            Boolean favoriteOnly,
            String keyword,
            int page,
            int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);

        Long notebookId = notebookIdStr != null && !notebookIdStr.isBlank()
                ? Long.parseLong(notebookIdStr)
                : null;

        Page<NoteEntity> pg;

        if (keyword != null && !keyword.isBlank()) {
            pg = noteSearchQuery.pageActiveNotesWithKeyword(userId, keyword, notebookId, null, favoriteOnly, p);
        } else if (Boolean.TRUE.equals(favoriteOnly)) {
            pg = notes.findByUser_IdAndDeletedAtIsNullAndFavoredTrueOrderByUpdatedAtDesc(userId, p);
        } else if (tagIds != null && !tagIds.isEmpty()) {
            pg = notes.findDistinctByTags(userId, tagIds, p);
        } else if (notebookId != null) {
            pg = notes.findByUser_IdAndDeletedAtIsNullAndNotebook_IdOrderByUpdatedAtDesc(userId, notebookId, p);
        } else {
            pg = notes.findByUser_IdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId, p);
        }

        return new Payloads.NotesPage(
                pg.getContent().stream().map(this::summary).toList(),
                pg.getTotalElements());
    }

    private Payloads.NoteSummary summary(NoteEntity n) {
        return new Payloads.NoteSummary(
                Long.toString(n.getId()),
                n.getTitle(),
                n.getPreview(),
                Times.iso(n.getUpdatedAt()),
                Long.toString(n.getNotebook().getId()),
                n.isFavored());
    }

    @Transactional(readOnly = true)
    public Payloads.NoteDetail detail(Long userId, Long noteId) {
        NoteEntity n = activeNote(userId, noteId);
        var tagViews = noteTags.findAllByNote_Id(noteId).stream()
                .map(nt -> toTag(nt.getTag()))
                .toList();
        return new Payloads.NoteDetail(
                Long.toString(n.getId()),
                n.getTitle(),
                n.getPreview(),
                Times.iso(n.getUpdatedAt()),
                Long.toString(n.getNotebook().getId()),
                n.isFavored(),
                n.getContentHtml(),
                n.getVersion(),
                Times.iso(n.getCreatedAt()),
                tagViews);
    }

    @Transactional
    public Payloads.NoteDetail create(Long userId, Payloads.CreateNoteBody body) {
        UserEntity u = users.findById(userId).orElseThrow(() -> notFoundUser());
        NotebookEntity nb = notebooks
                .findByIdAndUser_Id(Long.parseLong(body.notebookId()), userId)
                .orElseThrow(() -> notFoundNotebook());

        String html = Optional.ofNullable(body.contentHtml()).orElse("");
        String title = Optional.ofNullable(body.title()).filter(t -> !t.isBlank()).orElse("无标题");
        var now = LocalDateTime.now();
        NoteEntity n = new NoteEntity();
        n.setUser(u);
        n.setNotebook(nb);
        n.setTitle(title);
        n.setContentHtml(html);
        n.setPreview(HtmlTexts.plainPreview(html, 500));
        n.setVersion(1);
        n.setDeletedAt(null);
        n.setFavored(false);
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        notes.save(n);
        replaceTags(userId, n.getId(), body.tagIds(), n);
        em.flush(); // IDs available in note-tag comp id
        notebookSvc.recount(nb.getId());
        return detail(userId, n.getId());
    }

    @Transactional
    public Payloads.NoteDetail patch(
            Long userId, Long noteId, Payloads.PatchNoteBody body, Optional<Integer> ifMatchHeader) {

        NoteEntity n = activeNote(userId, noteId);
        Integer incoming = body.version() != null ? body.version() : ifMatchHeader.orElse(null);
        if (incoming != null && !incoming.equals(n.getVersion())) {
            throw new ApiBusinessException(HttpStatus.CONFLICT.value(), "VERSION_CONFLICT", "版本冲突");
        }

        if (body.title() != null && !body.title().isBlank()) {
            n.setTitle(body.title());
        }
        if (body.contentHtml() != null) {
            n.setContentHtml(body.contentHtml());
            n.setPreview(HtmlTexts.plainPreview(body.contentHtml(), 500));
        }
        if (body.notebookId() != null && !body.notebookId().isBlank()) {
            NotebookEntity tgt =
                    notebooks
                            .findByIdAndUser_Id(Long.parseLong(body.notebookId()), userId)
                            .orElseThrow(() -> notFoundNotebook());
            Long oldNb = n.getNotebook().getId();
            if (!oldNb.equals(tgt.getId())) {
                n.setNotebook(tgt);
                notebookSvc.recount(oldNb);
                notebookSvc.recount(tgt.getId());
            }
        }
        if (body.tagIds() != null) {
            replaceTags(userId, n.getId(), body.tagIds(), n);
        }
        n.setVersion(n.getVersion() + 1);
        n.setUpdatedAt(LocalDateTime.now());
        notes.save(n);
        notebookSvc.recount(n.getNotebook().getId());
        return detail(userId, noteId);
    }

    @Transactional
    public void trash(Long userId, Long noteId) {
        NoteEntity n = notes.findByIdAndUser_Id(noteId, userId).orElseThrow(NoteService::missingNote);
        if (n.getDeletedAt() != null) {
            return;
        }
        Long nb = n.getNotebook().getId();
        n.setDeletedAt(LocalDateTime.now());
        n.setUpdatedAt(LocalDateTime.now());
        notes.save(n);
        notebookSvc.recount(nb);
    }

    @Transactional
    public void fav(Long userId, Long noteId, boolean favored) {
        NoteEntity n = activeNote(userId, noteId);
        n.setFavored(favored);
        n.setUpdatedAt(LocalDateTime.now());
        notes.save(n);
    }

    @Transactional
    public void move(Long userId, Long noteId, String nbIdStr) {
        NoteEntity n = activeNote(userId, noteId);
        NotebookEntity tgt =
                notebooks
                        .findByIdAndUser_Id(Long.parseLong(nbIdStr), userId)
                        .orElseThrow(() -> notFoundNotebook());
        Long src = n.getNotebook().getId();
        if (src.equals(tgt.getId())) {
            return;
        }
        n.setNotebook(tgt);
        n.setUpdatedAt(LocalDateTime.now());
        notes.save(n);
        notebookSvc.recount(src);
        notebookSvc.recount(tgt.getId());
    }

    private void replaceTags(Long userId, long noteId, List<String> tagIdStrs, NoteEntity owning) {
        noteTags.deleteAllByNote_Id(noteId);
        if (tagIdStrs == null || tagIdStrs.isEmpty()) {
            em.flush();
            return;
        }
        var now = LocalDateTime.now();
        NoteEntity attach = owning != null ? owning : notes.getReferenceById(noteId);

        for (String tidStr : tagIdStrs) {
            long tid = Long.parseLong(tidStr);
            TagEntity tag =
                    tags.findByIdAndUser_Id(tid, userId).orElseThrow(() -> notFoundTag());
            NoteTagId id = new NoteTagId(noteId, tag.getId()); // lombok all-args embeddable
            NoteTagEntity link = new NoteTagEntity();
            link.setId(id);
            link.setNote(attach);
            link.setTag(tag);
            link.setCreatedAt(now);
            noteTags.save(link);
        }
        em.flush();
    }

    private NoteEntity activeNote(long userId, long noteId) {
        NoteEntity n = notes.findByIdAndUser_Id(noteId, userId).orElseThrow(NoteService::missingNote);
        if (n.getDeletedAt() != null) {
            throw missingNote();
        }
        return n;
    }

    @Transactional(readOnly = true)
    public NoteEntity requireActive(Long userId, long noteId) {
        return activeNote(userId, noteId);
    }

    private Payloads.TagView toTag(TagEntity tag) {
        return new Payloads.TagView(
                Long.toString(tag.getId()), tag.getName(), tag.getColor(), null);
    }

    @Transactional
    public List<Payloads.TagView> rewriteTags(Long userId, long noteId, List<String> tagIds) {
        NoteEntity n = activeNote(userId, noteId);
        replaceTags(userId, noteId, tagIds, n);
        notes.save(n);
        return tagsForNote(noteId);
    }

    private List<Payloads.TagView> tagsForNote(Long noteId) {
        return noteTags.findAllByNote_Id(noteId).stream().map(nt -> toTag(nt.getTag())).toList();
    }

    private static ApiBusinessException missingNote() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "笔记不存在");
    }

    private static ApiBusinessException notFoundUser() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "用户不存在");
    }

    private static ApiBusinessException notFoundNotebook() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "笔记本不存在");
    }

    private static ApiBusinessException notFoundTag() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "标签不存在");
    }
}
