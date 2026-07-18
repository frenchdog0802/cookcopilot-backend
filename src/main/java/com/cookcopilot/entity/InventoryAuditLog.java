package com.cookcopilot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "inventory_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    @Column(name = "pantry_item_id")
    private UUID pantryItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InventoryChangeSource source;

    @Column(name = "delta_quantity", nullable = false)
    private Double deltaQuantity;

    @Column(name = "previous_quantity")
    private Double previousQuantity;

    @Column(name = "new_quantity")
    private Double newQuantity;

    @Column(nullable = false)
    private String unit;

    @Column(name = "meal_plan_id")
    private UUID mealPlanId;

    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "shopping_list_item_id")
    private UUID shoppingListItemId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = System.currentTimeMillis() / 1000;
        }
    }
}
