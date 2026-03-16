package com.cookplanner.service;

import com.cookplanner.entity.MealPlan;
import com.cookplanner.entity.Recipe;
import com.cookplanner.repository.MealPlanRepository;
import com.cookplanner.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatActionService {

    private final RecipeRepository recipeRepository;
    private final MealPlanRepository mealPlanRepository;

    public List<String> getAvailableActions() {
        return List.of("add_recipe_to_menu", "remove_recipe_from_menu", "list_my_recipes");
    }

    public Map<String, Object> executeAction(String actionName, Map<String, Object> params, UUID userId) {
        return switch (actionName) {
            case "add_recipe_to_menu" -> addRecipeToMenu(params, userId);
            case "remove_recipe_from_menu" -> removeRecipeFromMenu(params, userId);
            case "list_my_recipes" -> listUserRecipes(userId);
            default -> Map.of("success", false, "error", "Unknown action: " + actionName);
        };
    }

    private Map<String, Object> addRecipeToMenu(Map<String, Object> params, UUID userId) {
        String recipeIdStr = (String) params.get("recipeId");
        if (recipeIdStr == null) return Map.of("success", false, "error", "Recipe ID is required");
        UUID recipeId = UUID.fromString(recipeIdStr);
        Recipe recipe = recipeRepository.findById(recipeId).orElse(null);
        if (recipe == null) return Map.of("success", false, "error", "Recipe not found");
        String mealType = (String) params.getOrDefault("mealType", "dinner");
        MealPlan mp = MealPlan.builder().userId(userId).recipeId(recipeId).mealType(mealType)
                .servingDate(String.valueOf(System.currentTimeMillis() / 1000)).build();
        mealPlanRepository.save(mp);
        return Map.of("success", true, "data", Map.of("mealPlanId", mp.getId(),
                "recipeName", recipe.getMealName(), "mealType", mealType,
                "message", "Added \"" + recipe.getMealName() + "\" to your " + mealType + " menu!"));
    }

    private Map<String, Object> removeRecipeFromMenu(Map<String, Object> params, UUID userId) {
        String id = (String) params.get("mealPlanId");
        if (id == null) return Map.of("success", false, "error", "Meal plan ID is required");
        UUID mpId = UUID.fromString(id);
        if (mealPlanRepository.findByIdAndUserId(mpId, userId).isEmpty())
            return Map.of("success", false, "error", "Meal plan not found");
        mealPlanRepository.deleteById(mpId);
        return Map.of("success", true, "data", Map.of("message", "Recipe removed from menu"));
    }

    private Map<String, Object> listUserRecipes(UUID userId) {
        List<Recipe> recipes = recipeRepository.findByUserId(userId);
        List<Map<String, Object>> list = recipes.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId()); m.put("name", r.getMealName());
            if (r.getImageUrl() != null) m.put("image", Map.of("url", r.getImageUrl()));
            return m;
        }).toList();
        return Map.of("success", true, "data", Map.of("recipes", list, "count", list.size()));
    }
}
