package com.rkey.vertex_backend.modules.board.entity;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "uml_components")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardEntity {
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id; 

    @Column(name="board_name", nullable=false)
    private String boardName;

    @Column(name="owner_email",nullable=false)
    private String ownerEmail;

    @Column(nullable=true)
    private List<UmlComponentEntity> components;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_saved")
    private OffsetDateTime lastSaved;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.lastSaved = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastSaved = OffsetDateTime.now();
    }
}
