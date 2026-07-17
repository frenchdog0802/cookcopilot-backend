package com.cookplanner.service;

import com.cookplanner.common.GlobalExceptionHandler.*;
import com.cookplanner.entity.*;
import com.cookplanner.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PantryItemService {

    private final PantryItemRepository pantryItemRepository;
    private final IngredientRepository ingredientRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;
    private final MealPlanRepository mealPlanRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    public List<Map<String, Object>> insertAllPantryItems(UUID userId, List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            UUID ingredientId = resolveIngredientId(item);
            PantryItem pi = PantryItem.builder()
                    .userId(userId)
                    .ingredientId(ingredientId)
                    .quantity(toDouble(item.get("quantity")))
                    .unit((String) item.getOrDefault("unit", ""))
                    .notes((String) item.getOrDefault("notes", ""))
                    .build();
            pantryItemRepository.save(pi);
            result.add(Map.of("id", pi.getId(), "quantity", pi.getQuantity(), "unit", pi.getUnit()));
        }
        return result;
    }

    public Map<String, Object> createPantryItem(UUID userId, Map<String, Object> item) {
        UUID ingredientId = resolveIngredientId(item);
        Ingredient ing = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));

        PantryItem pi = PantryItem.builder()
                .userId(userId)
                .ingredientId(ingredientId)
                .quantity(toDouble(item.get("quantity")))
                .unit((String) item.getOrDefault("unit", ing.getDefaultUnit()))
                .notes((String) item.getOrDefault("notes", ""))
                .build();
        pantryItemRepository.save(pi);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", pi.getId());
        result.put("quantity", pi.getQuantity());
        result.put("unit", pi.getUnit());
        result.put("notes", pi.getNotes());
        result.put("name", ing.getName());
        return result;
    }

    public List<Map<String, Object>> getAllPantryItems(UUID userId) {
        List<PantryItem> pantryItems = pantryItemRepository.findByUserId(userId);
        List<ShoppingListItem> shoppingListItems = shoppingListItemRepository.findByUserId(userId);

        String today = java.time.LocalDate.now().toString();
        List<MealPlan> mealPlans = mealPlanRepository.findByUserIdAndServingDateGreaterThanEqual(userId, today);

        // Sum ingredient quantities from meal plans
        Map<UUID, Double> ingredientQuantityMap = new HashMap<>();
        for (MealPlan mp : mealPlans) {
            List<RecipeIngredient> ris = recipeIngredientRepository.findByRecipeId(mp.getRecipeId());
            for (RecipeIngredient ri : ris) {
                ingredientQuantityMap.merge(ri.getIngredientId(), ri.getQuantity(), Double::sum);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (PantryItem pi : pantryItems) {
            Ingredient ing = ingredientRepository.findById(pi.getIngredientId()).orElse(null);

            double itemToBuy = shoppingListItems.stream()
                    .filter(s -> s.getIngredientId().equals(pi.getIngredientId()) && !s.getChecked())
                    .mapToDouble(s -> s.getQuantity() != null ? s.getQuantity() : 0)
                    .sum();

            double itemPlanned = ingredientQuantityMap.getOrDefault(pi.getIngredientId(), 0.0);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pi.getId().toString());
            m.put("user_id", pi.getUserId());
            m.put("ingredient_id", pi.getIngredientId());
            m.put("quantity", pi.getQuantity());
            m.put("item_to_buy", itemToBuy);
            m.put("item_planned", itemPlanned);
            m.put("name", ing != null ? ing.getName() : "Unknown");
            m.put("unit", pi.getUnit());
            result.add(m);
        }

        return result;
    }

    public PantryItem getPantryItemById(UUID id) {
        return pantryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pantry item not found"));
    }

    public PantryItem updatePantryItem(UUID id, Map<String, Object> updates) {
        PantryItem pi = getPantryItemById(id);
        if (updates.containsKey("quantity")) pi.setQuantity(toDouble(updates.get("quantity")));
        if (updates.containsKey("unit")) pi.setUnit((String) updates.get("unit"));
        if (updates.containsKey("notes")) pi.setNotes((String) updates.get("notes"));
        return pantryItemRepository.save(pi);
    }

    public void deletePantryItem(UUID id) {
        if (!pantryItemRepository.existsById(id)) throw new ResourceNotFoundException("Pantry item not found");
        pantryItemRepository.deleteById(id);
    }

    public List<Map<String, Object>> updateAllPantryItems(List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            UUID id = UUID.fromString((String) item.get("id"));
            PantryItem pi = getPantryItemById(id);
            if (item.containsKey("quantity")) pi.setQuantity(toDouble(item.get("quantity")));
            if (item.containsKey("unit")) pi.setUnit((String) item.get("unit"));
            if (item.containsKey("notes")) pi.setNotes((String) item.get("notes"));
            pantryItemRepository.save(pi);

            Ingredient ing = ingredientRepository.findById(pi.getIngredientId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pi.getId().toString());
            m.put("quantity", pi.getQuantity());
            m.put("unit", pi.getUnit());
            m.put("notes", pi.getNotes());
            m.put("name", ing != null ? ing.getName() : "Unknown");
            result.add(m);
        }
        return result;
    }

    // ── Helpers ──

    private UUID resolveIngredientId(Map<String, Object> item) {
        if (item.containsKey("ingredient_id") && item.get("ingredient_id") != null) {
            return UUID.fromString(item.get("ingredient_id").toString());
        }
        String name = (String) item.get("name");
        if (name == null) throw new BadRequestException("Missing 'name' field when ingredient_id is not provided");

        Ingredient ing = ingredientRepository.findByName(name)
                .orElseGet(() -> ingredientRepository.save(
                        Ingredient.builder().name(name).defaultUnit((String) item.getOrDefault("unit", "")).build()));
        return ing.getId();
    }

    private Double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) return Double.parseDouble((String) val);
        return 0.0;
    }
}
