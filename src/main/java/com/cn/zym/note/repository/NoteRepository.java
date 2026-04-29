package com.cn.zym.note.repository;

import com.cn.zym.note.entity.NoteEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<NoteEntity, Long> {

    Optional<NoteEntity> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_IdAndNotebook_IdAndDeletedAtIsNull(Long userId, Long notebookId);

    long countByUser_IdAndDeletedAtIsNull(Long userId);

    long count();

    Page<NoteEntity> findByUser_IdAndDeletedAtIsNullAndNotebook_IdOrderByUpdatedAtDesc(
            Long userId, Long notebookId, Pageable pageable);

    Page<NoteEntity> findByUser_IdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    Page<NoteEntity> findByUser_IdAndDeletedAtIsNullAndFavoredTrueOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    Page<NoteEntity> findByUser_IdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Long userId, Pageable pageable);

    @Query(
            "SELECT DISTINCT n FROM NoteEntity n INNER JOIN NoteTagEntity nt ON nt.note.id = n.id "
                    + "WHERE n.user.id = :userId AND n.deletedAt IS NULL AND nt.tag.id IN :tagIds")
    Page<NoteEntity> findDistinctByTags(
            @Param("userId") Long userId, @Param("tagIds") List<Long> tagIds, Pageable pageable);

    List<NoteEntity> findByUser_IdAndNotebook_IdAndDeletedAtIsNull(Long userId, Long notebookId);

    List<NoteEntity> findByUser_IdAndNotebook_Id(Long userId, Long notebookId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM NoteEntity n WHERE n.deletedAt IS NOT NULL AND n.deletedAt < :cutoff")
    int purgeTrashOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM NoteEntity n WHERE n.user.id = :uid AND n.deletedAt IS NOT NULL")
    int deleteAllTrashOfUser(@Param("uid") long uid);
}

