package com.lardermind.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "meal_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;

    @Column(name = "meal_type")
    private String mealType;

    @Column(name = "serving_date")
    private String servingDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    @Builder.Default
    private MealPlanStatus status = MealPlanStatus.PLANNED;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @PrePersist
    protected void onCreate() {
        long now = System.currentTimeMillis() / 1000;
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = MealPlanStatus.PLANNED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = System.currentTimeMillis() / 1000;
    }
}
