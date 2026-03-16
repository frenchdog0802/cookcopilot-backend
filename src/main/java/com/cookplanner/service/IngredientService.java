package com.cookplanner.service;

import com.cookplanner.common.GlobalExceptionHandler.*;
import com.cookplanner.entity.Ingredient;
import com.cookplanner.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    public List<Ingredient> insertAll(List<Ingredient> ingredients) {
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
        return ingredientRepository.save(ingredient);
    }

    public Ingredient updateIngredient(UUID id, Ingredient updates) {
        Ingredient ingredient = getIngredientById(id);
        if (updates.getName() != null) ingredient.setName(updates.getName());
        if (updates.getDefaultUnit() != null) ingredient.setDefaultUnit(updates.getDefaultUnit());
        if (updates.getImageUrl() != null) ingredient.setImageUrl(updates.getImageUrl());
        return ingredientRepository.save(ingredient);
    }

    public void deleteIngredient(UUID id) {
        if (!ingredientRepository.existsById(id)) throw new ResourceNotFoundException("Ingredient not found");
        ingredientRepository.deleteById(id);
    }
}
