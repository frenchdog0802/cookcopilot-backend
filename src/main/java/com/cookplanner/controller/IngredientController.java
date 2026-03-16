package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.entity.Ingredient;
import com.cookplanner.service.IngredientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingredient")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<Ingredient>>> insertAll(@RequestBody List<Ingredient> ingredients) {
        return ResponseEntity.ok(ApiResponse.success(ingredientService.insertAll(ingredients)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Ingredient>>> getAll(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(ApiResponse.success(ingredientService.getAllIngredients(query)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Ingredient>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(ingredientService.getIngredientById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Ingredient>> create(@RequestBody Ingredient ingredient) {
        return ResponseEntity.ok(ApiResponse.success(ingredientService.createIngredient(ingredient)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Ingredient>> update(@PathVariable UUID id, @RequestBody Ingredient updates) {
        return ResponseEntity.ok(ApiResponse.success(ingredientService.updateIngredient(id, updates)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(@PathVariable UUID id) {
        ingredientService.deleteIngredient(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Ingredient deleted")));
    }
}
