package com.lardermind.repository;

import com.lardermind.entity.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, UUID> {
    List<RecipeIngredient> findByRecipeId(UUID recipeId);
    List<RecipeIngredient> findByRecipeIdIn(Collection<UUID> recipeIds);
    void deleteByRecipeId(UUID recipeId);
    long countByIngredientId(UUID ingredientId);
}
