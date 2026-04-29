package com.cn.zym.note.repository;

import com.cn.zym.note.entity.TagEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<TagEntity, Long> {

    Optional<TagEntity> findByIdAndUser_Id(Long id, Long userId);

    Optional<TagEntity> findByUser_IdAndName(Long userId, String name);

    List<TagEntity> findAllByUser_IdOrderByNameAsc(Long userId);

    long countByUser_Id(Long userId);
}
