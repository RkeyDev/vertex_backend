package com.rkey.vertex_backend.modules.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rkey.vertex_backend.modules.auth.entity.RefreshTokenEntity;
import com.rkey.vertex_backend.modules.auth.entity.UserEntity;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByToken(String token);

    void deleteByUser(UserEntity user);

    void deleteAllByUser(UserEntity user);
}