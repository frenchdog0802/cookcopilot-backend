package com.lardermind.service;

import com.lardermind.common.GlobalExceptionHandler.*;
import com.lardermind.entity.Folder;
import com.lardermind.entity.Ingredient;
import com.lardermind.entity.Recipe;
import com.lardermind.entity.RecipeIngredient;
import com.lardermind.repository.IngredientRepository;
import com.lardermind.repository.RecipeIngredientRepository;
import com.lardermind.repository.RecipeRepository;
import com.lardermind.unit.UnitConverter;
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
    private final StockImageService stockImageService;
    private final FolderService folderService;

    @Transactional
    public Map<String, Object> createRecipe(UUID userId, String mealName, String instructions,
                                             UUID folderId, Map<String, String> image,
                                             List<Map<String, Object>> ingredients) {
        Map<String, String> resolvedImage = resolveImageOrStock(mealName, image);
        Recipe recipe = Recipe.builder()
                .userId(userId)
                .folderId(folderId)
                .mealName(mealName)
                .instructions(instructions)
                .imageUrl(resolvedImage != null ? resolvedImage.get("url") : null)
                .imagePublicId(resolvedImage != null ? resolvedImage.get("public_id") : null)
                .build();
        recipe = recipeRepository.save(recipe);

        List<Map<String, Object>> savedIngredients = new ArrayList<>();
        if (ingredients != null) {
            for (Map<String, Object> item : ingredients) {
                savedIngredients.add(saveRecipeIngredient(recipe.getId(), item));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", recipe.getId());
        result.put("meal_name", recipe.getMealName());
        result.put("instructions", resolveInstructions(recipe));
        result.put("ingredients", savedIngredients);
        result.put("image", buildImageMap(recipe));
        return result;
    }

    public List<Map<String, Object>> getAllRecipes(UUID userId) {
        List<Recipe> recipes = recipeRepository.findByUserId(userId);
        if (recipes.isEmpty()) {
            return List.of();
        }

        List<UUID> recipeIds = recipes.stream().map(Recipe::getId).toList();
        Map<UUID, List<Map<String, Object>>> ingredientsByRecipeId = getIngredientsForRecipes(recipeIds);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Recipe recipe : recipes) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", recipe.getId());
            r.put("folder_id", recipe.getFolderId());
            r.put("meal_name", recipe.getMealName());
            r.put("instructions", resolveInstructions(recipe));
            r.put("ingredients", ingredientsByRecipeId.getOrDefault(recipe.getId(), List.of()));
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
        r.put("instructions", resolveInstructions(recipe));
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

        recipeIngredientRepository.deleteByRecipeId(id);
        if (ingredients != null) {
            for (Map<String, Object> item : ingredients) {
                saveRecipeIngredient(recipe.getId(), item);
            }
        }

        List<Map<String, Object>> ingredientList = getIngredientsForRecipe(recipe.getId());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", recipe.getId());
        r.put("meal_name", recipe.getMealName());
        r.put("instructions", resolveInstructions(recipe));
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

    private Map<String, Object> saveRecipeIngredient(UUID recipeId, Map<String, Object> item) {
        Ingredient ing = resolveOrCreateIngredient(item);
        String inputUnit = item.get("unit") != null ? item.get("unit").toString() : UnitConverter.resolveBaseUnit(ing);
        double baseQty = UnitConverter.toIngredientBase(ing, toDouble(item.get("quantity")), inputUnit);
        String baseUnit = UnitConverter.resolveBaseUnit(ing);

        RecipeIngredient ri = RecipeIngredient.builder()
                .recipeId(recipeId)
                .ingredientId(ing.getId())
                .quantity(baseQty)
                .unit(baseUnit)
                .build();
        recipeIngredientRepository.save(ri);

        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("id", ri.getId());
        saved.put("name", ing.getName());
        saved.put("quantity", baseQty);
        saved.put("unit", baseUnit);
        saved.put("ingredient_id", ing.getId());
        return saved;
    }

    private Ingredient resolveOrCreateIngredient(Map<String, Object> item) {
        if (item.get("ingredient_id") != null) {
            UUID id = UUID.fromString(item.get("ingredient_id").toString());
            return ingredientRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
        }
        String name = (String) item.get("name");
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Ingredient name is required");
        }
        return ingredientRepository.findByName(name)
                .orElseGet(() -> {
                    Ingredient created = UnitConverter.inferAndBuild(name, (String) item.get("unit"));
                    UnitConverter.applyDefaults(created);
                    return ingredientRepository.save(created);
                });
    }

    private List<Map<String, Object>> getIngredientsForRecipe(UUID recipeId) {
        return getIngredientsForRecipes(List.of(recipeId)).getOrDefault(recipeId, List.of());
    }

    private Map<UUID, List<Map<String, Object>>> getIngredientsForRecipes(Collection<UUID> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return Map.of();
        }

        List<RecipeIngredient> recipeIngredients = recipeIngredientRepository.findByRecipeIdIn(recipeIds);
        if (recipeIngredients.isEmpty()) {
            return Map.of();
        }

        Set<UUID> ingredientIds = new HashSet<>();
        for (RecipeIngredient ri : recipeIngredients) {
            ingredientIds.add(ri.getIngredientId());
        }

        Map<UUID, Ingredient> ingredientsById = new HashMap<>();
        for (Ingredient ingredient : ingredientRepository.findAllById(ingredientIds)) {
            ingredientsById.put(ingredient.getId(), ingredient);
        }

        Map<UUID, List<Map<String, Object>>> grouped = new HashMap<>();
        for (RecipeIngredient ri : recipeIngredients) {
            Ingredient ing = ingredientsById.get(ri.getIngredientId());
            if (ing == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ri.getId());
            m.put("ingredient_id", ing.getId());
            m.put("name", ing.getName());
            m.put("quantity", ri.getQuantity());
            m.put("unit", ri.getUnit() != null ? ri.getUnit() : UnitConverter.resolveBaseUnit(ing));
            m.put("unit_kind", UnitConverter.resolveKind(ing).toApiValue());
            m.put("base_unit", UnitConverter.resolveBaseUnit(ing));
            m.put("default_display_unit", UnitConverter.resolveDisplayUnit(ing));
            grouped.computeIfAbsent(ri.getRecipeId(), ignored -> new ArrayList<>()).add(m);
        }
        return grouped;
    }

    private Map<String, String> buildImageMap(Recipe recipe) {
        if (recipe.getImageUrl() == null || recipe.getImageUrl().isBlank()) return null;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("url", recipe.getImageUrl());
        m.put("public_id", recipe.getImagePublicId());
        return m;
    }

    /**
     * Prefer a user-provided image; otherwise fetch a related stock photo by meal name
     * and store it on Cloudinary when possible. User uploads can still replace it later.
     */
    Map<String, String> resolveImageOrStock(String mealName, Map<String, String> image) {
        if (image != null) {
            String url = image.get("url");
            if (url != null && !url.isBlank()) {
                return image;
            }
        }
        return stockImageService.resolveStockImage(mealName).orElse(null);
    }

    /** Fills a recipe with a stock image when it has none (e.g. AI tool create path). */
    public void applyStockImageIfMissing(Recipe recipe) {
        if (recipe == null) {
            return;
        }
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isBlank()) {
            return;
        }
        stockImageService.resolveStockImage(recipe.getMealName()).ifPresent(stock -> {
            recipe.setImageUrl(stock.get("url"));
            recipe.setImagePublicId(stock.get("public_id"));
            recipeRepository.save(recipe);
        });
    }

    /** Moves a recipe into Breakfast / Lunch / Dinner when scheduled for that meal type. */
    public void moveRecipeToMealTypeFolder(UUID userId, UUID recipeId, String mealType) {
        if (userId == null || recipeId == null || mealType == null || mealType.isBlank()) {
            return;
        }
        String folderName = switch (mealType.trim().toLowerCase(Locale.ROOT)) {
            case "breakfast" -> "Breakfast";
            case "lunch" -> "Lunch";
            case "dinner" -> "Dinner";
            default -> null;
        };
        if (folderName == null) {
            return;
        }
        Recipe recipe = recipeRepository.findById(recipeId).orElse(null);
        if (recipe == null || !userId.equals(recipe.getUserId())) {
            return;
        }
        Folder folder = folderService.findOrCreateByName(userId, folderName);
        recipe.setFolderId(folder.getId());
        recipeRepository.save(recipe);
    }

    private String resolveInstructions(Recipe recipe) {
        if (recipe.getInstructions() != null && !recipe.getInstructions().isBlank()) {
            return recipe.getInstructions();
        }
        List<String> steps = recipe.getSteps();
        if (steps != null && !steps.isEmpty()) {
            return String.join("\n", steps);
        }
        return recipe.getInstructions();
    }

    private Double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) return Double.parseDouble((String) val);
        return 0.0;
    }
}
