package com.cookplanner.service;

import com.cookplanner.common.GlobalExceptionHandler.*;
import com.cookplanner.entity.Ingredient;
import com.cookplanner.entity.Recipe;
import com.cookplanner.entity.RecipeIngredient;
import com.cookplanner.repository.IngredientRepository;
import com.cookplanner.repository.RecipeIngredientRepository;
import com.cookplanner.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final IngredientRepository ingredientRepository;

    @Transactional
    public Map<String, Object> createRecipe(UUID userId, String mealName, String instructions,
                                             UUID folderId, Map<String, String> image,
                                             List<Map<String, Object>> ingredients) {
        Recipe recipe = Recipe.builder()
                .userId(userId)
                .folderId(folderId)
                .mealName(mealName)
                .instructions(instructions)
                .imageUrl(image != null ? image.get("url") : null)
                .imagePublicId(image != null ? image.get("public_id") : null)
                .build();
        recipe = recipeRepository.save(recipe);

        List<Map<String, Object>> savedIngredients = new ArrayList<>();
        if (ingredients != null) {
            for (Map<String, Object> item : ingredients) {
                String name = (String) item.get("name");
                Ingredient ing = ingredientRepository.findByName(name)
                        .orElseGet(() -> ingredientRepository.save(
                                Ingredient.builder().name(name).defaultUnit((String) item.get("unit")).build()));

                RecipeIngredient ri = RecipeIngredient.builder()
                        .recipeId(recipe.getId())
                        .ingredientId(ing.getId())
                        .quantity(toDouble(item.get("quantity")))
                        .unit((String) item.get("unit"))
                        .build();
                recipeIngredientRepository.save(ri);
                savedIngredients.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", recipe.getId());
        result.put("meal_name", recipe.getMealName());
        result.put("instructions", recipe.getInstructions());
        result.put("ingredients", savedIngredients);
        result.put("image", buildImageMap(recipe));
        return result;
    }

    public List<Map<String, Object>> getAllRecipes(UUID userId) {
        List<Recipe> recipes = recipeRepository.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Recipe recipe : recipes) {
            List<Map<String, Object>> ingredientList = getIngredientsForRecipe(recipe.getId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", recipe.getId());
            r.put("folder_id", recipe.getFolderId());
            r.put("meal_name", recipe.getMealName());
            r.put("instructions", recipe.getInstructions());
            r.put("ingredients", ingredientList);
            r.put("image", buildImageMap(recipe));
            result.add(r);
        }
        return result;
    }

    public Map<String, Object> getRecipeById(UUID id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        List<Map<String, Object>> ingredientList = getIngredientsForRecipe(recipe.getId());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", recipe.getId());
        r.put("meal_name", recipe.getMealName());
        r.put("instructions", recipe.getInstructions());
        r.put("ingredients", ingredientList);
        r.put("image", buildImageMap(recipe));
        return r;
    }

    @Transactional
    public Map<String, Object> updateRecipe(UUID id, String mealName, String instructions,
                                             UUID folderId, Map<String, String> image,
                                             List<Map<String, Object>> ingredients) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (mealName != null) recipe.setMealName(mealName);
        if (instructions != null) recipe.setInstructions(instructions);
        if (folderId != null) recipe.setFolderId(folderId);
        if (image != null) {
            recipe.setImageUrl(image.get("url"));
            recipe.setImagePublicId(image.get("public_id"));
        }
        recipe = recipeRepository.save(recipe);

        // Replace ingredients
        recipeIngredientRepository.deleteByRecipeId(id);
        if (ingredients != null) {
            for (Map<String, Object> item : ingredients) {
                String name = (String) item.get("name");
                Ingredient ing = ingredientRepository.findByName(name)
                        .orElseGet(() -> ingredientRepository.save(
                                Ingredient.builder().name(name).defaultUnit((String) item.get("unit")).build()));

                RecipeIngredient ri = RecipeIngredient.builder()
                        .recipeId(recipe.getId())
                        .ingredientId(ing.getId())
                        .quantity(toDouble(item.get("quantity")))
                        .unit((String) item.get("unit"))
                        .build();
                recipeIngredientRepository.save(ri);
            }
        }

        List<Map<String, Object>> ingredientList = getIngredientsForRecipe(recipe.getId());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", recipe.getId());
        r.put("meal_name", recipe.getMealName());
        r.put("instructions", recipe.getInstructions());
        r.put("ingredients", ingredientList);
        r.put("image", buildImageMap(recipe));
        return r;
    }

    @Transactional
    public void deleteRecipe(UUID id) {
        if (!recipeRepository.existsById(id)) throw new ResourceNotFoundException("Recipe not found");
        recipeIngredientRepository.deleteByRecipeId(id);
        recipeRepository.deleteById(id);
    }

    // ── Helpers ──

    private List<Map<String, Object>> getIngredientsForRecipe(UUID recipeId) {
        List<RecipeIngredient> ris = recipeIngredientRepository.findByRecipeId(recipeId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (RecipeIngredient ri : ris) {
            ingredientRepository.findById(ri.getIngredientId()).ifPresent(ing -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", ri.getId());
                m.put("name", ing.getName());
                m.put("quantity", ri.getQuantity());
                m.put("unit", ri.getUnit());
                list.add(m);
            });
        }
        return list;
    }

    private Map<String, String> buildImageMap(Recipe recipe) {
        if (recipe.getImageUrl() == null) return null;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("url", recipe.getImageUrl());
        m.put("public_id", recipe.getImagePublicId());
        return m;
    }

    private Double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) return Double.parseDouble((String) val);
        return 0.0;
    }
}
