package com.lardermind.controller;

import com.lardermind.common.ApiResponse;
import com.lardermind.dto.*;
import com.lardermind.entity.MealPlan;
import com.lardermind.repository.RecipeRepository;
import com.lardermind.service.MealPlanService;
import jakarta.validation.Valid;
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
    private final RecipeRepository recipeRepository;

    private MealPlanDto toDto(MealPlan mealPlan) {
        String mealName = recipeRepository.findById(mealPlan.getRecipeId())
                .map(recipe -> recipe.getMealName())
                .orElse(null);
        return MealPlanDto.builder()
                .id(mealPlan.getId())
                .recipeId(mealPlan.getRecipeId())
                .mealType(mealPlan.getMealType())
                .servingDate(mealPlan.getServingDate())
                .mealName(mealName)
                .status(mealPlan.getStatus() != null ? mealPlan.getStatus().name() : "PLANNED")
                .build();
    }

    @SuppressWarnings("unchecked")
    private MealPlanDto toDtoFromMap(Map<String, Object> map) {
        return MealPlanDto.builder()
                .id(map.get("id") != null ? UUID.fromString(map.get("id").toString()) : null)
                .recipeId(map.get("recipe_id") != null ? UUID.fromString(map.get("recipe_id").toString()) : null)
                .mealType((String) map.get("meal_type"))
                .servingDate((String) map.get("serving_date"))
                .mealName((String) map.get("meal_name"))
                .imageUrl((Map<String, String>) map.get("image_url"))
                .status(map.get("status") != null ? map.get("status").toString() : "PLANNED")
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

    @GetMapping("/pending-confirm")
    public ApiResponse<GetPendingMealPlansResponse> getPendingConfirm(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<MealPlanDto> dtos = mealPlanService.getPendingConfirmations(userId).stream()
                .map(this::toDtoFromMap)
                .toList();
        return ApiResponse.success(new GetPendingMealPlansResponse(dtos));
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

    @PostMapping("/{id}/confirm")
    @SuppressWarnings("unchecked")
    public ApiResponse<ConfirmMealPlanResponse> confirm(@PathVariable UUID id, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> result = mealPlanService.confirmMealPlan(userId, id);
        List<Map<String, Object>> shortages = (List<Map<String, Object>>) result.getOrDefault("shortages", List.of());
        List<Map<String, Object>> deducted = (List<Map<String, Object>>) result.getOrDefault("deducted", List.of());
        boolean alreadyConfirmed = Boolean.TRUE.equals(result.get("already_confirmed"));
        return ApiResponse.success(ConfirmMealPlanResponse.builder()
                .mealPlan(toDtoFromMap(result))
                .shortages(shortages)
                .deducted(deducted)
                .alreadyConfirmed(alreadyConfirmed)
                .build());
    }

    @PostMapping("/{id}/skip")
    public ApiResponse<SkipMealPlanResponse> skip(@PathVariable UUID id, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> result = mealPlanService.skipMealPlan(userId, id);
        boolean alreadySkipped = Boolean.TRUE.equals(result.get("already_skipped"));
        return ApiResponse.success(SkipMealPlanResponse.builder()
                .mealPlan(toDtoFromMap(result))
                .alreadySkipped(alreadySkipped)
                .build());
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
