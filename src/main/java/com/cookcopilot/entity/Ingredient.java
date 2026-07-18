package com.cookcopilot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ingredients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Legacy alias of baseUnit; kept for older rows / clients. */
    @Column(name = "default_unit")
    private String defaultUnit;

    /** weight | volume | count */
    @Column(name = "unit_kind")
    private String unitKind;

    /** Canonical storage unit: g, ml, or a count unit. */
    @Column(name = "base_unit")
    private String baseUnit;

    /** Preferred UI unit within the same kind; null falls back to baseUnit. */
    @Column(name = "default_display_unit")
    private String defaultDisplayUnit;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @PrePersist
    protected void onCreate() {
        long now = System.currentTimeMillis() / 1000;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = System.currentTimeMillis() / 1000;
    }
}
