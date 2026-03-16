package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.entity.MealPlan;
import com.cookplanner.service.MealPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/meal-plan")
@RequiredArgsConstructor
public class MealPlanController {

    private final MealPlanService mealPlanService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(mealPlanService.getAllMealPlans(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MealPlan>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(mealPlanService.getMealPlanById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(Authentication auth, @RequestBody Map<String, String> body) {
        UUID userId = (UUID) auth.getPrincipal();
        UUID recipeId = UUID.fromString(body.get("recipe_id"));
        return ResponseEntity.ok(ApiResponse.success(
                mealPlanService.createMealPlan(userId, recipeId, body.get("meal_type"), body.get("serving_date"))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MealPlan>> update(@PathVariable UUID id, @RequestBody MealPlan updates) {
        return ResponseEntity.ok(ApiResponse.success(mealPlanService.updateMealPlan(id, updates)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(@PathVariable UUID id, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        mealPlanService.deleteMealPlan(id, userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Meal plan deleted")));
    }
}
