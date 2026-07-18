import os
import re

pkg_dir = r"C:\Users\bert5\IdeaProjects\Cook-Planner\src\main\java\com\cookplanner\dto"
os.makedirs(pkg_dir, exist_ok=True)

def write_dto(name, fields, annotations=None, imports=None):
    if annotations is None:
        annotations = ['@Data', '@Builder', '@NoArgsConstructor', '@AllArgsConstructor']
    if imports is None:
        imports = []
    
    content = f"package com.cookplanner.dto;\n\n"
    if any("Valid" in f or "NotNull" in f or "NotBlank" in f or "Email" in f for f in fields):
        content += "import jakarta.validation.constraints.*;\n"
    content += "import lombok.*;\n"
    content += "import java.util.*;\n"
    for imp in imports:
        content += f"import {imp};\n"
    content += "\n"
    for ann in annotations:
        content += f"{ann}\n"
    content += f"public class {name} {{\n"
    for f in fields:
        content += f"    private {f};\n"
    
    content += "}\n"
    
    with open(os.path.join(pkg_dir, f"{name}.java"), "w", encoding="utf-8") as f:
        f.write(content)

# Auth
write_dto('SignupRequest', ['@NotBlank String firstName', '@NotBlank String lastName', '@NotBlank @Email String email', '@NotBlank String password'])
write_dto('SignupResponse', ['String token', 'UserDto user'])
write_dto('SigninRequest', ['@NotBlank @Email String email', '@NotBlank String password'])
write_dto('SigninResponse', ['String token', 'UserDto user'])
write_dto('SignoutResponse', ['String message'])
write_dto('GoogleLoginRequest', ['@NotBlank String token'])
write_dto('GoogleLoginResponse', ['String token', 'UserDto user'])

# Chat
write_dto('ChatSendRequest', ['@NotBlank String message', 'Map<String, Object> recipeContext'])
write_dto('ChatSendResponse', ['String type', 'String message', 'Map<String, Object> data'])
write_dto('ListActionsResponse', ['List<String> actions', 'String description'])

# Folder
write_dto('FolderDto', ['UUID id', 'String name', 'String color', 'String icon'])
write_dto('GetAllFoldersResponse', ['List<FolderDto> folders'])
write_dto('GetFolderByIdResponse', ['FolderDto folder'])
write_dto('CreateFolderRequest', ['@NotBlank String name', 'String color', 'String icon'])
write_dto('CreateFolderResponse', ['FolderDto folder'])
write_dto('UpdateFolderRequest', ['String name', 'String color', 'String icon'])
write_dto('UpdateFolderResponse', ['FolderDto folder'])
write_dto('DeleteFolderResponse', ['String message'])

# Health
write_dto('HealthResponse', ['String status', 'Long timestamp'])

# Ingredient
write_dto('IngredientDto', ['UUID id', 'String name', 'String category'])
write_dto('BulkInsertIngredientsRequest', ['List<IngredientDto> ingredients'])
write_dto('BulkInsertIngredientsResponse', ['List<IngredientDto> ingredients'])
write_dto('GetAllIngredientsResponse', ['List<IngredientDto> ingredients'])
write_dto('GetIngredientByIdResponse', ['IngredientDto ingredient'])
write_dto('CreateIngredientRequest', ['@NotBlank String name', 'String category'])
write_dto('CreateIngredientResponse', ['IngredientDto ingredient'])
write_dto('UpdateIngredientRequest', ['String name', 'String category'])
write_dto('UpdateIngredientResponse', ['IngredientDto ingredient'])
write_dto('DeleteIngredientResponse', ['String message'])

# MealPlan
write_dto('MealPlanDto', ['UUID id', 'UUID recipeId', 'String mealType', 'String servingDate'])
write_dto('GetAllMealPlansResponse', ['List<MealPlanDto> mealPlans'])
write_dto('GetMealPlanByIdResponse', ['MealPlanDto mealPlan'])
write_dto('CreateMealPlanRequest', ['@NotNull UUID recipeId', '@NotBlank String mealType', '@NotBlank String servingDate'])
write_dto('CreateMealPlanResponse', ['MealPlanDto mealPlan'])
write_dto('UpdateMealPlanRequest', ['String mealType', 'String servingDate'])
write_dto('UpdateMealPlanResponse', ['MealPlanDto mealPlan'])
write_dto('DeleteMealPlanResponse', ['String message'])

