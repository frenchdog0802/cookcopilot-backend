package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/recipe")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(recipeService.getAllRecipes(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(recipeService.getRecipeById(id)));
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(Authentication auth, @RequestBody Map<String, Object> body) {
        UUID userId = (UUID) auth.getPrincipal();
        UUID folderId = body.get("folder_id") != null ? UUID.fromString(body.get("folder_id").toString()) : null;
        Map<String, String> image = (Map<String, String>) body.get("image");
        List<Map<String, Object>> ingredients = (List<Map<String, Object>>) body.get("ingredients");
        return ResponseEntity.ok(ApiResponse.success(
                recipeService.createRecipe(userId, (String) body.get("meal_name"),
                        (String) body.get("instructions"), folderId, image, ingredients)));
    }

    @PutMapping("/{id}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID folderId = body.get("folder_id") != null ? UUID.fromString(body.get("folder_id").toString()) : null;
        Map<String, String> image = (Map<String, String>) body.get("image");
        List<Map<String, Object>> ingredients = (List<Map<String, Object>>) body.get("ingredients");
        return ResponseEntity.ok(ApiResponse.success(
                recipeService.updateRecipe(id, (String) body.get("meal_name"),
                        (String) body.get("instructions"), folderId, image, ingredients)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(@PathVariable UUID id) {
        recipeService.deleteRecipe(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Recipe deleted")));
    }
}
