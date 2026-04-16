package com.rkey.vertex_backend.modules.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import com.rkey.vertex_backend.modules.board.entity.BoardEntity;

@Repository
public interface BoardRepository extends JpaRepository<BoardEntity, Long>{
    
    List<BoardEntity> findAllByOwnerEmail(String ownerEmail);
    Optional<BoardEntity> findByOwnerEmailAndBoardName(String ownerEmail, String boardName);
    Optional<BoardEntity> findByToken(String token);
}
