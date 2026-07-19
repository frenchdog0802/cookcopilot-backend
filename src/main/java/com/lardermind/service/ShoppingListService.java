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
public class ShoppingListService {

    private final ShoppingListItemRepository shoppingListItemRepository;
    private final IngredientRepository ingredientRepository;
    private final PantryItemRepository pantryItemRepository;
    private final InventoryAuditService inventoryAuditService;

    public List<Map<String, Object>> insertAllShoppingListItems(UUID userId, List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>();
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
            result.add(createShoppingListItem(userId, item));
        }
        return result;
    }

    public Map<String, Object> createShoppingListItem(UUID userId, Map<String, Object> item) {
        Ingredient ing = resolveIngredient(item);
        String baseUnit = UnitConverter.resolveBaseUnit(ing);
        String inputUnit = item.get("unit") != null ? item.get("unit").toString() : baseUnit;
        double baseQty = UnitConverter.toIngredientBase(ing, toDouble(item.get("quantity")), inputUnit);

        List<PantryItem> pantryRows = pantryItemRepository.findAllByUserIdAndIngredientId(userId, ing.getId());
        if (pantryRows.isEmpty()) {
            pantryItemRepository.save(PantryItem.builder()
                    .userId(userId)
                    .ingredientId(ing.getId())
                    .quantity(0.0)
                    .unit(baseUnit)
                    .build());
        }

        List<ShoppingListItem> existingUnchecked =
                shoppingListItemRepository.findAllByUserIdAndIngredientIdAndChecked(userId, ing.getId(), false);
        ShoppingListItem sli;
        boolean merged = false;
        if (!existingUnchecked.isEmpty()) {
            sli = existingUnchecked.get(0);
            double previous = sli.getQuantity() != null ? sli.getQuantity() : 0;
            sli.setQuantity(previous + baseQty);
            sli.setUnit(baseUnit);
            shoppingListItemRepository.save(sli);
            for (int i = 1; i < existingUnchecked.size(); i++) {
                ShoppingListItem dup = existingUnchecked.get(i);
                sli.setQuantity((sli.getQuantity() != null ? sli.getQuantity() : 0)
                        + (dup.getQuantity() != null ? dup.getQuantity() : 0));
                shoppingListItemRepository.deleteById(dup.getId());
            }
            if (existingUnchecked.size() > 1) {
                shoppingListItemRepository.save(sli);
            }
            merged = true;
        } else {
            sli = ShoppingListItem.builder()
                    .userId(userId)
                    .ingredientId(ing.getId())
                    .quantity(baseQty)
                    .unit(baseUnit)
                    .checked(item.get("checked") != null ? (Boolean) item.get("checked") : false)
                    .build();
            shoppingListItemRepository.save(sli);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", sli.getId());
        result.put("quantity", sli.getQuantity());
        result.put("unit", sli.getUnit());
        result.put("checked", sli.getChecked());
        result.put("name", ing.getName());
        result.put("ingredient_id", ing.getId());
        result.put("merged", merged);
        result.put("unit_kind", UnitConverter.resolveKind(ing).toApiValue());
        result.put("base_unit", baseUnit);
        result.put("default_display_unit", UnitConverter.resolveDisplayUnit(ing));
        return result;
    }

    public List<Map<String, Object>> getAllShoppingListItems(UUID userId) {
        List<ShoppingListItem> items = shoppingListItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ShoppingListItem item : items) {
            Ingredient ing = ingredientRepository.findById(item.getIngredientId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId().toString());
            m.put("user_id", item.getUserId());
            m.put("ingredient_id", item.getIngredientId());
            m.put("quantity", item.getQuantity());
            m.put("name", ing != null ? ing.getName() : "Unknown");
            m.put("unit", item.getUnit() != null ? item.getUnit()
                    : (ing != null ? UnitConverter.resolveBaseUnit(ing) : ""));
            m.put("checked", item.getChecked());
            if (ing != null) {
                m.put("unit_kind", UnitConverter.resolveKind(ing).toApiValue());
                m.put("base_unit", UnitConverter.resolveBaseUnit(ing));
                m.put("default_display_unit", UnitConverter.resolveDisplayUnit(ing));
            }
            result.add(m);
        }
        return result;
    }

    public ShoppingListItem getShoppingListItemById(UUID id) {
        return shoppingListItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shopping list item not found"));
    }

    public Map<String, Object> updateShoppingListItem(UUID id, Map<String, Object> updates) {
        ShoppingListItem item = getShoppingListItemById(id);
        Ingredient ing = ingredientRepository.findById(item.getIngredientId())
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));

        if (updates.containsKey("quantity") || updates.containsKey("unit")) {
            double qty = updates.containsKey("quantity") ? toDouble(updates.get("quantity")) : item.getQuantity();
            String unit = updates.containsKey("unit") && updates.get("unit") != null
                    ? updates.get("unit").toString()
                    : UnitConverter.resolveBaseUnit(ing);
            double baseQty = UnitConverter.toIngredientBase(ing, qty, unit);
            item.setQuantity(baseQty);
            item.setUnit(UnitConverter.resolveBaseUnit(ing));
        }
        if (updates.containsKey("checked")) item.setChecked(toBoolean(updates.get("checked")));
        shoppingListItemRepository.save(item);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId().toString());
        result.put("pantry_item_id", "");
        result.put("new_quantity", item.getQuantity());
        result.put("new_item_to_buy", item.getQuantity());
        result.put("checked", item.getChecked());

        boolean checked = Boolean.TRUE.equals(item.getChecked());
        boolean alreadyAdded = Boolean.TRUE.equals(item.getHasBeenAddedToPantry());
        if (checked && !alreadyAdded) {
            handleCheckItem(item, ing, result);
        } else if (!checked && alreadyAdded) {
            handleUncheckItem(item, ing, result);
        }

        return result;
    }

    public void deleteShoppingListItem(UUID id) {
        if (!shoppingListItemRepository.existsById(id))
            throw new ResourceNotFoundException("Shopping list item not found");
        shoppingListItemRepository.deleteById(id);
    }

    private void handleCheckItem(ShoppingListItem shoppingItem, Ingredient ing, Map<String, Object> resultItem) {
        String baseUnit = UnitConverter.resolveBaseUnit(ing);
        double addQty = shoppingItem.getQuantity() != null ? shoppingItem.getQuantity() : 0;

        Optional<PantryItem> existing = pantryItemRepository
                .findAllByUserIdAndIngredientId(shoppingItem.getUserId(), shoppingItem.getIngredientId())
                .stream()
                .findFirst();

        double previousQty;
        double newQty;
        UUID pantryItemId;
        if (existing.isPresent()) {
            PantryItem pi = existing.get();
            previousQty = UnitConverter.toIngredientBaseLenient(
                    ing,
                    pi.getQuantity() != null ? pi.getQuantity() : 0,
                    pi.getUnit() != null && !pi.getUnit().isBlank() ? pi.getUnit() : baseUnit);
            newQty = previousQty + addQty;
            pi.setQuantity(newQty);
            pi.setUnit(baseUnit);
            pantryItemRepository.save(pi);
            pantryItemId = pi.getId();
            resultItem.put("pantry_item_id", pi.getId().toString());
        } else {
            previousQty = 0;
            PantryItem pi = PantryItem.builder()
                    .userId(shoppingItem.getUserId())
                    .ingredientId(shoppingItem.getIngredientId())
                    .quantity(addQty)
                    .unit(baseUnit)
                    .build();
            pantryItemRepository.save(pi);
            newQty = pi.getQuantity();
            pantryItemId = pi.getId();
            resultItem.put("pantry_item_id", pi.getId().toString());
        }

        shoppingItem.setHasBeenAddedToPantry(true);
        shoppingListItemRepository.save(shoppingItem);
        resultItem.put("new_quantity", newQty);
        resultItem.put("new_item_to_buy", 0.0);

        inventoryAuditService.log(
                shoppingItem.getUserId(),
                shoppingItem.getIngredientId(),
                pantryItemId,
                InventoryChangeSource.SHOPPING_CHECKED,
                addQty,
                previousQty,
                newQty,
                baseUnit,
                null,
                null,
                shoppingItem.getId(),
                "Shopping item checked"
        );
    }

    private void handleUncheckItem(ShoppingListItem shoppingItem, Ingredient ing, Map<String, Object> resultItem) {
        Optional<PantryItem> existing = pantryItemRepository
                .findAllByUserIdAndIngredientId(shoppingItem.getUserId(), shoppingItem.getIngredientId())
                .stream()
                .findFirst();

        if (existing.isEmpty()) return;

        String baseUnit = UnitConverter.resolveBaseUnit(ing);
        PantryItem pi = existing.get();
        double pantryBase = UnitConverter.toIngredientBaseLenient(
                ing,
                pi.getQuantity() != null ? pi.getQuantity() : 0,
                pi.getUnit() != null && !pi.getUnit().isBlank() ? pi.getUnit() : baseUnit);
        double subtract = shoppingItem.getQuantity() != null ? shoppingItem.getQuantity() : 0;
        double newQty = Math.max(0, pantryBase - subtract);
        pi.setQuantity(newQty);
        pi.setUnit(baseUnit);
        pantryItemRepository.save(pi);

        shoppingItem.setHasBeenAddedToPantry(false);
        shoppingListItemRepository.save(shoppingItem);

        resultItem.put("new_quantity", newQty);
        resultItem.put("new_item_to_buy", shoppingItem.getQuantity());
        resultItem.put("pantry_item_id", pi.getId().toString());

        inventoryAuditService.log(
                shoppingItem.getUserId(),
                shoppingItem.getIngredientId(),
                pi.getId(),
                InventoryChangeSource.SHOPPING_UNCHECKED,
                -Math.min(subtract, pantryBase),
                pantryBase,
                newQty,
                baseUnit,
                null,
                null,
                shoppingItem.getId(),
                "Shopping item unchecked"
        );
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
        if (val instanceof String) {
            try {
                return Double.parseDouble((String) val);
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private Boolean toBoolean(Object val) {
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof String) {
            return Boolean.parseBoolean((String) val);
        }
        return false;
    }
}
