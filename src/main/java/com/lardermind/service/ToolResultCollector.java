package com.lardermind.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects structured tool outcomes per user for a single chat turn.
 * Not request-scoped — streaming completions run on LangChain4j worker threads
 * where the HTTP request scope is already inactive.
 */
@Component
public class ToolResultCollector {

    public record ToolResult(String toolName, Map<String, Object> data) {
    }

    private final ConcurrentHashMap<UUID, List<ToolResult>> resultsByUser = new ConcurrentHashMap<>();
    /** Fallback bag for unit tests that construct this class directly without a user id. */
    private final ThreadLocal<List<ToolResult>> unboundResults =
            ThreadLocal.withInitial(ArrayList::new);

    public void begin(UUID userId) {
        resultsByUser.put(userId, synchronizedList());
    }

    public void end(UUID userId) {
        if (userId != null) {
            resultsByUser.remove(userId);
        }
    }

    public void addResult(UUID userId, String toolName, Map<String, Object> data) {
        bag(userId).add(new ToolResult(toolName, data));
    }

    public void addResult(String toolName, Map<String, Object> data) {
        unboundResults.get().add(new ToolResult(toolName, data));
    }

    /** @deprecated prefer {@link #addResult}; kept for existing call sites */
    public void setResult(String toolName, Map<String, Object> data) {
        addResult(toolName, data);
    }

    public boolean hasResult(UUID userId) {
        List<ToolResult> results = resultsByUser.get(userId);
        return results != null && !results.isEmpty();
    }

    public boolean hasResult() {
        return !unboundResults.get().isEmpty();
    }

    public List<ToolResult> getResults(UUID userId) {
        List<ToolResult> results = resultsByUser.get(userId);
        return results == null ? List.of() : List.copyOf(results);
    }

    public List<ToolResult> getResults() {
        return List.copyOf(unboundResults.get());
    }

    public String getToolName(UUID userId) {
        List<ToolResult> results = resultsByUser.get(userId);
        return results == null || results.isEmpty() ? null : results.get(results.size() - 1).toolName();
    }

    public String getToolName() {
        List<ToolResult> results = unboundResults.get();
        return results.isEmpty() ? null : results.get(results.size() - 1).toolName();
    }

    public Map<String, Object> data(UUID userId) {
        List<ToolResult> results = resultsByUser.get(userId);
        return results == null || results.isEmpty() ? null : results.get(results.size() - 1).data();
    }

    public Map<String, Object> data() {
        List<ToolResult> results = unboundResults.get();
        return results.isEmpty() ? null : results.get(results.size() - 1).data();
    }

    public Map<String, Object> getData() {
        return data();
    }

    public void clear(UUID userId) {
        end(userId);
    }

    public void clear() {
        unboundResults.get().clear();
        unboundResults.remove();
    }

    public Map<String, Object> toAggregatedData(UUID userId) {
        return toAggregatedData(getResults(userId));
    }

    public Map<String, Object> toAggregatedData() {
        return toAggregatedData(getResults());
    }

    public String primaryResponseType(UUID userId) {
        return primaryResponseType(getResults(userId));
    }

    public String primaryResponseType() {
        return primaryResponseType(getResults());
    }

    public static String mapToolToResponseType(String toolName) {
        return switch (toolName) {
            case "createRecipe" -> "recipe_created";
            case "importRecipeFromUrl" -> "recipe_imported";
            case "addItemsToShoppingList" -> "shopping_list_updated";
            case "addRecipeToMenu", "removeRecipeFromMenu", "updateMealPlan", "planMeals", "clearMealPlans" -> "meal_plan_updated";
            case "addPantryItems", "updatePantryItem", "removePantryItem", "organizePantry" -> "pantry_updated";
            case "updateRecipe" -> "recipe_updated";
            case "suggestMealsFromPantry" -> "meal_suggestions";
            case "updatePreferences" -> "preferences_updated";
            default -> "action_result";
        };
    }

    private List<ToolResult> bag(UUID userId) {
        return resultsByUser.computeIfAbsent(userId, id -> synchronizedList());
    }

    private static List<ToolResult> synchronizedList() {
        return java.util.Collections.synchronizedList(new ArrayList<>());
    }

    private static Map<String, Object> toAggregatedData(List<ToolResult> results) {
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

    private static String primaryResponseType(List<ToolResult> results) {
        if (results.isEmpty()) {
            return "text";
        }
        if (results.size() > 1) {
            return "multi_action";
        }
        return mapToolToResponseType(results.get(0).toolName());
    }
}
