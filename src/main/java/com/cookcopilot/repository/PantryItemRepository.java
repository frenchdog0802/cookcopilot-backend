package com.cookcopilot.repository;

import com.cookcopilot.entity.PantryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PantryItemRepository extends JpaRepository<PantryItem, UUID> {
    List<PantryItem> findByUserId(UUID userId);
    Optional<PantryItem> findByUserIdAndIngredientId(UUID userId, UUID ingredientId);
    long countByIngredientId(UUID ingredientId);
}
