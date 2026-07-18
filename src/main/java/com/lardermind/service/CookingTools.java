package com.lardermind.service;

import com.lardermind.entity.Ingredient;
import com.lardermind.entity.Folder;
import com.lardermind.entity.MealPlan;
import com.lardermind.entity.PantryItem;
import com.lardermind.entity.Recipe;
import com.lardermind.entity.RecipeIngredient;
import com.lardermind.entity.UserPreference;
import com.lardermind.repository.IngredientRepository;
import com.lardermind.repository.MealPlanRepository;
import com.lardermind.repository.PantryItemRepository;
import com.lardermind.repository.RecipeIngredientRepository;
import com.lardermind.repository.RecipeRepository;
import com.lardermind.unit.UnitConverter;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CookingTools {

    private static final String DEFAULT_MEAL_TYPE = "dinner";

    private final ToolResultCollector toolResultCollector;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final MealPlanRepository mealPlanRepository;
    private final IngredientRepository ingredientRepository;
    private final PantryItemRepository pantryItemRepository;
    private final ShoppingListService shoppingListService;
    private final MealPlanService mealPlanService;
    private final PantryItemService pantryItemService;
    private final RecipeService recipeService;
    private final RecipeImportService recipeImportService;
    private final FolderService folderService;
    private final UserPreferenceService userPreferenceService;

    public record IngredientInput(String name, String quantity, String unit, String note) {
    }

    public record ShoppingItemInput(String name, String quantity, String unit) {
    }

    public record PantryItemInput(String name, String quantity, String unit, String notes) {
    }

    public record MealPlanEntry(String recipeId, String recipeName, String servingDate, String mealType) {
    }

    // ── Preferences ──

    @Tool("Get the user's food preferences: allergies, dislikes, likes, dietary restrictions, and household notes. Call this before suggesting or planning meals.")
    public String getPreferences(@ToolMemoryId UUID userId) {
        UserPreference prefs = userPreferenceService.getOrCreate(userId);
        return summarizePreferences(prefs);
    }

    @Tool("""
            Update the user's food preferences. Only pass fields you want to change; omit fields to leave them unchanged.
            CRITICAL: Only save what the user explicitly said. Never invent allergies, disliked ingredients, or likes.
            Taste rules like "not sour" / "not spicy" belong in dietaryRestrictions or notes — do NOT invent ingredient dislikes (e.g. peppers, eggplant).
            allergies/dislikes/likes/dietaryRestrictions replace the whole list when provided (pass an empty list to clear).
            householdNotes captures family member preferences (e.g. Dad no spicy, kid loves pasta).
            measurementUnit is metric or imperial.
            """)
    public String updatePreferences(
            @ToolMemoryId UUID userId,
            List<String> allergies,
            List<String> dislikes,
            List<String> likes,
            List<String> dietaryRestrictions,
            String householdNotes,
            String measurementUnit,
            String notes) {
        UserPreference prefs = userPreferenceService.update(userId, new UserPreferenceService.PreferenceUpdate(
                allergies,
                dislikes,
                likes,
                dietaryRestrictions,
                householdNotes,
                measurementUnit,
                notes
        ));
        Map<String, Object> data = userPreferenceService.toMap(prefs);
        data.put("message", "Updated user preferences.");
        toolResultCollector.addResult(userId, "updatePreferences", data);
        return "Preferences updated. " + summarizePreferences(prefs);
    }

    private String summarizePreferences(UserPreference prefs) {
        return "Allergies: " + joinOrNone(prefs.getAllergies())
                + "; Dislikes: " + joinOrNone(prefs.getDislikes())
                + "; Likes: " + joinOrNone(prefs.getLikes())
                + "; Dietary restrictions: " + joinOrNone(prefs.getDietaryRestrictions())
                + "; Household notes: " + blankToNone(prefs.getHouseholdNotes())
                + "; Units: " + (prefs.getMeasurementUnit() != null ? prefs.getMeasurementUnit() : "metric")
                + "; Notes: " + blankToNone(prefs.getNotes());
    }

    private static String joinOrNone(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join(", ", values);
    }

    private static String blankToNone(String value) {
        return value == null || value.isBlank() ? "none" : value.trim();
    }

    // ── Recipes ──

    @Tool("List all recipes belonging to the current user")
    public String listMyRecipes(@ToolMemoryId UUID userId) {
        List<Recipe> recipes = recipeRepository.findByUserId(userId);
        if (recipes.isEmpty()) {
            return "You have no saved recipes yet.";
        }
        return recipes.stream()
                .map(recipe -> recipe.getMealName() + " (id: " + recipe.getId() + ")")
                .collect(Collectors.joining(", "));
    }

    @Tool("Get full details of a recipe by ID including ingredients and steps")
    public String getRecipeDetails(@ToolMemoryId UUID userId, String recipeId) {
        UUID parsedRecipeId = parseUuid(recipeId, "recipe ID");
        if (parsedRecipeId == null) {
            return "Invalid recipe ID format.";
        }
        if (recipeRepository.findByIdAndUserId(parsedRecipeId, userId).isEmpty()) {
            return "Recipe not found or does not belong to you.";
        }
        Map<String, Object> recipe = recipeService.getRecipeById(parsedRecipeId);
        return "Recipe: " + recipe.get("meal_name")
                + " | ingredients: " + recipe.get("ingredients")
                + " | instructions: " + recipe.get("instructions");
    }

    @Tool("Create a new recipe for the current user. Create ONE recipe per call. Pass at most 8 ingredients and 12 steps.")
    public String createRecipe(@ToolMemoryId UUID userId, String name, String description, List<IngredientInput> ingredients, List<String> steps) {
        if (ingredients != null && ingredients.size() > ChatLimits.MAX_TOOL_LIST_SIZE) {
            return "Too many ingredients. Maximum is " + ChatLimits.MAX_TOOL_LIST_SIZE + ".";
        }
        if (steps != null && steps.size() > ChatLimits.MAX_RECIPE_STEPS) {
            return "Too many steps. Maximum is " + ChatLimits.MAX_RECIPE_STEPS + ". Create the recipe with fewer steps, or split into another recipe.";
        }

        List<String> resolvedSteps = steps != null ? steps : List.of();
        Folder uncategorized = folderService.getOrCreateUncategorized(userId);
        Recipe recipe = Recipe.builder()
                .userId(userId)
                .folderId(uncategorized.getId())
                .mealName(name)
                .description(description)
                .steps(resolvedSteps)
                .instructions(resolvedSteps.isEmpty() ? null : String.join("\n", resolvedSteps))
                .build();
        recipe = recipeRepository.save(recipe);
        recipeService.applyStockImageIfMissing(recipe);

        int ingredientCount = saveRecipeIngredients(recipe.getId(), ingredients);

        Map<String, Object> data = recipeCreatedData(recipe, ingredientCount, resolvedSteps);
        toolResultCollector.addResult(userId, "createRecipe", data);
        return "Created recipe \"" + recipe.getMealName() + "\" with " + ingredientCount + " ingredients.";
    }

    @Tool("Update an existing recipe's name, description, ingredients, or steps")
    public String updateRecipe(@ToolMemoryId UUID userId, String recipeId, String name, String description,
                               List<IngredientInput> ingredients, List<String> steps) {
        UUID parsedRecipeId = parseUuid(recipeId, "recipe ID");
        if (parsedRecipeId == null) {
            return "Invalid recipe ID format.";
        }
        if (recipeRepository.findByIdAndUserId(parsedRecipeId, userId).isEmpty()) {
            return "Recipe not found or does not belong to you.";
        }

        List<Map<String, Object>> ingredientMaps = toIngredientMaps(ingredients);
        List<String> resolvedSteps = steps != null ? steps : List.of();
        String instructions = resolvedSteps.isEmpty() ? description : String.join("\n", resolvedSteps);

        Map<String, Object> updated = recipeService.updateRecipe(
                parsedRecipeId,
                name,
                instructions,
                null,
                null,
                ingredientMaps.isEmpty() ? null : ingredientMaps);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recipeId", updated.get("id"));
        data.put("recipeName", updated.get("meal_name"));
        data.put("message", "Updated recipe \"" + updated.get("meal_name") + "\"");
        toolResultCollector.addResult(userId, "updateRecipe", data);
        return data.get("message").toString();
    }

    @Tool("Import a recipe from a web URL and save it for the current user")
    public String importRecipeFromUrl(@ToolMemoryId UUID userId, String url) {
        String safeUrl = RecipeImportService.sanitizeUrlForLog(url);
        log.info("importRecipeFromUrl started userId={} url={}", userId, safeUrl);
        try {
            Map<String, Object> extracted = recipeImportService.extractRecipeFromUrl(url);
            String name = extracted.get("name").toString();
            String description = extracted.containsKey("description") ? stringValue(extracted.get("description")) : "";
            List<IngredientInput> ingredients = RecipeImportService.toIngredientInputs(extracted);
            List<String> steps = RecipeImportService.toSteps(extracted);

            List<String> resolvedSteps = steps.isEmpty() ? List.of(description) : steps;
            Folder uncategorized = folderService.getOrCreateUncategorized(userId);
            Recipe recipe = Recipe.builder()
                    .userId(userId)
                    .folderId(uncategorized.getId())
                    .mealName(name)
                    .description(description)
                    .steps(resolvedSteps)
                    .instructions(String.join("\n", resolvedSteps))
                    .build();
            recipe = recipeRepository.save(recipe);
            recipeService.applyStockImageIfMissing(recipe);
            int ingredientCount = saveRecipeIngredients(recipe.getId(), ingredients);

            Map<String, Object> data = recipeCreatedData(recipe, ingredientCount, resolvedSteps);
            data.put("sourceUrl", url);
            data.put("imported", true);
            data.put("folderId", uncategorized.getId());
            toolResultCollector.addResult(userId, "importRecipeFromUrl", data);
            log.info("importRecipeFromUrl succeeded userId={} url={} recipeId={} folderId={} name={} ingredients={}",
                    userId, safeUrl, recipe.getId(), uncategorized.getId(), name, ingredientCount);
            return "Imported recipe \"" + name + "\" from URL with " + ingredientCount + " ingredients.";
        } catch (IllegalArgumentException ex) {
            log.warn("importRecipeFromUrl failed userId={} url={} reason={}", userId, safeUrl, ex.getMessage());
            return ex.getMessage();
        } catch (RuntimeException ex) {
            log.error("importRecipeFromUrl unexpected error userId={} url={}", userId, safeUrl, ex);
            throw ex;
        }
    }

    // ── Meal plans ──

    @Tool("List meal plans for the user, optionally filtered by date range (YYYY-MM-DD). Returns date, meal type, and recipe name — use this to verify scheduling before summarizing.")
    public String listMealPlans(@ToolMemoryId UUID userId, String fromDate, String toDate) {
        List<Map<String, Object>> plans = mealPlanService.getAllMealPlans(userId);
        List<Map<String, Object>> filtered = plans.stream()
                .filter(plan -> withinDateRange(stringValue(plan.get("serving_date")), fromDate, toDate))
                .sorted((a, b) -> {
                    String da = stringValue(a.get("serving_date"));
                    String db = stringValue(b.get("serving_date"));
                    int byDate = da.compareTo(db);
                    if (byDate != 0) {
                        return byDate;
                    }
                    return stringValue(a.get("meal_type")).compareTo(stringValue(b.get("meal_type")));
                })
                .toList();
        if (filtered.isEmpty()) {
            return "No meal plans scheduled"
                    + (fromDate != null || toDate != null ? " in that date range." : ".");
        }
        return filtered.stream()
                .map(plan -> plan.get("serving_date") + " " + plan.get("meal_type")
                        + ": " + plan.get("meal_name")
                        + " (planId: " + plan.get("id") + ", recipeId: " + plan.get("recipe_id") + ")")
                .collect(Collectors.joining("\n"));
    }

    @Tool("Add one of the user's recipes to their meal menu on a specific date")
    public String addRecipeToMenu(@ToolMemoryId UUID userId, String recipeId, String servingDate, String mealType) {
        UUID parsedRecipeId = parseUuid(recipeId, "recipe ID");
        if (parsedRecipeId == null) {
            return "Invalid recipe ID format.";
        }

        if (recipeRepository.findByIdAndUserId(parsedRecipeId, userId).isEmpty()) {
            return "Recipe not found or does not belong to you.";
        }

        String resolvedDate = resolveServingDate(servingDate);
        if (resolvedDate == null) {
            return "Invalid serving date. Use YYYY-MM-DD format.";
        }
        String resolvedMealType = mealType != null && !mealType.isBlank() ? mealType : DEFAULT_MEAL_TYPE;

        Map<String, Object> created = mealPlanService.createMealPlan(userId, parsedRecipeId, resolvedMealType, resolvedDate);
        Map<String, Object> data = mealPlanData(created, "Added \"" + created.get("meal_name") + "\" to "
                + resolvedMealType + " on " + resolvedDate);
        toolResultCollector.addResult(userId, "addRecipeToMenu", data);
        return data.get("message").toString();
    }

    @Tool("Schedule multiple meals at once. Pass at most 8 meals per call; call again for more. Prefer recipeName matching listMyRecipes; also pass recipeId when known.")
    public String planMeals(@ToolMemoryId UUID userId, List<MealPlanEntry> meals) {
        if (meals == null || meals.isEmpty()) {
            return "No meals provided.";
        }
        if (meals.size() > ChatLimits.MAX_TOOL_LIST_SIZE) {
            return "Too many meals. Maximum is " + ChatLimits.MAX_TOOL_LIST_SIZE + ".";
        }

        List<Map<String, Object>> scheduled = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (MealPlanEntry entry : meals) {
            if (entry == null) {
                skipped.add("null entry");
                continue;
            }
            UUID recipeId = resolveRecipeReference(userId, entry.recipeId(), entry.recipeName());
            if (recipeId == null) {
                skipped.add("recipe not found id=" + entry.recipeId() + " name=" + entry.recipeName());
                continue;
            }
            String servingDate = resolveServingDate(entry.servingDate());
            if (servingDate == null) {
                skipped.add("invalid date=" + entry.servingDate() + " for recipeId=" + recipeId);
                continue;
            }
            String mealType = entry.mealType() != null && !entry.mealType().isBlank()
                    ? entry.mealType() : DEFAULT_MEAL_TYPE;
            Map<String, Object> created = mealPlanService.createMealPlan(userId, recipeId, mealType, servingDate);
            scheduled.add(created);
        }

        if (scheduled.isEmpty()) {
            String detail = skipped.isEmpty() ? "" : " Skipped: " + String.join("; ", skipped);
            return "Could not schedule any meals. Check recipe IDs/names and dates." + detail;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mealsScheduled", scheduled.size());
        data.put("meals", scheduled);
        String message = "Scheduled " + scheduled.size() + "/" + meals.size() + " meal(s): "
                + scheduled.stream()
                .map(m -> m.get("serving_date") + " " + m.get("meal_type") + "=" + m.get("meal_name"))
                .collect(Collectors.joining("; "));
        if (!skipped.isEmpty()) {
            message += " Skipped " + skipped.size() + ": " + String.join("; ", skipped);
        }
        data.put("message", message);
        toolResultCollector.addResult(userId, "planMeals", data);
        return message;
    }

    @Tool("Update an existing meal plan's date, meal type, or recipe")
    public String updateMealPlan(@ToolMemoryId UUID userId, String mealPlanId, String servingDate, String mealType, String recipeId) {
        UUID parsedMealPlanId = parseUuid(mealPlanId, "meal plan ID");
        if (parsedMealPlanId == null) {
            return "Invalid meal plan ID format.";
        }

        if (mealPlanRepository.findByIdAndUserId(parsedMealPlanId, userId).isEmpty()) {
            return "Meal plan not found.";
        }

        MealPlan updates = MealPlan.builder().build();
        if (servingDate != null && !servingDate.isBlank()) {
            String resolved = resolveServingDate(servingDate);
            if (resolved == null) {
                return "Invalid serving date. Use YYYY-MM-DD format.";
            }
            updates.setServingDate(resolved);
        }
        if (mealType != null && !mealType.isBlank()) {
            updates.setMealType(mealType);
        }
        if (recipeId != null && !recipeId.isBlank()) {
            UUID parsedRecipeId = parseUuid(recipeId, "recipe ID");
            if (parsedRecipeId == null) {
                return "Invalid recipe ID format.";
            }
            if (recipeRepository.findByIdAndUserId(parsedRecipeId, userId).isEmpty()) {
                return "Recipe not found or does not belong to you.";
            }
            updates.setRecipeId(parsedRecipeId);
        }

        MealPlan updated = mealPlanService.updateMealPlan(parsedMealPlanId, updates);
        recipeRepository.findById(updated.getRecipeId()).ifPresent(recipe -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("mealPlanId", updated.getId());
            data.put("recipeName", recipe.getMealName());
            data.put("mealType", updated.getMealType());
            data.put("servingDate", updated.getServingDate());
            data.put("message", "Updated meal plan for \"" + recipe.getMealName() + "\".");
            toolResultCollector.addResult(userId, "updateMealPlan", data);
        });

        return "Meal plan updated.";
    }

    @Tool("Remove a recipe from the user's meal menu by meal plan ID")
    public String removeRecipeFromMenu(@ToolMemoryId UUID userId, String mealPlanId) {
        UUID parsedMealPlanId = parseUuid(mealPlanId, "meal plan ID");
        if (parsedMealPlanId == null) {
            return "Invalid meal plan ID format.";
        }

        if (mealPlanRepository.findByIdAndUserId(parsedMealPlanId, userId).isEmpty()) {
            return "Meal plan not found.";
        }

        mealPlanRepository.deleteById(parsedMealPlanId);
        Map<String, Object> data = Map.of("message", "Recipe removed from menu", "mealPlanId", parsedMealPlanId);
        toolResultCollector.addResult(userId, "removeRecipeFromMenu", data);
        return data.get("message").toString();
    }

    @Tool("Clear many meal plans in one call by optional date range (YYYY-MM-DD). Use this instead of calling removeRecipeFromMenu repeatedly when moving/rescheduling a menu (e.g. clear all 2025 plans). Omit fromDate/toDate to clear ALL meal plans.")
    public String clearMealPlans(@ToolMemoryId UUID userId, String fromDate, String toDate) {
        String from = resolveOptionalDate(fromDate);
        if (fromDate != null && !fromDate.isBlank() && from == null) {
            return "Invalid fromDate. Use YYYY-MM-DD format.";
        }
        String to = resolveOptionalDate(toDate);
        if (toDate != null && !toDate.isBlank() && to == null) {
            return "Invalid toDate. Use YYYY-MM-DD format.";
        }

        List<MealPlan> plans = mealPlanRepository.findByUserId(userId);
        List<UUID> toDelete = plans.stream()
                .filter(plan -> withinDateRange(plan.getServingDate(), from, to))
                .map(MealPlan::getId)
                .toList();

        if (toDelete.isEmpty()) {
            return "No meal plans found to clear"
                    + (from != null || to != null ? " in that date range." : ".");
        }

        mealPlanRepository.deleteAllById(toDelete);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("removedCount", toDelete.size());
        data.put("fromDate", from);
        data.put("toDate", to);
        String message = "Cleared " + toDelete.size() + " meal plan(s)"
                + (from != null || to != null
                ? " from " + (from != null ? from : "…") + " to " + (to != null ? to : "…")
                : "")
                + ".";
        data.put("message", message);
        toolResultCollector.addResult(userId, "clearMealPlans", data);
        return message;
    }

    // ── Pantry ──

    @Tool("List all items in the user's pantry inventory")
    public String listPantry(@ToolMemoryId UUID userId) {
        List<Map<String, Object>> items = pantryItemService.getAllPantryItems(userId);
        if (items.isEmpty()) {
            return "Pantry is empty.";
        }
        return items.stream()
                .map(item -> item.get("name") + ": " + item.get("quantity") + " " + item.get("unit")
                        + " (id: " + item.get("id") + ")")
                .collect(Collectors.joining(", "));
    }

    @Tool("Add items to the user's pantry inventory. Pass at most 8 items per call; call again for more.")
    public String addPantryItems(@ToolMemoryId UUID userId, List<PantryItemInput> items) {
        if (items == null || items.isEmpty()) {
            return "No items provided.";
        }
        if (items.size() > ChatLimits.MAX_TOOL_LIST_SIZE) {
            return "Too many items. Maximum is " + ChatLimits.MAX_TOOL_LIST_SIZE + ".";
        }

        List<Map<String, Object>> payload = new ArrayList<>();
        List<Map<String, Object>> responseItems = new ArrayList<>();

        for (PantryItemInput item : items) {
            if (item == null || item.name() == null || item.name().isBlank()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", item.name());
            entry.put("quantity", parseQuantity(item.quantity()));
            entry.put("unit", item.unit() != null ? item.unit() : "");
            entry.put("notes", item.notes() != null ? item.notes() : "");
            payload.add(entry);
            responseItems.add(Map.of(
                    "name", item.name(),
                    "quantity", entry.get("quantity"),
                    "unit", entry.get("unit")));
        }

        if (payload.isEmpty()) {
            return "No items provided.";
        }

        pantryItemService.insertAllPantryItems(userId, payload);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemsAdded", responseItems.size());
        data.put("items", responseItems);
        data.put("message", "Added " + responseItems.size() + " item(s) to pantry.");
        toolResultCollector.addResult(userId, "addPantryItems", data);
        return data.get("message").toString();
    }

    @Tool("Update a pantry item's quantity, unit, or notes")
    public String updatePantryItem(@ToolMemoryId UUID userId, String pantryItemId, String quantity, String unit, String notes) {
        UUID parsedId = parseUuid(pantryItemId, "pantry item ID");
        if (parsedId == null) {
            return "Invalid pantry item ID format.";
        }

        PantryItem existing = pantryItemRepository.findById(parsedId).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return "Pantry item not found.";
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        if (quantity != null && !quantity.isBlank()) {
            updates.put("quantity", parseQuantity(quantity));
        }
        if (unit != null) {
            updates.put("unit", unit);
        }
        if (notes != null) {
            updates.put("notes", notes);
        }

        PantryItem updated = pantryItemService.updatePantryItem(parsedId, updates);
        Ingredient ing = ingredientRepository.findById(updated.getIngredientId()).orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pantryItemId", updated.getId());
        data.put("name", ing != null ? ing.getName() : "Unknown");
        data.put("quantity", updated.getQuantity());
        data.put("unit", updated.getUnit());
        data.put("message", "Updated pantry item.");
        toolResultCollector.addResult(userId, "updatePantryItem", data);
        return data.get("message").toString();
    }

    @Tool("Remove an item from the user's pantry")
    public String removePantryItem(@ToolMemoryId UUID userId, String pantryItemId) {
        UUID parsedId = parseUuid(pantryItemId, "pantry item ID");
        if (parsedId == null) {
            return "Invalid pantry item ID format.";
        }

        PantryItem existing = pantryItemRepository.findById(parsedId).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return "Pantry item not found.";
        }

        pantryItemService.deletePantryItem(parsedId);
        Map<String, Object> data = Map.of("message", "Removed pantry item.", "pantryItemId", parsedId);
        toolResultCollector.addResult(userId, "removePantryItem", data);
        return data.get("message").toString();
    }

    @Tool("Organize pantry by merging duplicate ingredients and normalizing entries")
    public String organizePantry(@ToolMemoryId UUID userId) {
        List<PantryItem> items = pantryItemRepository.findByUserId(userId);
        if (items.isEmpty()) {
            return "Pantry is already empty.";
        }

        Map<String, List<PantryItem>> grouped = new LinkedHashMap<>();
        for (PantryItem item : items) {
            Ingredient ing = ingredientRepository.findById(item.getIngredientId()).orElse(null);
            String key = ing != null ? ing.getName().toLowerCase(Locale.ROOT) : item.getId().toString();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        int merged = 0;
        int removed = 0;
        for (List<PantryItem> group : grouped.values()) {
            if (group.size() <= 1) {
                continue;
            }
            PantryItem keeper = group.get(0);
            Ingredient keeperIng = ingredientRepository.findById(keeper.getIngredientId()).orElse(null);
            double totalQty = 0;
            if (keeperIng != null) {
                String baseUnit = UnitConverter.resolveBaseUnit(keeperIng);
                for (PantryItem pi : group) {
                    totalQty += UnitConverter.toIngredientBaseLenient(
                            keeperIng,
                            pi.getQuantity() != null ? pi.getQuantity() : 0,
                            pi.getUnit() != null && !pi.getUnit().isBlank() ? pi.getUnit() : baseUnit);
                }
                keeper.setQuantity(totalQty);
                keeper.setUnit(baseUnit);
            } else {
                totalQty = group.stream()
                        .mapToDouble(pi -> pi.getQuantity() != null ? pi.getQuantity() : 0)
                        .sum();
                keeper.setQuantity(totalQty);
            }
            pantryItemRepository.save(keeper);
            merged++;
            for (int i = 1; i < group.size(); i++) {
                pantryItemRepository.deleteById(group.get(i).getId());
                removed++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mergedGroups", merged);
        data.put("removedDuplicates", removed);
        data.put("message", "Organized pantry: merged " + merged + " duplicate group(s), removed "
                + removed + " duplicate item(s).");
        toolResultCollector.addResult(userId, "organizePantry", data);
        return data.get("message").toString();
    }

    // ── Shopping ──

    @Tool("Add items to the user's shopping list. Pass at most 8 items per call; call again for more.")
    public String addItemsToShoppingList(@ToolMemoryId UUID userId, List<ShoppingItemInput> items) {
        if (items == null || items.isEmpty()) {
            return "No items provided.";
        }
        if (items.size() > ChatLimits.MAX_TOOL_LIST_SIZE) {
            return "Too many items. Maximum is " + ChatLimits.MAX_TOOL_LIST_SIZE + ".";
        }

        List<Map<String, Object>> payload = new ArrayList<>();
        List<Map<String, Object>> responseItems = new ArrayList<>();

        for (ShoppingItemInput item : items) {
            if (item == null || item.name() == null || item.name().isBlank()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", item.name());
            entry.put("quantity", item.quantity());
            entry.put("unit", item.unit() != null ? item.unit() : "");
            payload.add(entry);
            responseItems.add(entry);
        }

        if (payload.isEmpty()) {
            return "No items provided.";
        }

        shoppingListService.insertAllShoppingListItems(userId, payload);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemsAdded", responseItems.size());
        data.put("items", responseItems);
        toolResultCollector.addResult(userId, "addItemsToShoppingList", data);
        return "Added " + responseItems.size() + " items to your shopping list.";
    }

    // ── Suggestions ──

    @Tool("Suggest meals the user can cook based on pantry inventory and saved recipes")
    public String suggestMealsFromPantry(@ToolMemoryId UUID userId, boolean scheduleTopMatches, int maxSuggestions) {
        List<Map<String, Object>> pantry = pantryItemService.getAllPantryItems(userId);
        List<Recipe> recipes = recipeRepository.findByUserId(userId);

        if (recipes.isEmpty()) {
            return "No saved recipes to suggest from. Create or import recipes first.";
        }

        Map<String, Double> pantryByName = pantry.stream()
                .collect(Collectors.toMap(
                        item -> item.get("name").toString().toLowerCase(Locale.ROOT),
                        item -> toDouble(item.get("quantity")),
                        Double::sum));

        int limit = maxSuggestions > 0 ? Math.min(maxSuggestions, 10) : 5;
        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (Recipe recipe : recipes) {
            List<RecipeIngredient> ingredients = recipeIngredientRepository.findByRecipeId(recipe.getId());
            if (ingredients.isEmpty()) {
                continue;
            }
            int matched = 0;
            List<String> missing = new ArrayList<>();
            for (RecipeIngredient ri : ingredients) {
                Ingredient ing = ingredientRepository.findById(ri.getIngredientId()).orElse(null);
                if (ing == null) {
                    continue;
                }
                double available = pantryByName.getOrDefault(ing.getName().toLowerCase(Locale.ROOT), 0.0);
                double needed = ri.getQuantity() != null ? ri.getQuantity() : 0;
                if (available >= needed && needed > 0) {
                    matched++;
                } else if (available <= 0) {
                    missing.add(ing.getName());
                }
            }
            double score = ingredients.isEmpty() ? 0 : (double) matched / ingredients.size();
            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("recipeId", recipe.getId());
            suggestion.put("recipeName", recipe.getMealName());
            suggestion.put("matchScore", Math.round(score * 100));
            suggestion.put("missingIngredients", missing);
            suggestions.add(suggestion);
        }

        suggestions.sort((a, b) -> Double.compare(
                toDouble(b.get("matchScore")),
                toDouble(a.get("matchScore"))));

        List<Map<String, Object>> top = suggestions.stream().limit(limit).toList();
        if (top.isEmpty()) {
            return "No recipe suggestions available.";
        }

        List<Map<String, Object>> scheduled = new ArrayList<>();
        if (scheduleTopMatches && !top.isEmpty()) {
            String today = LocalDate.now().toString();
            Map<String, Object> best = top.get(0);
            UUID recipeId = (UUID) best.get("recipeId");
            Map<String, Object> created = mealPlanService.createMealPlan(userId, recipeId, DEFAULT_MEAL_TYPE, today);
            scheduled.add(created);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("suggestions", top);
        data.put("scheduled", scheduled);
        data.put("message", "Found " + top.size() + " meal suggestion(s).");
        toolResultCollector.addResult(userId, "suggestMealsFromPantry", data);

        return top.stream()
                .map(s -> s.get("recipeName") + " (" + s.get("matchScore") + "% match)")
                .collect(Collectors.joining(", "));
    }

    // ── Helpers ──

    private Map<String, Object> recipeCreatedData(Recipe recipe, int ingredientCount, List<String> steps) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recipeId", recipe.getId());
        data.put("recipeName", recipe.getMealName());
        data.put("ingredientCount", ingredientCount);
        data.put("steps", steps);
        return data;
    }

    private Map<String, Object> mealPlanData(Map<String, Object> created, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mealPlanId", created.get("id"));
        data.put("recipeName", created.get("meal_name"));
        data.put("mealType", created.get("meal_type"));
        data.put("servingDate", created.get("serving_date"));
        data.put("notEnoughItems", created.get("notEnoughItems"));
        data.put("message", message);
        return data;
    }

    private int saveRecipeIngredients(UUID recipeId, List<IngredientInput> ingredients) {
        int ingredientCount = 0;
        if (ingredients == null || ingredients.isEmpty()) {
            return ingredientCount;
        }
        Map<String, Ingredient> resolvedIngredients = resolveIngredients(ingredients);
        for (IngredientInput input : ingredients) {
            if (input == null || input.name() == null || input.name().isBlank()) {
                continue;
            }
            Ingredient ingredient = resolvedIngredients.get(input.name().toLowerCase(Locale.ROOT));
            String inputUnit = input.unit() != null && !input.unit().isBlank()
                    ? input.unit()
                    : UnitConverter.resolveBaseUnit(ingredient);
            double baseQty = UnitConverter.toIngredientBaseLenient(
                    ingredient, parseQuantity(input.quantity()), inputUnit);
            String baseUnit = UnitConverter.resolveBaseUnit(ingredient);
            RecipeIngredient recipeIngredient = RecipeIngredient.builder()
                    .recipeId(recipeId)
                    .ingredientId(ingredient.getId())
                    .quantity(baseQty)
                    .unit(baseUnit)
                    .note(input.note())
                    .build();
            recipeIngredientRepository.save(recipeIngredient);
            ingredientCount++;
        }
        return ingredientCount;
    }

    private List<Map<String, Object>> toIngredientMaps(List<IngredientInput> ingredients) {
        if (ingredients == null) {
            return List.of();
        }
        List<Map<String, Object>> maps = new ArrayList<>();
        for (IngredientInput input : ingredients) {
            if (input == null || input.name() == null || input.name().isBlank()) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", input.name());
            map.put("quantity", parseQuantity(input.quantity()));
            map.put("unit", input.unit());
            maps.add(map);
        }
        return maps;
    }

    private Map<String, Ingredient> resolveIngredients(List<IngredientInput> inputs) {
        List<String> names = inputs.stream()
                .filter(Objects::nonNull)
                .map(IngredientInput::name)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        Map<String, Ingredient> existingByName = ingredientRepository.findAllByNameInIgnoreCase(names).stream()
                .collect(Collectors.toMap(
                        ingredient -> ingredient.getName().toLowerCase(Locale.ROOT),
                        ingredient -> ingredient,
                        (left, right) -> left));

        Map<String, Ingredient> resolved = new HashMap<>();
        for (IngredientInput input : inputs) {
            if (input == null || input.name() == null || input.name().isBlank()) {
                continue;
            }
            String key = input.name().toLowerCase(Locale.ROOT);
            Ingredient ingredient = existingByName.get(key);
            if (ingredient == null) {
                Ingredient created = UnitConverter.inferAndBuild(
                        input.name(),
                        input.unit() != null ? input.unit() : "");
                UnitConverter.applyDefaults(created);
                ingredient = ingredientRepository.save(created);
                existingByName.put(key, ingredient);
            }
            resolved.put(key, ingredient);
        }
        return resolved;
    }

    private UUID parseUuid(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Resolve a recipe by UUID first, then by exact/fuzzy meal name. */
    private UUID resolveRecipeReference(UUID userId, String recipeId, String recipeName) {
        if (recipeId != null && !recipeId.isBlank()) {
            UUID parsed = parseUuid(recipeId, "recipe ID");
            if (parsed != null && recipeRepository.findByIdAndUserId(parsed, userId).isPresent()) {
                return parsed;
            }
        }

        if (recipeName == null || recipeName.isBlank()) {
            return null;
        }

        String target = recipeName.trim();
        List<Recipe> recipes = recipeRepository.findByUserId(userId);

        for (Recipe recipe : recipes) {
            if (recipe.getMealName() != null && recipe.getMealName().equalsIgnoreCase(target)) {
                return recipe.getId();
            }
        }

        for (Recipe recipe : recipes) {
            String name = recipe.getMealName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (name.contains(target) || target.contains(name)) {
                return recipe.getId();
            }
        }

        return null;
    }

    private String resolveServingDate(String servingDate) {
        if (servingDate == null || servingDate.isBlank()) {
            return LocalDate.now().toString();
        }
        return resolveOptionalDate(servingDate);
    }

    /** Like resolveServingDate but returns null for blank input instead of defaulting to today. */
    private String resolveOptionalDate(String servingDate) {
        if (servingDate == null || servingDate.isBlank()) {
            return null;
        }
        String trimmed = servingDate.trim();
        try {
            return LocalDate.parse(trimmed).toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        // Accept M-D / MM-DD using today's year (e.g. "7-20" → 2026-07-20)
        try {
            String[] parts = trimmed.split("[-/]");
            if (parts.length == 2) {
                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                return LocalDate.of(LocalDate.now().getYear(), month, day).toString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private boolean withinDateRange(String servingDate, String fromDate, String toDate) {
        if (fromDate == null && toDate == null) {
            return true;
        }
        try {
            LocalDate date = LocalDate.parse(servingDate);
            if (fromDate != null && !fromDate.isBlank()) {
                if (date.isBefore(LocalDate.parse(fromDate))) {
                    return false;
                }
            }
            if (toDate != null && !toDate.isBlank()) {
                if (date.isAfter(LocalDate.parse(toDate))) {
                    return false;
                }
            }
            return true;
        } catch (DateTimeParseException ex) {
            return true;
        }
    }

    private double parseQuantity(String quantity) {
        if (quantity == null || quantity.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(quantity);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
