package com.cookcopilot.service;

import com.cookcopilot.entity.InventoryAuditLog;
import com.cookcopilot.entity.InventoryChangeSource;
import com.cookcopilot.repository.InventoryAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryAuditService {

    private final InventoryAuditLogRepository inventoryAuditLogRepository;

    public void log(
            UUID userId,
            UUID ingredientId,
            UUID pantryItemId,
            InventoryChangeSource source,
            double deltaQuantity,
            Double previousQuantity,
            Double newQuantity,
            String unit,
            UUID mealPlanId,
            UUID recipeId,
            UUID shoppingListItemId,
            String note
    ) {
        if (deltaQuantity == 0.0) {
            return;
        }
        inventoryAuditLogRepository.save(InventoryAuditLog.builder()
                .userId(userId)
                .ingredientId(ingredientId)
                .pantryItemId(pantryItemId)
                .source(source)
                .deltaQuantity(deltaQuantity)
                .previousQuantity(previousQuantity)
                .newQuantity(newQuantity)
                .unit(unit != null ? unit : "")
                .mealPlanId(mealPlanId)
                .recipeId(recipeId)
                .shoppingListItemId(shoppingListItemId)
                .note(note)
                .build());
    }
}
