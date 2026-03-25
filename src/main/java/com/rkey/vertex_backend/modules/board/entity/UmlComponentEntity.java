package com.rkey.vertex_backend.modules.board.entity;

import java.time.OffsetDateTime;
import java.util.HashMap;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.rkey.vertex_backend.modules.board.models.enums.ComponentType;

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
public class UmlComponentEntity {
   
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id; 

    @Column(nullable=false)
    private Double xPos;

    @Column(nullable=false)
    private Double yPos;

    @Column(nullable=false)
    private Double width;

    @Column(nullable=false)
    private Double height;

    @Column(nullable=false)
    private ComponentType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
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
