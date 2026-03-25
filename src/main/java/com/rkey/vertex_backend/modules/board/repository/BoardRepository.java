package com.rkey.vertex_backend.modules.board.repository;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import com.rkey.vertex_backend.modules.board.entity.BoardEntity;

@Repository
public interface BoardRepository extends JpaRepository<BoardEntity, Long>{
    
    List<BoardEntity> findAllByOwnerEmail(String ownerEmail);
}
