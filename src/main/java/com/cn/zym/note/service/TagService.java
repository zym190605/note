package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.entity.TagEntity;
import com.cn.zym.note.entity.UserEntity;
import com.cn.zym.note.repository.NoteTagRepository;
import com.cn.zym.note.repository.TagRepository;
import com.cn.zym.note.repository.UserRepository;
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
public class TagService {

    private final TagRepository tags;
    private final NoteTagRepository noteTags;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Payloads.TagsCollection list(Long userId) {
        List<Payloads.TagView> items = tags.findAllByUser_IdOrderByNameAsc(userId).stream()
                .map(t -> new Payloads.TagView(
                        Long.toString(t.getId()),
                        t.getName(),
                        t.getColor(),
                        noteTags.countAllByTag_Id(t.getId())))
                .toList();
        return new Payloads.TagsCollection(items);
    }

    @Transactional
    public Payloads.TagView create(Long userId, Payloads.TagCreateBody body) {
        tags.findByUser_IdAndName(userId, body.name()).ifPresent(existing -> {
            throw new ApiBusinessException(HttpStatus.CONFLICT.value(), "TAG_EXISTS", "标签已存在");
        });
        UserEntity u = users.findById(userId).orElseThrow(() -> userMissing());
        var now = LocalDateTime.now();
        TagEntity t = new TagEntity();
        t.setUser(u);
        t.setName(body.name());
        t.setColor(body.color());
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        tags.save(t);
        return new Payloads.TagView(Long.toString(t.getId()), t.getName(), t.getColor(), 0L);
    }

    @Transactional
    public Payloads.TagView rename(Long userId, Long tagId, String name, String color) {
        TagEntity t = tags.findByIdAndUser_Id(tagId, userId).orElseThrow(() -> nf());
        if (name != null && !name.isBlank()) {
            t.setName(name);
        }
        if (color != null) {
            t.setColor(color);
        }
        t.setUpdatedAt(LocalDateTime.now());
        tags.save(t);
        return new Payloads.TagView(
                Long.toString(t.getId()), t.getName(), t.getColor(), noteTags.countAllByTag_Id(t.getId()));
    }

    @Transactional
    public void delete(Long userId, long tagId) {
        TagEntity t = tags.findByIdAndUser_Id(tagId, userId).orElseThrow(() -> nf());
        tags.delete(t);
    }

    private static ApiBusinessException nf() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "标签不存在");
    }

    private static ApiBusinessException userMissing() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "用户不存在");
    }
}