# PantryItem
write_dto('PantryItemDto', ['UUID id', 'String name', 'Map<String, Object> details'])
write_dto('BulkInsertPantryItemsRequest', ['List<PantryItemDto> items'])
write_dto('BulkInsertPantryItemsResponse', ['List<PantryItemDto> items'])
write_dto('BulkUpdatePantryItemsRequest', ['List<PantryItemDto> items'])
write_dto('BulkUpdatePantryItemsResponse', ['List<PantryItemDto> items'])
write_dto('GetAllPantryItemsResponse', ['List<PantryItemDto> items'])
write_dto('GetPantryItemByIdResponse', ['PantryItemDto item'])
write_dto('CreatePantryItemRequest', ['@NotBlank String name', 'Map<String, Object> details'])
write_dto('CreatePantryItemResponse', ['PantryItemDto item'])
write_dto('UpdatePantryItemRequest', ['String name', 'Map<String, Object> details'])
write_dto('UpdatePantryItemResponse', ['PantryItemDto item'])
write_dto('DeletePantryItemResponse', ['String message'])

# Recipe
write_dto('RecipeDto', ['UUID id', 'String mealName', 'String instructions', 'UUID folderId', 'Map<String, String> image', 'List<Map<String, Object>> ingredients'])
write_dto('GetAllRecipesResponse', ['List<RecipeDto> recipes'])
write_dto('GetRecipeByIdResponse', ['RecipeDto recipe'])
write_dto('CreateRecipeRequest', ['@NotBlank String mealName', 'String instructions', 'UUID folderId', 'Map<String, String> image', 'List<Map<String, Object>> ingredients'])
write_dto('CreateRecipeResponse', ['RecipeDto recipe'])
write_dto('UpdateRecipeRequest', ['String mealName', 'String instructions', 'UUID folderId', 'Map<String, String> image', 'List<Map<String, Object>> ingredients'])
write_dto('UpdateRecipeResponse', ['RecipeDto recipe'])
write_dto('DeleteRecipeResponse', ['String message'])

# ShoppingList
write_dto('ShoppingListItemDto', ['UUID id', 'String name', 'Map<String, Object> details'])
write_dto('BulkInsertShoppingListItemsRequest', ['List<ShoppingListItemDto> items'])
write_dto('BulkInsertShoppingListItemsResponse', ['List<ShoppingListItemDto> items'])
write_dto('GetAllShoppingListItemsResponse', ['List<ShoppingListItemDto> items'])
write_dto('GetShoppingListItemByIdResponse', ['ShoppingListItemDto item'])
write_dto('CreateShoppingListItemRequest', ['@NotBlank String name', 'Map<String, Object> details'])
write_dto('CreateShoppingListItemResponse', ['ShoppingListItemDto item'])
write_dto('UpdateShoppingListItemRequest', ['String name', 'Map<String, Object> details'])
write_dto('UpdateShoppingListItemResponse', ['ShoppingListItemDto item'])
write_dto('DeleteShoppingListItemResponse', ['String message'])

# Upload
write_dto('UploadImageResponse', ['String imageUrl', 'String publicId'])
write_dto('DeleteImageResponse', ['String message'])

# User
write_dto('UserDto', ['UUID id', 'String firstName', 'String lastName', 'String email'])
write_dto('ListUsersResponse', ['List<UserDto> users'])
write_dto('ReadUserResponse', ['UserDto user'])
write_dto('UpdateUserRequest', ['String firstName', 'String lastName', '@Email String email'])
write_dto('UpdateUserResponse', ['UserDto user'])
write_dto('DeleteUserResponse', ['String message'])

print('DTOs generated successfully!')
