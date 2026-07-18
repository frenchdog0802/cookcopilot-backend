package com.cookcopilot.service;

import com.cookcopilot.common.GlobalExceptionHandler.*;
import com.cookcopilot.entity.Ingredient;
import com.cookcopilot.repository.IngredientRepository;
import com.cookcopilot.repository.PantryItemRepository;
import com.cookcopilot.repository.RecipeIngredientRepository;
import com.cookcopilot.repository.ShoppingListItemRepository;
import com.cookcopilot.unit.UnitConverter;
import com.cookcopilot.unit.UnitKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final PantryItemRepository pantryItemRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;

    public List<Ingredient> insertAll(List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            UnitConverter.applyDefaults(ingredient);
        }
        return ingredientRepository.saveAll(ingredients);
    }

    public List<Ingredient> getAllIngredients(String query) {
        if (query != null && !query.isBlank()) {
            return ingredientRepository.findByNameContainingIgnoreCase(query);
        }
        return ingredientRepository.findAll();
    }

    public Ingredient getIngredientById(UUID id) {
        return ingredientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
    }

    public Ingredient createIngredient(Ingredient ingredient) {
        UnitConverter.applyDefaults(ingredient);
        return ingredientRepository.save(ingredient);
    }

    public Ingredient updateIngredient(UUID id, Ingredient updates) {
        Ingredient ingredient = getIngredientById(id);
        if (updates.getName() != null) {
            ingredient.setName(updates.getName());
        }
        if (updates.getImageUrl() != null) {
            ingredient.setImageUrl(updates.getImageUrl());
        }

        boolean kindChangeRequested = updates.getUnitKind() != null
                && !updates.getUnitKind().isBlank()
                && !updates.getUnitKind().equalsIgnoreCase(
                        ingredient.getUnitKind() != null ? ingredient.getUnitKind() : "");

        if (kindChangeRequested) {
            if (hasQuantityReferences(id)) {
                throw new BadRequestException(
                        "Cannot change unit kind while recipe, pantry, or shopping list items reference this ingredient. Delete and recreate, or use merge later.");
            }
            ingredient.setUnitKind(updates.getUnitKind());
            ingredient.setBaseUnit(updates.getBaseUnit());
            ingredient.setDefaultDisplayUnit(updates.getDefaultDisplayUnit());
            if (updates.getDefaultUnit() != null) {
                ingredient.setDefaultUnit(updates.getDefaultUnit());
            }
            UnitConverter.applyDefaults(ingredient);
            return ingredientRepository.save(ingredient);
        }

        if (updates.getDefaultDisplayUnit() != null) {
            String display = UnitConverter.normalize(updates.getDefaultDisplayUnit());
            UnitKind kind = UnitConverter.resolveKind(ingredient);
            if (display != null) {
                UnitKind displayKind = UnitConverter.kindOf(display);
                if (displayKind != kind) {
                    throw new BadRequestException("defaultDisplayUnit must match unit kind " + kind.toApiValue());
                }
                if (kind == UnitKind.COUNT && !display.equals(UnitConverter.resolveBaseUnit(ingredient))) {
                    throw new BadRequestException("Count ingredients cannot change display unit away from base unit");
                }
                ingredient.setDefaultDisplayUnit(display);
            }
        }

        if (updates.getBaseUnit() != null && !hasQuantityReferences(id)) {
            ingredient.setBaseUnit(updates.getBaseUnit());
            UnitConverter.applyDefaults(ingredient);
        }

        if (updates.getDefaultUnit() != null && ingredient.getBaseUnit() == null) {
            ingredient.setDefaultUnit(updates.getDefaultUnit());
            UnitConverter.applyDefaults(ingredient);
        }

        return ingredientRepository.save(ingredient);
    }

    public void deleteIngredient(UUID id) {
        if (!ingredientRepository.existsById(id)) {
            throw new ResourceNotFoundException("Ingredient not found");
        }
        ingredientRepository.deleteById(id);
    }

    public boolean hasQuantityReferences(UUID ingredientId) {
        return recipeIngredientRepository.countByIngredientId(ingredientId) > 0
                || pantryItemRepository.countByIngredientId(ingredientId) > 0
                || shoppingListItemRepository.countByIngredientId(ingredientId) > 0;
    }
}
