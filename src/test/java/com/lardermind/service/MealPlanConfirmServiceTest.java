package com.lardermind.service;

import com.lardermind.entity.*;
import com.lardermind.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MealPlanConfirmServiceTest {

    @Mock MealPlanRepository mealPlanRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock RecipeIngredientRepository recipeIngredientRepository;
    @Mock IngredientRepository ingredientRepository;
    @Mock PantryItemRepository pantryItemRepository;
    @Mock ShoppingListItemRepository shoppingListItemRepository;
    @Mock InventoryAuditService inventoryAuditService;

    @InjectMocks MealPlanService mealPlanService;

    UUID userId;
    UUID mealPlanId;
    UUID recipeId;
    UUID ingredientId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mealPlanId = UUID.randomUUID();
        recipeId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();
    }

    @Test
    void confirmDeductsPantryAndClampsAtZero() {
        MealPlan mp = MealPlan.builder()
                .id(mealPlanId)
                .userId(userId)
                .recipeId(recipeId)
                .mealType("dinner")
                .servingDate("2026-07-17")
                .status(MealPlanStatus.PENDING_CONFIRM)
                .build();

        Recipe recipe = Recipe.builder().id(recipeId).mealName("Pasta").build();
        Ingredient ing = Ingredient.builder()
                .id(ingredientId)
                .name("Tomato")
                .unitKind("weight")
                .baseUnit("g")
                .defaultDisplayUnit("g")
                .build();
        RecipeIngredient ri = RecipeIngredient.builder()
                .recipeId(recipeId)
                .ingredientId(ingredientId)
                .quantity(500.0)
                .unit("g")
                .build();
        PantryItem pantry = PantryItem.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .ingredientId(ingredientId)
                .quantity(200.0)
                .unit("g")
                .build();

        when(mealPlanRepository.findByIdAndUserId(mealPlanId, userId)).thenReturn(Optional.of(mp));
        when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
        when(recipeIngredientRepository.findByRecipeId(recipeId)).thenReturn(List.of(ri));
        when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ing));
        when(pantryItemRepository.findByUserIdAndIngredientId(userId, ingredientId)).thenReturn(Optional.of(pantry));
        when(pantryItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mealPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = mealPlanService.confirmMealPlan(userId, mealPlanId);

        assertEquals(MealPlanStatus.CONFIRMED.name(), result.get("status"));
        assertEquals(0.0, pantry.getQuantity());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shortages = (List<Map<String, Object>>) result.get("shortages");
        assertEquals(1, shortages.size());
        assertEquals(500.0, shortages.get(0).get("needed"));
        assertEquals(200.0, shortages.get(0).get("available"));

        verify(inventoryAuditService).log(
                eq(userId),
                eq(ingredientId),
                eq(pantry.getId()),
                eq(InventoryChangeSource.MEAL_CONFIRMED),
                eq(-200.0),
                eq(200.0),
                eq(0.0),
                eq("g"),
                eq(mealPlanId),
                eq(recipeId),
                isNull(),
                anyString()
        );
    }

    @Test
    void skipDoesNotTouchPantry() {
        MealPlan mp = MealPlan.builder()
                .id(mealPlanId)
                .userId(userId)
                .recipeId(recipeId)
                .mealType("dinner")
                .servingDate("2026-07-17")
                .status(MealPlanStatus.PENDING_CONFIRM)
                .build();
        Recipe recipe = Recipe.builder().id(recipeId).mealName("Pasta").build();

        when(mealPlanRepository.findByIdAndUserId(mealPlanId, userId)).thenReturn(Optional.of(mp));
        when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
        when(mealPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = mealPlanService.skipMealPlan(userId, mealPlanId);

        assertEquals(MealPlanStatus.SKIPPED.name(), result.get("status"));
        verifyNoInteractions(pantryItemRepository);
        verifyNoInteractions(inventoryAuditService);
    }
}
