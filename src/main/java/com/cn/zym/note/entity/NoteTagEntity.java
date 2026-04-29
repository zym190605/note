package com.cn.zym.note.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "note_tags")
public class NoteTagEntity {

    @EmbeddedId
    private NoteTagId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("noteId")
    @JoinColumn(name = "note_id")
    private NoteEntity note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id")
    private TagEntity tag;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
