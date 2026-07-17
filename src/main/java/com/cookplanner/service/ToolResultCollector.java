package com.cookplanner.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request-scoped holder for structured tool outcomes. Supports multiple tool results per request.
 */
@Component
@RequestScope
public class ToolResultCollector {

    public record ToolResult(String toolName, Map<String, Object> data) {
    }

    private final List<ToolResult> results = new ArrayList<>();

    public void addResult(String toolName, Map<String, Object> data) {
        results.add(new ToolResult(toolName, data));
    }

    /** @deprecated prefer {@link #addResult}; kept for existing call sites */
    public void setResult(String toolName, Map<String, Object> data) {
        addResult(toolName, data);
    }

    public boolean hasResult() {
        return !results.isEmpty();
    }

    public List<ToolResult> getResults() {
        return List.copyOf(results);
    }

    public String getToolName() {
        return results.isEmpty() ? null : results.get(results.size() - 1).toolName();
    }

    public Map<String, Object> data() {
        return results.isEmpty() ? null : results.get(results.size() - 1).data();
    }

    public Map<String, Object> getData() {
        return data();
    }

    public void clear() {
        results.clear();
    }

    public Map<String, Object> toAggregatedData() {
        if (results.isEmpty()) {
            return Map.of();
        }
        if (results.size() == 1) {
            return results.get(0).data();
        }
        List<Map<String, Object>> actions = new ArrayList<>();
        for (ToolResult result : results) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("tool", result.toolName());
            action.put("responseType", mapToolToResponseType(result.toolName()));
            action.putAll(result.data());
            actions.add(action);
        }
        Map<String, Object> aggregated = new LinkedHashMap<>();
        aggregated.put("actionCount", actions.size());
        aggregated.put("actions", actions);
        return aggregated;
    }

    public String primaryResponseType() {
        if (results.isEmpty()) {
            return "text";
        }
        if (results.size() > 1) {
            return "multi_action";
        }
        return mapToolToResponseType(results.get(0).toolName());
    }

    public static String mapToolToResponseType(String toolName) {
        return switch (toolName) {
            case "createRecipe" -> "recipe_created";
            case "importRecipeFromUrl" -> "recipe_imported";
            case "addItemsToShoppingList" -> "shopping_list_updated";
            case "addRecipeToMenu", "removeRecipeFromMenu", "updateMealPlan", "planMeals" -> "meal_plan_updated";
            case "addPantryItems", "updatePantryItem", "removePantryItem", "organizePantry" -> "pantry_updated";
            case "updateRecipe" -> "recipe_updated";
            case "suggestMealsFromPantry" -> "meal_suggestions";
            default -> "action_result";
        };
    }
}
