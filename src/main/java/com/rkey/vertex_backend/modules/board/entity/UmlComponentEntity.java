package com.rkey.vertex_backend.modules.board.entity;

import java.time.OffsetDateTime;
import java.util.HashMap;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.rkey.vertex_backend.modules.board.models.enums.ComponentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity representing a UML component (Class, Interface, etc.) on the design board.
 * Uses JSONB for flexible data storage and standard JPA for spatial coordinates.
 */
@Entity
@Table(name = "uml_components")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UmlComponentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private BoardEntity board;

    @Column(name = "x_pos", nullable = false)
    private Double xPos;

    @Column(name = "y_pos", nullable = false)
    private Double yPos;

    @Column(nullable = false)
    private Double width;

    @Column(nullable = false)
    private Double height;

    @Enumerated(EnumType.STRING) 
    @Column(name = "component_type", nullable = false)
    private ComponentType type;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_data", columnDefinition = "jsonb")
    private HashMap<String, Object> data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}