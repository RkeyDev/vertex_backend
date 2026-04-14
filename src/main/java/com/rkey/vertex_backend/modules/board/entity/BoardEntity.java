package com.rkey.vertex_backend.modules.board.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "boards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_name", nullable = false)
    private String boardName;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "json_data", nullable = true)
    private String josnData;

    @Column(name = "board_token", nullable = true)
    private String token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_saved")
    private OffsetDateTime lastSaved;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.lastSaved = OffsetDateTime.now();

        this.token = UUID.randomUUID().toString();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastSaved = OffsetDateTime.now();
    }
}