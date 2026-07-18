package com.lardermind.repository;

import com.lardermind.entity.ShoppingListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {
    List<ShoppingListItem> findByUserId(UUID userId);
    List<ShoppingListItem> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<ShoppingListItem> findByUserIdAndIngredientIdAndChecked(UUID userId, UUID ingredientId, Boolean checked);
    long countByIngredientId(UUID ingredientId);
}
