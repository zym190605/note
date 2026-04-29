package com.cn.zym.note.repository;

import com.cn.zym.note.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPhone(String phone);

    boolean existsByPhone(String phone);

    long countBy();

    @Query("SELECT COALESCE(SUM(u.storageUsedBytes), 0L) FROM UserEntity u")
    long sumAllStorageUsed();
}
