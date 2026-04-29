package com.cn.zym.note.repository;

import com.cn.zym.note.entity.UserFileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFileRepository extends JpaRepository<UserFileEntity, Long> {

    Optional<UserFileEntity> findByIdAndUser_Id(Long id, Long userId);

    @Query("SELECT COALESCE(SUM(f.sizeBytes), 0) FROM UserFileEntity f WHERE f.user.id = :uid")
    long sumSizeBytesByUserId(@Param("uid") long uid);
}
