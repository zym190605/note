package com.cn.zym.note.repository;

import com.cn.zym.note.entity.RefreshTokenEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByJtiAndRevokedAtIsNull(String jti);

    Optional<RefreshTokenEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    List<RefreshTokenEntity> findAllByUser_Id(Long userId);
}
