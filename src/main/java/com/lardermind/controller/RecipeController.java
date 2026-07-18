package com.lardermind.controller;

import com.lardermind.common.ApiResponse;
import com.lardermind.service.RecipeService;
import com.lardermind.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/recipe")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @SuppressWarnings("unchecked")
    private RecipeDto toDtoFromMap(Map<String, Object> map) {
        Object idObj = map.get("id");
        UUID id = null;
        if (idObj instanceof UUID) id = (UUID) idObj;
        else if (idObj != null) id = UUID.fromString(idObj.toString());

        Object folderIdObj = map.get("folder_id");
        UUID folderId = null;
        if (folderIdObj instanceof UUID) folderId = (UUID) folderIdObj;
        else if (folderIdObj != null) folderId = UUID.fromString(folderIdObj.toString());

        return RecipeDto.builder()
                .id(id)
                .mealName((String) map.get("meal_name"))
                .instructions((String) map.get("instructions"))
                .folderId(folderId)
                .image((Map<String, String>) map.get("image"))
                .ingredients((List<Map<String, Object>>) map.get("ingredients"))
                .build();
    }

    @GetMapping
    public ApiResponse<GetAllRecipesResponse> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<RecipeDto> dtos = recipeService.getAllRecipes(userId).stream().map(this::toDtoFromMap).toList();
        return ApiResponse.success(new GetAllRecipesResponse(dtos));
    }

    @GetMapping("/{id}")
    public ApiResponse<GetRecipeByIdResponse> getById(@PathVariable UUID id) {
        Map<String, Object> result = recipeService.getRecipeById(id);
        return ApiResponse.success(new GetRecipeByIdResponse(toDtoFromMap(result)));
    }

    @PostMapping
    public ApiResponse<CreateRecipeResponse> create(Authentication auth, @Valid @RequestBody CreateRecipeRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> result = recipeService.createRecipe(userId, request.getMealName(),
                request.getInstructions(), request.getFolderId(), request.getImage(), request.getIngredients());
        return ApiResponse.success(new CreateRecipeResponse(toDtoFromMap(result)));
    }

    @PutMapping("/{id}")
    public ApiResponse<UpdateRecipeResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRecipeRequest request) {
        Map<String, Object> result = recipeService.updateRecipe(id, request.getMealName(),
                request.getInstructions(), request.getFolderId(), request.getImage(), request.getIngredients());
        return ApiResponse.success(new UpdateRecipeResponse(toDtoFromMap(result)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteRecipeResponse> delete(@PathVariable UUID id) {
        recipeService.deleteRecipe(id);
        return ApiResponse.success(new DeleteRecipeResponse("Recipe deleted"));
    }
}
