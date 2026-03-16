package com.cookplanner.repository;

import com.cookplanner.entity.ShoppingListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {
    List<ShoppingListItem> findByUserId(UUID userId);
    Optional<ShoppingListItem> findByUserIdAndIngredientIdAndChecked(UUID userId, UUID ingredientId, Boolean checked);
}
