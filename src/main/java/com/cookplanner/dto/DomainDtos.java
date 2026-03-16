package com.cookplanner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// ── Recipe DTOs ──

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class RecipeRequest {
    private UUID folder_id;
    private String meal_name;
    private String instructions;
    private Map<String, String> image; // { url, public_id }
    private List<RecipeIngredientItem> ingredients;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecipeIngredientItem {
        private String name;
        private Double quantity;
        private String unit;
    }
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class RecipeResponse {
    private UUID id;
    private UUID folder_id;
    private String meal_name;
    private String instructions;
    private List<IngredientItemResponse> ingredients;
    private Map<String, String> image;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class IngredientItemResponse {
        private UUID id;
        private String name;
        private Double quantity;
        private String unit;
    }
}

// ── Folder DTOs ──

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class FolderRequest {
    private String name;
    private String color;
    private String icon;
}

// ── Ingredient DTOs ──
// Ingredient entities are returned directly or via the repository.

// ── PantryItem DTOs ──

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class PantryItemRequest {
    private String name;
    private UUID ingredient_id;
    private Double quantity;
    private String unit;
    private String notes;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class PantryItemResponse {
    private UUID id;
    private UUID user_id;
    private UUID ingredient_id;
    private Double quantity;
    private Double item_to_buy;
    private Double item_planned;
    private String name;
    private String unit;
    private String notes;
}

// ── ShoppingList DTOs ──

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class ShoppingListItemRequest {
    private String name;
    private UUID ingredient_id;
    private Double quantity;
    private String unit;
    private Boolean checked;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class ShoppingListItemResponse {
    private UUID id;
    private UUID user_id;
    private UUID ingredient_id;
    private Double quantity;
    private String name;
    private String unit;
    private Boolean checked;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class ShoppingListUpdateResult {
    private UUID id;
    private UUID pantry_item_id;
    private Double new_quantity;
    private Double new_item_to_buy;
    private Boolean checked;
}

// ── MealPlan DTOs ──

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class MealPlanRequest {
    private UUID recipe_id;
    private String meal_type;
    private String serving_date;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class MealPlanResponse {
    private UUID id;
    private UUID recipe_id;
    private String meal_name;
    private Map<String, String> image_url;
    private String meal_type;
    private String serving_date;
    private List<Map<String, Object>> notEnoughItems;
}

// ── Chat DTOs ──

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class ChatRequest {
    private String message;
    private RecipeContext recipeContext;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecipeContext {
        private String recipeId;
        private String recipeName;
    }
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class ChatResponse {
    private String type;
    private String message;
    private Object data;
}

// ── Upload DTOs ──

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class ImageUploadResponse {
    private String image_url;
    private String public_id;
}

// ── Bulk Update DTO ──

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class PantryItemBulkUpdateItem {
    private UUID id;
    private Double quantity;
    private String unit;
    private String notes;
}
