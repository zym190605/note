package com.cn.zym.note.service;

import com.cn.zym.note.entity.NoteEntity;
import com.cn.zym.note.util.Times;
import com.cn.zym.note.web.dto.Payloads;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppSearchService {

    private final NoteSearchQuery noteSearchQuery;

    @Transactional(readOnly = true)
    public Payloads.SearchPage search(long userId, String q, Long notebookId, Long tagId, Boolean favoriteOnly, int limit) {
        int lim = Math.min(Math.max(limit, 1), 100);
        Pageable pageable =
                PageRequest.of(0, lim, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<NoteEntity> page =
                noteSearchQuery.pageActiveNotesWithKeyword(userId, q.trim(), notebookId, tagId, favoriteOnly, pageable);
        List<Payloads.SearchHit> hits = new ArrayList<>();
        for (NoteEntity n : page.getContent()) {
            hits.add(new Payloads.SearchHit(
                    Long.toString(n.getId()),
                    n.getTitle(),
                    snippet(q, n.getPreview()),
                    Long.toString(n.getNotebook().getId()),
                    Times.iso(n.getUpdatedAt()),
                    List.of("title")));
        }
        return new Payloads.SearchPage(hits, null);
    }

    private static String snippet(String q, String preview) {
        if (preview == null) {
            return "";
        }
        int idx = preview.toLowerCase().indexOf(q.trim().toLowerCase());
        if (idx < 0) {
            return preview.substring(0, Math.min(preview.length(), 120));
        }
        int start = Math.max(idx - 20, 0);
        int end = Math.min(preview.length(), idx + q.length() + 60);
        return preview.substring(start, end);
    }
}
