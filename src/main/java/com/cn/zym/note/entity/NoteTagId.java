package com.cn.zym.note.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class NoteTagId implements Serializable {

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;
}
