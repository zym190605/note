package com.cn.zym.note.repository;

import com.cn.zym.note.entity.NoteTagEntity;
import com.cn.zym.note.entity.NoteTagId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteTagRepository extends JpaRepository<NoteTagEntity, NoteTagId> {

    List<NoteTagEntity> findAllByNote_Id(Long noteId);

    void deleteAllByNote_Id(Long noteId);

    long countAllByTag_Id(Long tagId);
}
