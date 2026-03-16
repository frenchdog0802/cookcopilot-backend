package com.cookplanner.service;

import com.cookplanner.common.GlobalExceptionHandler.*;
import com.cookplanner.entity.*;
import com.cookplanner.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ShoppingListService {

    private final ShoppingListItemRepository shoppingListItemRepository;
    private final IngredientRepository ingredientRepository;
    private final PantryItemRepository pantryItemRepository;

    public List<Map<String, Object>> insertAllShoppingListItems(UUID userId, List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            UUID ingredientId = resolveIngredientId(item);
            ShoppingListItem sli = ShoppingListItem.builder()
                    .userId(userId)
                    .ingredientId(ingredientId)
                    .quantity(toDouble(item.get("quantity")))
                    .unit((String) item.getOrDefault("unit", ""))
                    .checked(false)
                    .build();
            shoppingListItemRepository.save(sli);
            result.add(Map.of("id", sli.getId()));
        }
        return result;
    }

    public Map<String, Object> createShoppingListItem(UUID userId, Map<String, Object> item) {
        UUID ingredientId = resolveIngredientId(item);
        Ingredient ing = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));

        // Auto-create pantry item if not exists
        pantryItemRepository.findByUserIdAndIngredientId(userId, ingredientId)
                .orElseGet(() -> pantryItemRepository.save(PantryItem.builder()
                        .userId(userId)
                        .ingredientId(ingredientId)
                        .quantity(0.0)
                        .unit(ing.getDefaultUnit() != null ? ing.getDefaultUnit() : "")
                        .build()));

        ShoppingListItem sli = ShoppingListItem.builder()
                .userId(userId)
                .ingredientId(ingredientId)
                .quantity(toDouble(item.get("quantity")))
                .unit((String) item.getOrDefault("unit", ing.getDefaultUnit() != null ? ing.getDefaultUnit() : ""))
                .checked(item.get("checked") != null ? (Boolean) item.get("checked") : false)
                .build();
        shoppingListItemRepository.save(sli);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", sli.getId());
        result.put("quantity", sli.getQuantity());
        result.put("unit", sli.getUnit());
        result.put("checked", sli.getChecked());
        result.put("name", ing.getName());
        return result;
    }

    public List<Map<String, Object>> getAllShoppingListItems(UUID userId) {
        List<ShoppingListItem> items = shoppingListItemRepository.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ShoppingListItem item : items) {
            Ingredient ing = ingredientRepository.findById(item.getIngredientId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId().toString());
            m.put("user_id", item.getUserId());
            m.put("ingredient_id", item.getIngredientId());
            m.put("quantity", item.getQuantity());
            m.put("name", ing != null ? ing.getName() : "Unknown");
            m.put("unit", item.getUnit());
            m.put("checked", item.getChecked());
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
        if (updates.containsKey("quantity")) item.setQuantity(toDouble(updates.get("quantity")));
        if (updates.containsKey("unit")) item.setUnit((String) updates.get("unit"));
        if (updates.containsKey("checked")) item.setChecked((Boolean) updates.get("checked"));
        shoppingListItemRepository.save(item);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId().toString());
        result.put("pantry_item_id", "");
        result.put("new_quantity", item.getQuantity());
        result.put("new_item_to_buy", item.getQuantity());
        result.put("checked", item.getChecked());

        // Handle pantry sync
        if (item.getChecked() && !item.getHasBeenAddedToPantry()) {
            handleCheckItem(item, result);
        } else if (!item.getChecked() && item.getHasBeenAddedToPantry()) {
            handleUncheckItem(item, result);
        }

        return result;
    }

    public void deleteShoppingListItem(UUID id) {
        if (!shoppingListItemRepository.existsById(id))
            throw new ResourceNotFoundException("Shopping list item not found");
        shoppingListItemRepository.deleteById(id);
    }

    // ── Pantry Sync Helpers ──

    private void handleCheckItem(ShoppingListItem shoppingItem, Map<String, Object> resultItem) {
        Optional<PantryItem> existing = pantryItemRepository
                .findByUserIdAndIngredientId(shoppingItem.getUserId(), shoppingItem.getIngredientId());

        double newQty;
        if (existing.isPresent()) {
            PantryItem pi = existing.get();
            newQty = (pi.getQuantity() != null ? pi.getQuantity() : 0) + (shoppingItem.getQuantity() != null ? shoppingItem.getQuantity() : 0);
            pi.setQuantity(newQty);
            pantryItemRepository.save(pi);
            resultItem.put("pantry_item_id", pi.getId().toString());
        } else {
            PantryItem pi = PantryItem.builder()
                    .userId(shoppingItem.getUserId())
                    .ingredientId(shoppingItem.getIngredientId())
                    .quantity(shoppingItem.getQuantity() != null ? shoppingItem.getQuantity() : 0)
                    .unit(shoppingItem.getUnit() != null ? shoppingItem.getUnit() : "")
                    .build();
            pantryItemRepository.save(pi);
            newQty = pi.getQuantity();
        }

        shoppingItem.setHasBeenAddedToPantry(true);
        shoppingListItemRepository.save(shoppingItem);
        resultItem.put("new_quantity", newQty);
        resultItem.put("new_item_to_buy", 0.0);
    }

    private void handleUncheckItem(ShoppingListItem shoppingItem, Map<String, Object> resultItem) {
        Optional<PantryItem> existing = pantryItemRepository
                .findByUserIdAndIngredientId(shoppingItem.getUserId(), shoppingItem.getIngredientId());

        if (existing.isEmpty()) return;

        PantryItem pi = existing.get();
        double newQty = Math.max(0, (pi.getQuantity() != null ? pi.getQuantity() : 0) - (shoppingItem.getQuantity() != null ? shoppingItem.getQuantity() : 0));
        pi.setQuantity(newQty);
        pantryItemRepository.save(pi);

        shoppingItem.setHasBeenAddedToPantry(false);
        shoppingListItemRepository.save(shoppingItem);

        resultItem.put("new_quantity", newQty);
        resultItem.put("new_item_to_buy", shoppingItem.getQuantity());
        resultItem.put("pantry_item_id", pi.getId().toString());
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
