package com.cn.zym.note.repository;

import com.cn.zym.note.entity.NoteShareEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteShareRepository extends JpaRepository<NoteShareEntity, Long> {

    Optional<NoteShareEntity> findByToken(String token);

    Optional<NoteShareEntity> findByNote_IdAndUser_Id(long noteId, long userId);

    void deleteByNote_Id(long noteId);
}
