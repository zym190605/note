package com.cn.zym.note.repository;

import com.cn.zym.note.entity.NotebookEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotebookRepository extends JpaRepository<NotebookEntity, Long> {

    Optional<NotebookEntity> findByIdAndUser_Id(Long id, Long userId);

    Optional<NotebookEntity> findByUser_IdAndDefaultNotebookTrue(Long userId);

    List<NotebookEntity> findAllByUser_IdOrderBySortOrderAscUpdatedAtDesc(Long userId);

    long countAllByUser_Id(Long userId);
}
