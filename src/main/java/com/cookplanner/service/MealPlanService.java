package com.cookplanner.service;

import com.cookplanner.common.GlobalExceptionHandler.*;
import com.cookplanner.entity.*;
import com.cookplanner.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final PantryItemRepository pantryItemRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;

    public List<Map<String, Object>> getAllMealPlans(UUID userId) {
        List<MealPlan> mealPlans = mealPlanRepository.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MealPlan mp : mealPlans) {
            recipeRepository.findById(mp.getRecipeId()).ifPresent(recipe -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", mp.getId());
                m.put("recipe_id", mp.getRecipeId());
                m.put("meal_name", recipe.getMealName());
                m.put("image_url", buildImageMap(recipe));
                m.put("meal_type", mp.getMealType());
                m.put("serving_date", mp.getServingDate());
                result.add(m);
            });
        }
        return result;
    }

    public MealPlan getMealPlanById(UUID id) {
        return mealPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan not found"));
    }

    public Map<String, Object> createMealPlan(UUID userId, UUID recipeId, String mealType, String servingDate) {
        MealPlan mp = MealPlan.builder()
                .userId(userId)
                .recipeId(recipeId)
                .mealType(mealType)
                .servingDate(servingDate)
                .build();
        mp = mealPlanRepository.save(mp);

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        // Check pantry inventory
        List<RecipeIngredient> recipeIngredients = recipeIngredientRepository.findByRecipeId(recipeId);
        List<PantryItem> pantryItems = pantryItemRepository.findByUserId(userId);
        List<Map<String, Object>> notEnoughItems = new ArrayList<>();

        for (RecipeIngredient ri : recipeIngredients) {
            PantryItem pantryItem = pantryItems.stream()
                    .filter(pi -> pi.getIngredientId().equals(ri.getIngredientId()))
                    .findFirst().orElse(null);

            double available = pantryItem != null ? pantryItem.getQuantity() : 0;
            double needed = ri.getQuantity() != null ? ri.getQuantity() : 0;

            if (available < needed) {
                Ingredient ing = ingredientRepository.findById(ri.getIngredientId()).orElse(null);
                double required = needed - available;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("ingredient_id", ri.getIngredientId());
                item.put("name", ing != null ? ing.getName() : "Unknown");
                item.put("required_quantity", required);
                item.put("available_quantity", available);
                notEnoughItems.add(item);

                // Auto-add to shopping list
                Optional<ShoppingListItem> existingSli = shoppingListItemRepository
                        .findByUserIdAndIngredientIdAndChecked(userId, ri.getIngredientId(), false);
                if (existingSli.isPresent()) {
                    ShoppingListItem sli = existingSli.get();
                    sli.setQuantity((sli.getQuantity() != null ? sli.getQuantity() : 0) + needed);
                    shoppingListItemRepository.save(sli);
                } else {
                    ShoppingListItem sli = ShoppingListItem.builder()
                            .userId(userId)
                            .ingredientId(ri.getIngredientId())
                            .quantity(required)
                            .checked(false)
                            .unit(ing != null ? ing.getDefaultUnit() : "")
                            .build();
                    shoppingListItemRepository.save(sli);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", mp.getId());
        result.put("recipe_id", recipeId);
        result.put("image_url", buildImageMap(recipe));
        result.put("meal_name", recipe.getMealName());
        result.put("meal_type", mp.getMealType());
        result.put("serving_date", mp.getServingDate());
        result.put("notEnoughItems", notEnoughItems);
        return result;
    }

    public MealPlan updateMealPlan(UUID id, MealPlan updates) {
        MealPlan mp = getMealPlanById(id);
        if (updates.getMealType() != null) mp.setMealType(updates.getMealType());
        if (updates.getServingDate() != null) mp.setServingDate(updates.getServingDate());
        if (updates.getRecipeId() != null) mp.setRecipeId(updates.getRecipeId());
        return mealPlanRepository.save(mp);
    }

    public void deleteMealPlan(UUID id, UUID userId) {
        MealPlan mp = getMealPlanById(id);
        mealPlanRepository.deleteById(id);
    }

    private Map<String, String> buildImageMap(Recipe recipe) {
        if (recipe.getImageUrl() == null) return null;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("url", recipe.getImageUrl());
        m.put("public_id", recipe.getImagePublicId());
        return m;
    }
}
