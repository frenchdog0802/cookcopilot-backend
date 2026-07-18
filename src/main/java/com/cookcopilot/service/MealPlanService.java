package com.cookcopilot.service;

import com.cookcopilot.common.GlobalExceptionHandler.*;
import com.cookcopilot.entity.*;
import com.cookcopilot.repository.*;
import com.cookcopilot.unit.UnitConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private static final int PENDING_CONFIRM_AUTO_SKIP_DAYS = 7;

    private final MealPlanRepository mealPlanRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final PantryItemRepository pantryItemRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;
    private final InventoryAuditService inventoryAuditService;

    public List<Map<String, Object>> getAllMealPlans(UUID userId) {
        transitionStatusesForUser(userId);

        List<MealPlan> mealPlans = mealPlanRepository.findByUserId(userId);
        if (mealPlans.isEmpty()) {
            return List.of();
        }

        Set<UUID> recipeIds = new HashSet<>();
        for (MealPlan mp : mealPlans) {
            recipeIds.add(mp.getRecipeId());
        }

        Map<UUID, Recipe> recipesById = new HashMap<>();
        for (Recipe recipe : recipeRepository.findAllById(recipeIds)) {
            recipesById.put(recipe.getId(), recipe);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (MealPlan mp : mealPlans) {
            Recipe recipe = recipesById.get(mp.getRecipeId());
            if (recipe == null) {
                continue;
            }
            result.add(toMealPlanMap(mp, recipe));
        }
        return result;
    }

    public List<Map<String, Object>> getPendingConfirmations(UUID userId) {
        transitionStatusesForUser(userId);
        List<MealPlan> pending = mealPlanRepository.findByUserIdAndStatus(userId, MealPlanStatus.PENDING_CONFIRM);
        if (pending.isEmpty()) {
            return List.of();
        }

        Set<UUID> recipeIds = new HashSet<>();
        for (MealPlan mp : pending) {
            recipeIds.add(mp.getRecipeId());
        }
        Map<UUID, Recipe> recipesById = new HashMap<>();
        for (Recipe recipe : recipeRepository.findAllById(recipeIds)) {
            recipesById.put(recipe.getId(), recipe);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (MealPlan mp : pending) {
            Recipe recipe = recipesById.get(mp.getRecipeId());
            if (recipe == null) {
                continue;
            }
            result.add(toMealPlanMap(mp, recipe));
        }
        return result;
    }

    public MealPlan getMealPlanById(UUID id) {
        return mealPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan not found"));
    }

    public Map<String, Object> createMealPlan(UUID userId, UUID recipeId, String mealType, String servingDate) {
        MealPlanStatus initialStatus = resolveInitialStatus(servingDate);
        MealPlan mp = MealPlan.builder()
                .userId(userId)
                .recipeId(recipeId)
                .mealType(mealType)
                .servingDate(servingDate)
                .status(initialStatus)
                .build();
        mp = mealPlanRepository.save(mp);

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        List<RecipeIngredient> recipeIngredients = recipeIngredientRepository.findByRecipeId(recipeId);
        List<PantryItem> pantryItems = pantryItemRepository.findByUserId(userId);
        List<Map<String, Object>> notEnoughItems = new ArrayList<>();

        for (RecipeIngredient ri : recipeIngredients) {
            Ingredient ing = ingredientRepository.findById(ri.getIngredientId()).orElse(null);
            if (ing == null) {
                continue;
            }
            String baseUnit = UnitConverter.resolveBaseUnit(ing);

            PantryItem pantryItem = pantryItems.stream()
                    .filter(pi -> pi.getIngredientId().equals(ri.getIngredientId()))
                    .findFirst().orElse(null);

            double needed = ri.getQuantity() != null ? ri.getQuantity() : 0;
            double available = 0;
            if (pantryItem != null) {
                available = UnitConverter.toIngredientBaseLenient(
                        ing,
                        pantryItem.getQuantity() != null ? pantryItem.getQuantity() : 0,
                        pantryItem.getUnit() != null && !pantryItem.getUnit().isBlank()
                                ? pantryItem.getUnit()
                                : baseUnit);
            }

            double shortage = UnitConverter.clampShortage(needed, available);
            if (shortage <= 0) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ingredient_id", ri.getIngredientId());
            item.put("name", ing.getName());
            item.put("required_quantity", shortage);
            item.put("available_quantity", available);
            item.put("unit", baseUnit);
            notEnoughItems.add(item);

            Optional<ShoppingListItem> existingSli = shoppingListItemRepository
                    .findByUserIdAndIngredientIdAndChecked(userId, ri.getIngredientId(), false);
            if (existingSli.isPresent()) {
                ShoppingListItem sli = existingSli.get();
                double existingBase = UnitConverter.toIngredientBaseLenient(
                        ing,
                        sli.getQuantity() != null ? sli.getQuantity() : 0,
                        sli.getUnit() != null && !sli.getUnit().isBlank() ? sli.getUnit() : baseUnit);
                sli.setQuantity(existingBase + shortage);
                sli.setUnit(baseUnit);
                shoppingListItemRepository.save(sli);
            } else {
                ShoppingListItem sli = ShoppingListItem.builder()
                        .userId(userId)
                        .ingredientId(ri.getIngredientId())
                        .quantity(shortage)
                        .checked(false)
                        .unit(baseUnit)
                        .build();
                shoppingListItemRepository.save(sli);
            }
        }

        Map<String, Object> result = toMealPlanMap(mp, recipe);
        result.put("notEnoughItems", notEnoughItems);
        return result;
    }

    @Transactional
    public Map<String, Object> confirmMealPlan(UUID userId, UUID mealPlanId) {
        MealPlan mp = mealPlanRepository.findByIdAndUserId(mealPlanId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan not found"));

        if (mp.getStatus() == MealPlanStatus.CONFIRMED) {
            Recipe recipe = recipeRepository.findById(mp.getRecipeId()).orElse(null);
            Map<String, Object> already = toMealPlanMap(mp, recipe);
            already.put("shortages", List.of());
            already.put("deducted", List.of());
            already.put("already_confirmed", true);
            return already;
        }
        if (mp.getStatus() == MealPlanStatus.SKIPPED) {
            throw new BadRequestException("Cannot confirm a skipped meal plan");
        }

        Recipe recipe = recipeRepository.findById(mp.getRecipeId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        List<RecipeIngredient> recipeIngredients = recipeIngredientRepository.findByRecipeId(mp.getRecipeId());
        List<Map<String, Object>> shortages = new ArrayList<>();
        List<Map<String, Object>> deducted = new ArrayList<>();

        for (RecipeIngredient ri : recipeIngredients) {
            Ingredient ing = ingredientRepository.findById(ri.getIngredientId()).orElse(null);
            if (ing == null) {
                continue;
            }
            String baseUnit = UnitConverter.resolveBaseUnit(ing);
            double needed = ri.getQuantity() != null ? ri.getQuantity() : 0;
            if (needed <= 0) {
                continue;
            }

            Optional<PantryItem> pantryOpt = pantryItemRepository
                    .findByUserIdAndIngredientId(userId, ri.getIngredientId());

            double available = 0;
            PantryItem pantryItem = null;
            if (pantryOpt.isPresent()) {
                pantryItem = pantryOpt.get();
                available = UnitConverter.toIngredientBaseLenient(
                        ing,
                        pantryItem.getQuantity() != null ? pantryItem.getQuantity() : 0,
                        pantryItem.getUnit() != null && !pantryItem.getUnit().isBlank()
                                ? pantryItem.getUnit()
                                : baseUnit);
            }

            if (available < needed) {
                Map<String, Object> shortage = new LinkedHashMap<>();
                shortage.put("ingredient_id", ri.getIngredientId());
                shortage.put("name", ing.getName());
                shortage.put("needed", needed);
                shortage.put("available", available);
                shortage.put("unit", baseUnit);
                shortages.add(shortage);
            }

            if (pantryItem == null) {
                continue;
            }

            double deductAmount = Math.min(available, needed);
            double newQty = Math.max(0, available - needed);
            pantryItem.setQuantity(newQty);
            pantryItem.setUnit(baseUnit);
            pantryItemRepository.save(pantryItem);

            if (deductAmount > 0) {
                inventoryAuditService.log(
                        userId,
                        ri.getIngredientId(),
                        pantryItem.getId(),
                        InventoryChangeSource.MEAL_CONFIRMED,
                        -deductAmount,
                        available,
                        newQty,
                        baseUnit,
                        mp.getId(),
                        mp.getRecipeId(),
                        null,
                        available < needed
                                ? "Confirmed meal; pantry short of recipe need (clamped to 0)"
                                : "Confirmed meal"
                );
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ingredient_id", ri.getIngredientId());
            row.put("name", ing.getName());
            row.put("deducted", deductAmount);
            row.put("previous_quantity", available);
            row.put("new_quantity", newQty);
            row.put("unit", baseUnit);
            deducted.add(row);
        }

        mp.setStatus(MealPlanStatus.CONFIRMED);
        mealPlanRepository.save(mp);

        Map<String, Object> result = toMealPlanMap(mp, recipe);
        result.put("shortages", shortages);
        result.put("deducted", deducted);
        result.put("already_confirmed", false);
        return result;
    }

    @Transactional
    public Map<String, Object> skipMealPlan(UUID userId, UUID mealPlanId) {
        MealPlan mp = mealPlanRepository.findByIdAndUserId(mealPlanId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan not found"));

        if (mp.getStatus() == MealPlanStatus.CONFIRMED) {
            throw new BadRequestException("Cannot skip a confirmed meal plan");
        }
        if (mp.getStatus() == MealPlanStatus.SKIPPED) {
            Recipe recipe = recipeRepository.findById(mp.getRecipeId()).orElse(null);
            Map<String, Object> already = toMealPlanMap(mp, recipe);
            already.put("already_skipped", true);
            return already;
        }

        mp.setStatus(MealPlanStatus.SKIPPED);
        mealPlanRepository.save(mp);

        Recipe recipe = recipeRepository.findById(mp.getRecipeId()).orElse(null);
        Map<String, Object> result = toMealPlanMap(mp, recipe);
        result.put("already_skipped", false);
        return result;
    }

    public MealPlan updateMealPlan(UUID id, MealPlan updates) {
        MealPlan mp = getMealPlanById(id);
        if (updates.getMealType() != null) mp.setMealType(updates.getMealType());
        if (updates.getServingDate() != null) {
            mp.setServingDate(updates.getServingDate());
            if (mp.getStatus() == MealPlanStatus.PLANNED || mp.getStatus() == MealPlanStatus.PENDING_CONFIRM) {
                mp.setStatus(resolveInitialStatus(updates.getServingDate()));
            }
        }
        if (updates.getRecipeId() != null) mp.setRecipeId(updates.getRecipeId());
        return mealPlanRepository.save(mp);
    }

    public void deleteMealPlan(UUID id, UUID userId) {
        MealPlan mp = mealPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan not found"));
        mealPlanRepository.delete(mp);
    }

    @Transactional
    public void transitionAllStatuses() {
        String today = LocalDate.now().toString();
        String autoSkipBefore = LocalDate.now().minusDays(PENDING_CONFIRM_AUTO_SKIP_DAYS).toString();

        List<MealPlan> all = mealPlanRepository.findAll();
        List<MealPlan> changed = new ArrayList<>();
        for (MealPlan mp : all) {
            if (mp.getServingDate() == null) {
                continue;
            }
            MealPlanStatus status = mp.getStatus() != null ? mp.getStatus() : MealPlanStatus.PLANNED;
            if (status == MealPlanStatus.PLANNED && mp.getServingDate().compareTo(today) < 0) {
                mp.setStatus(MealPlanStatus.PENDING_CONFIRM);
                changed.add(mp);
            } else if (status == MealPlanStatus.PENDING_CONFIRM
                    && mp.getServingDate().compareTo(autoSkipBefore) <= 0) {
                mp.setStatus(MealPlanStatus.SKIPPED);
                changed.add(mp);
            }
        }
        if (!changed.isEmpty()) {
            mealPlanRepository.saveAll(changed);
        }
    }

    @Transactional
    public void transitionStatusesForUser(UUID userId) {
        String today = LocalDate.now().toString();
        String autoSkipBefore = LocalDate.now().minusDays(PENDING_CONFIRM_AUTO_SKIP_DAYS).toString();

        List<MealPlan> userPlans = mealPlanRepository.findByUserId(userId);
        List<MealPlan> changed = new ArrayList<>();
        for (MealPlan mp : userPlans) {
            if (mp.getServingDate() == null) {
                continue;
            }
            MealPlanStatus status = mp.getStatus() != null ? mp.getStatus() : MealPlanStatus.PLANNED;
            if (status == MealPlanStatus.PLANNED && mp.getServingDate().compareTo(today) < 0) {
                mp.setStatus(MealPlanStatus.PENDING_CONFIRM);
                changed.add(mp);
            } else if (status == MealPlanStatus.PENDING_CONFIRM
                    && mp.getServingDate().compareTo(autoSkipBefore) <= 0) {
                mp.setStatus(MealPlanStatus.SKIPPED);
                changed.add(mp);
            }
        }
        if (!changed.isEmpty()) {
            mealPlanRepository.saveAll(changed);
        }
    }

    private MealPlanStatus resolveInitialStatus(String servingDate) {
        if (servingDate == null || servingDate.isBlank()) {
            return MealPlanStatus.PLANNED;
        }
        String today = LocalDate.now().toString();
        if (servingDate.compareTo(today) < 0) {
            return MealPlanStatus.PENDING_CONFIRM;
        }
        return MealPlanStatus.PLANNED;
    }

    private Map<String, Object> toMealPlanMap(MealPlan mp, Recipe recipe) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mp.getId());
        m.put("recipe_id", mp.getRecipeId());
        m.put("meal_name", recipe != null ? recipe.getMealName() : null);
        m.put("image_url", recipe != null ? buildImageMap(recipe) : null);
        m.put("meal_type", mp.getMealType());
        m.put("serving_date", mp.getServingDate());
        m.put("status", mp.getStatus() != null ? mp.getStatus().name() : MealPlanStatus.PLANNED.name());
        return m;
    }

    private Map<String, String> buildImageMap(Recipe recipe) {
        if (recipe.getImageUrl() == null) return null;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("url", recipe.getImageUrl());
        m.put("public_id", recipe.getImagePublicId());
        return m;
    }
}
