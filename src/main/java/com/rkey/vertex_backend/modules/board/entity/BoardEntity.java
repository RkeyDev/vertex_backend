package com.rkey.vertex_backend.modules.board.entity;

import java.time.OffsetDateTime;
import java.util.List;
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

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UmlComponentEntity> components = new ArrayList<>();

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