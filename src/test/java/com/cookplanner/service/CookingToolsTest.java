package com.cookplanner.service;

import com.cookplanner.entity.Ingredient;
import com.cookplanner.entity.Recipe;
import com.cookplanner.entity.RecipeIngredient;
import com.cookplanner.repository.IngredientRepository;
import com.cookplanner.repository.MealPlanRepository;
import com.cookplanner.repository.PantryItemRepository;
import com.cookplanner.repository.RecipeIngredientRepository;
import com.cookplanner.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CookingToolsTest {

    @Mock
    private UserContext userContext;

    @Mock
    private ToolResultCollector toolResultCollector;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;

    @Mock
    private MealPlanRepository mealPlanRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private PantryItemRepository pantryItemRepository;

    @Mock
    private ShoppingListService shoppingListService;

    @Mock
    private MealPlanService mealPlanService;

    @Mock
    private PantryItemService pantryItemService;

    @Mock
    private RecipeService recipeService;

    @Mock
    private RecipeImportService recipeImportService;

    @InjectMocks
    private CookingTools cookingTools;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        lenient().when(userContext.getUserId()).thenReturn(userId);
    }

    @Test
    void createRecipe_happyPath() {
        Ingredient tomato = Ingredient.builder().id(UUID.randomUUID()).name("Tomato").build();
        when(ingredientRepository.findAllByNameInIgnoreCase(any())).thenReturn(List.of(tomato));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe recipe = invocation.getArgument(0);
            recipe.setId(UUID.randomUUID());
            return recipe;
        });

        String result = cookingTools.createRecipe(
                "Pasta",
                "Simple pasta",
                List.of(new CookingTools.IngredientInput("Tomato", "2", "pcs", null)),
                List.of("Boil pasta", "Add sauce"));

        assertTrue(result.contains("Created recipe"));
        verify(recipeIngredientRepository).save(any(RecipeIngredient.class));
        verify(toolResultCollector).addResult(eq("createRecipe"), argThat(data ->
                data.containsKey("recipeId") && data.get("ingredientCount").equals(1)));
    }

    @Test
    void createRecipe_zeroIngredientsAllowed() {
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe recipe = invocation.getArgument(0);
            recipe.setId(UUID.randomUUID());
            return recipe;
        });

        cookingTools.createRecipe("Plain", null, List.of(), List.of("Step 1"));

        verify(recipeIngredientRepository, never()).save(any());
        verify(toolResultCollector).addResult(eq("createRecipe"), argThat(data -> data.get("ingredientCount").equals(0)));
    }

    @Test
    void createRecipe_rejectsMoreThanThirtyIngredients() {
        List<CookingTools.IngredientInput> ingredients = java.util.stream.IntStream.range(0, 31)
                .mapToObj(index -> new CookingTools.IngredientInput("item-" + index, "1", "pcs", null))
                .toList();

        String result = cookingTools.createRecipe("Too many", null, ingredients, List.of());

        assertTrue(result.contains("Too many ingredients"));
        verify(recipeRepository, never()).save(any());
        verify(toolResultCollector, never()).addResult(any(), any());
    }

    @Test
    void addItemsToShoppingList_bulkInsert() {
        String result = cookingTools.addItemsToShoppingList(List.of(
                new CookingTools.ShoppingItemInput("Milk", "1", "L"),
                new CookingTools.ShoppingItemInput("Eggs", "6", "pcs")));

        assertTrue(result.contains("Added 2 items"));
        verify(shoppingListService).insertAllShoppingListItems(eq(userId), argThat(items -> items.size() == 2));
        verify(toolResultCollector).addResult(eq("addItemsToShoppingList"), argThat(data -> data.get("itemsAdded").equals(2)));
    }

    @Test
    void addRecipeToMenu_usesMealPlanServiceWithDate() {
        UUID recipeId = UUID.randomUUID();
        Recipe recipe = Recipe.builder().id(recipeId).userId(userId).mealName("Soup").build();
        when(recipeRepository.findByIdAndUserId(recipeId, userId)).thenReturn(Optional.of(recipe));

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", UUID.randomUUID());
        created.put("meal_name", "Soup");
        created.put("meal_type", "dinner");
        created.put("serving_date", "2026-07-20");
        created.put("notEnoughItems", List.of());
        when(mealPlanService.createMealPlan(userId, recipeId, "dinner", "2026-07-20")).thenReturn(created);

        String result = cookingTools.addRecipeToMenu(recipeId.toString(), "2026-07-20", "dinner");

        assertTrue(result.contains("Added"));
        verify(mealPlanService).createMealPlan(userId, recipeId, "dinner", "2026-07-20");
        verify(toolResultCollector).addResult(eq("addRecipeToMenu"), argThat(data -> data.get("recipeName").equals("Soup")));
    }

    @Test
    void addRecipeToMenu_foreignRecipeFails() {
        UUID recipeId = UUID.randomUUID();
        when(recipeRepository.findByIdAndUserId(recipeId, userId)).thenReturn(Optional.empty());

        String result = cookingTools.addRecipeToMenu(recipeId.toString(), "2026-07-20", "dinner");

        assertEquals("Recipe not found or does not belong to you.", result);
        verify(mealPlanService, never()).createMealPlan(any(), any(), any(), any());
        verify(toolResultCollector, never()).addResult(any(), any());
    }

    @Test
    void addPantryItems_addsItemsViaService() {
        String result = cookingTools.addPantryItems(List.of(
                new CookingTools.PantryItemInput("Chicken", "2", "lbs", null)));

        assertTrue(result.contains("Added 1 item"));
        verify(pantryItemService).insertAllPantryItems(eq(userId), argThat(items -> items.size() == 1));
        verify(toolResultCollector).addResult(eq("addPantryItems"), argThat(data -> data.get("itemsAdded").equals(1)));
    }

    @Test
    void importRecipeFromUrl_createsRecipe() {
        Map<String, Object> extracted = Map.of(
                "name", "Imported Soup",
                "description", "Tasty",
                "ingredients", List.of(Map.of("name", "Carrot", "quantity", "2", "unit", "pcs")),
                "steps", List.of("Chop", "Cook"));
        when(recipeImportService.extractRecipeFromUrl("https://example.com/recipe")).thenReturn(extracted);
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe recipe = invocation.getArgument(0);
            recipe.setId(UUID.randomUUID());
            return recipe;
        });
        Ingredient carrot = Ingredient.builder().id(UUID.randomUUID()).name("Carrot").build();
        when(ingredientRepository.findAllByNameInIgnoreCase(any())).thenReturn(List.of());
        when(ingredientRepository.save(any(Ingredient.class))).thenReturn(carrot);

        String result = cookingTools.importRecipeFromUrl("https://example.com/recipe");

        assertTrue(result.contains("Imported recipe"));
        verify(toolResultCollector).addResult(eq("importRecipeFromUrl"), argThat(data -> data.get("imported").equals(true)));
    }
}
