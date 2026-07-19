package com.lardermind.service;

import com.lardermind.common.GlobalExceptionHandler.*;
import com.lardermind.entity.*;
import com.lardermind.repository.*;
import com.lardermind.unit.UnitConverter;
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
    private final InventoryAuditService inventoryAuditService;
    private final MealPlanService mealPlanService;

    public List<Map<String, Object>> insertAllPantryItems(UUID userId, List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        // Merge same-name rows within one batch before upserting.
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            String rawName = item.get("name") != null ? item.get("name").toString().trim() : "";
            String key = rawName.toLowerCase(Locale.ROOT);
            if (key.isEmpty() && item.get("ingredient_id") != null) {
                key = "id:" + item.get("ingredient_id");
            }
            if (key.isEmpty()) {
                continue;
            }
            Map<String, Object> existing = byName.get(key);
            if (existing == null) {
                byName.put(key, new LinkedHashMap<>(item));
            } else {
                existing.put("quantity", toDouble(existing.get("quantity")) + toDouble(item.get("quantity")));
            }
        }
        for (Map<String, Object> item : byName.values()) {
            result.add(createPantryItem(userId, item));
        }
        return result;
    }

    public Map<String, Object> createPantryItem(UUID userId, Map<String, Object> item) {
        Ingredient ing = resolveIngredient(item);
        String baseUnit = UnitConverter.resolveBaseUnit(ing);
        String inputUnit = item.get("unit") != null ? item.get("unit").toString() : baseUnit;
        double baseQty = UnitConverter.toIngredientBase(ing, toDouble(item.get("quantity")), inputUnit);

        List<PantryItem> existingRows = pantryItemRepository.findAllByUserIdAndIngredientId(userId, ing.getId());
        PantryItem pi;
        boolean merged = false;
        if (!existingRows.isEmpty()) {
            pi = existingRows.get(0);
            double previous = UnitConverter.toIngredientBaseLenient(
                    ing,
                    pi.getQuantity() != null ? pi.getQuantity() : 0,
                    pi.getUnit() != null && !pi.getUnit().isBlank() ? pi.getUnit() : baseUnit);
            pi.setQuantity(previous + baseQty);
            pi.setUnit(baseUnit);
            if (item.get("notes") != null && !item.get("notes").toString().isBlank()) {
                String note = item.get("notes").toString();
                String current = pi.getNotes() != null ? pi.getNotes() : "";
                if (current.isBlank()) {
                    pi.setNotes(note);
                } else if (!current.contains(note)) {
                    pi.setNotes(current + "; " + note);
                }
            }
            pantryItemRepository.save(pi);
            // Collapse any pre-existing duplicate rows for this ingredient.
            for (int i = 1; i < existingRows.size(); i++) {
                PantryItem dup = existingRows.get(i);
                double dupQty = UnitConverter.toIngredientBaseLenient(
                        ing,
                        dup.getQuantity() != null ? dup.getQuantity() : 0,
                        dup.getUnit() != null && !dup.getUnit().isBlank() ? dup.getUnit() : baseUnit);
                pi.setQuantity((pi.getQuantity() != null ? pi.getQuantity() : 0) + dupQty);
                pantryItemRepository.deleteById(dup.getId());
            }
            if (existingRows.size() > 1) {
                pantryItemRepository.save(pi);
            }
            merged = true;
        } else {
            pi = PantryItem.builder()
                    .userId(userId)
                    .ingredientId(ing.getId())
                    .quantity(baseQty)
                    .unit(baseUnit)
                    .notes((String) item.getOrDefault("notes", ""))
                    .build();
            pantryItemRepository.save(pi);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", pi.getId());
        result.put("quantity", pi.getQuantity());
        result.put("unit", pi.getUnit());
        result.put("notes", pi.getNotes());
        result.put("name", ing.getName());
        result.put("ingredient_id", ing.getId());
        result.put("merged", merged);
        result.put("unit_kind", UnitConverter.resolveKind(ing).toApiValue());
        result.put("base_unit", baseUnit);
        result.put("default_display_unit", UnitConverter.resolveDisplayUnit(ing));
        return result;
    }

    public List<Map<String, Object>> getAllPantryItems(UUID userId) {
        mealPlanService.transitionStatusesForUser(userId);

        List<PantryItem> pantryItems = pantryItemRepository.findByUserId(userId);
        List<ShoppingListItem> shoppingListItems = shoppingListItemRepository.findByUserId(userId);

        String today = java.time.LocalDate.now().toString();
        List<MealPlan> mealPlans = mealPlanRepository.findByUserIdAndServingDateGreaterThanEqual(userId, today);

        Map<UUID, Double> ingredientQuantityMap = new HashMap<>();
        for (MealPlan mp : mealPlans) {
            MealPlanStatus status = mp.getStatus() != null ? mp.getStatus() : MealPlanStatus.PLANNED;
            // Only future/today planned meals count toward planned usage
            if (status != MealPlanStatus.PLANNED) {
                continue;
            }
            List<RecipeIngredient> ris = recipeIngredientRepository.findByRecipeId(mp.getRecipeId());
            for (RecipeIngredient ri : ris) {
                ingredientQuantityMap.merge(ri.getIngredientId(),
                        ri.getQuantity() != null ? ri.getQuantity() : 0.0,
                        Double::sum);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (PantryItem pi : pantryItems) {
            Ingredient ing = ingredientRepository.findById(pi.getIngredientId()).orElse(null);
            String baseUnit = ing != null ? UnitConverter.resolveBaseUnit(ing) : (pi.getUnit() != null ? pi.getUnit() : "");

            double itemToBuy = shoppingListItems.stream()
                    .filter(s -> s.getIngredientId().equals(pi.getIngredientId()) && !Boolean.TRUE.equals(s.getChecked()))
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
            m.put("unit", pi.getUnit() != null ? pi.getUnit() : baseUnit);
            if (ing != null) {
                m.put("unit_kind", UnitConverter.resolveKind(ing).toApiValue());
                m.put("base_unit", baseUnit);
                m.put("default_display_unit", UnitConverter.resolveDisplayUnit(ing));
            }
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
        Ingredient ing = ingredientRepository.findById(pi.getIngredientId())
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
        String baseUnit = UnitConverter.resolveBaseUnit(ing);

        Double previousQty = null;
        Double newQty = null;
        if (updates.containsKey("quantity") || updates.containsKey("unit")) {
            previousQty = UnitConverter.toIngredientBaseLenient(
                    ing,
                    pi.getQuantity() != null ? pi.getQuantity() : 0,
                    pi.getUnit() != null && !pi.getUnit().isBlank() ? pi.getUnit() : baseUnit);
            double qty = updates.containsKey("quantity") ? toDouble(updates.get("quantity")) : pi.getQuantity();
            String unit = updates.containsKey("unit") && updates.get("unit") != null
                    ? updates.get("unit").toString()
                    : baseUnit;
            newQty = UnitConverter.toIngredientBase(ing, qty, unit);
            pi.setQuantity(newQty);
            pi.setUnit(baseUnit);
        }
        if (updates.containsKey("notes")) pi.setNotes((String) updates.get("notes"));
        PantryItem saved = pantryItemRepository.save(pi);

        if (previousQty != null && newQty != null) {
            double delta = newQty - previousQty;
            inventoryAuditService.log(
                    saved.getUserId(),
                    saved.getIngredientId(),
                    saved.getId(),
                    InventoryChangeSource.MANUAL_ADJUST,
                    delta,
                    previousQty,
                    newQty,
                    baseUnit,
                    null,
                    null,
                    null,
                    "Manual pantry adjustment"
            );
        }
        return saved;
    }

    public void deletePantryItem(UUID id) {
        if (!pantryItemRepository.existsById(id)) throw new ResourceNotFoundException("Pantry item not found");
        pantryItemRepository.deleteById(id);
    }

    public List<Map<String, Object>> updateAllPantryItems(List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            UUID id = UUID.fromString(item.get("id").toString());
            PantryItem pi = updatePantryItem(id, item);

            Ingredient ing = ingredientRepository.findById(pi.getIngredientId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pi.getId().toString());
            m.put("quantity", pi.getQuantity());
            m.put("unit", pi.getUnit());
            m.put("notes", pi.getNotes());
            m.put("name", ing != null ? ing.getName() : "Unknown");
            if (ing != null) {
                m.put("unit_kind", UnitConverter.resolveKind(ing).toApiValue());
                m.put("base_unit", UnitConverter.resolveBaseUnit(ing));
                m.put("default_display_unit", UnitConverter.resolveDisplayUnit(ing));
            }
            result.add(m);
        }
        return result;
    }

    private Ingredient resolveIngredient(Map<String, Object> item) {
        if (item.containsKey("ingredient_id") && item.get("ingredient_id") != null) {
            return ingredientRepository.findById(UUID.fromString(item.get("ingredient_id").toString()))
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
        }
        String name = (String) item.get("name");
        if (name == null) throw new BadRequestException("Missing 'name' field when ingredient_id is not provided");

        return ingredientRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Ingredient created = UnitConverter.inferAndBuild(name, (String) item.getOrDefault("unit", ""));
                    UnitConverter.applyDefaults(created);
                    return ingredientRepository.save(created);
                });
    }

    private Double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) return Double.parseDouble((String) val);
        return 0.0;
    }
}
