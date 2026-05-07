package com.rkey.vertex_backend.modules.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.entity.VerificationTokenEntity;


@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationTokenEntity, Long> {

    Optional<VerificationTokenEntity> findByToken(String token);
    Optional<VerificationTokenEntity> findByUser(UserEntity user);
    
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM VerificationTokenEntity v WHERE v.user = :user")
    void deleteByUser(@Param("user") UserEntity user);

    
}