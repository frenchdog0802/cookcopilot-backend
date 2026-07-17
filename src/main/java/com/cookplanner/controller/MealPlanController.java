package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.entity.MealPlan;
import com.cookplanner.dto.*;
import jakarta.validation.Valid;
import com.cookplanner.service.MealPlanService;
import lombok.RequiredArgsConstructor;
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

    private MealPlanDto toDto(MealPlan mealPlan) {
        return MealPlanDto.builder()
                .id(mealPlan.getId())
                .recipeId(mealPlan.getRecipeId())
                .mealType(mealPlan.getMealType())
                .servingDate(mealPlan.getServingDate())
                .build();
    }

    private MealPlanDto toDtoFromMap(Map<String, Object> map) {
        return MealPlanDto.builder()
                .id(map.get("id") != null ? UUID.fromString(map.get("id").toString()) : null)
                .recipeId(map.get("recipe_id") != null ? UUID.fromString(map.get("recipe_id").toString()) : null)
                .mealType((String) map.get("meal_type"))
                .servingDate((String) map.get("serving_date"))
                .build();
    }

    @GetMapping
    public ApiResponse<GetAllMealPlansResponse> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<MealPlanDto> dtos = mealPlanService.getAllMealPlans(userId).stream()
                .map(this::toDtoFromMap)
                .toList();
        return ApiResponse.success(new GetAllMealPlansResponse(dtos));
    }

    @GetMapping("/{id}")
    public ApiResponse<GetMealPlanByIdResponse> getById(@PathVariable UUID id) {
        MealPlan mealPlan = mealPlanService.getMealPlanById(id);
        return ApiResponse.success(new GetMealPlanByIdResponse(toDto(mealPlan)));
    }

    @PostMapping
    public ApiResponse<CreateMealPlanResponse> create(Authentication auth, @Valid @RequestBody CreateMealPlanRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> result = mealPlanService.createMealPlan(userId, request.getRecipeId(), request.getMealType(), request.getServingDate());
        return ApiResponse.success(new CreateMealPlanResponse(toDtoFromMap(result)));
    }

    @PutMapping("/{id}")
    public ApiResponse<UpdateMealPlanResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateMealPlanRequest request) {
        MealPlan updates = MealPlan.builder()
                .mealType(request.getMealType())
                .servingDate(request.getServingDate())
                .build();
        MealPlan result = mealPlanService.updateMealPlan(id, updates);
        return ApiResponse.success(new UpdateMealPlanResponse(toDto(result)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteMealPlanResponse> delete(@PathVariable UUID id, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        mealPlanService.deleteMealPlan(id, userId);
        return ApiResponse.success(new DeleteMealPlanResponse("Meal plan deleted"));
    }
}
