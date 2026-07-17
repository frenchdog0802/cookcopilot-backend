package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.entity.Ingredient;
import com.cookplanner.dto.*;
import jakarta.validation.Valid;
import com.cookplanner.service.IngredientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingredient")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;

    private IngredientDto toDto(Ingredient ingredient) {
        return IngredientDto.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .build();
    }

    private Ingredient toEntity(IngredientDto dto) {
        return Ingredient.builder()
                .id(dto.getId())
                .name(dto.getName())
                .build();
    }

    @PostMapping("/bulk")
    public ApiResponse<BulkInsertIngredientsResponse> insertAll(@Valid @RequestBody BulkInsertIngredientsRequest request) {
        List<Ingredient> ingredientsToInsert = request.getIngredients().stream().map(this::toEntity).toList();
        List<IngredientDto> result = ingredientService.insertAll(ingredientsToInsert).stream().map(this::toDto).toList();
        return ApiResponse.success(new BulkInsertIngredientsResponse(result));
    }

    @GetMapping
    public ApiResponse<GetAllIngredientsResponse> getAll(@RequestParam(required = false) String query) {
        List<IngredientDto> result = ingredientService.getAllIngredients(query).stream().map(this::toDto).toList();
        return ApiResponse.success(new GetAllIngredientsResponse(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<GetIngredientByIdResponse> getById(@PathVariable UUID id) {
        Ingredient ingredient = ingredientService.getIngredientById(id);
        return ApiResponse.success(new GetIngredientByIdResponse(toDto(ingredient)));
    }

    @PostMapping
    public ApiResponse<CreateIngredientResponse> create(@Valid @RequestBody CreateIngredientRequest request) {
        Ingredient ingredient = Ingredient.builder()
                .name(request.getName())
                .build();
        Ingredient result = ingredientService.createIngredient(ingredient);
        return ApiResponse.success(new CreateIngredientResponse(toDto(result)));
    }

    @PutMapping("/{id}")
    public ApiResponse<UpdateIngredientResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateIngredientRequest request) {
        Ingredient updates = Ingredient.builder()
                .name(request.getName())
                .build();
        Ingredient result = ingredientService.updateIngredient(id, updates);
        return ApiResponse.success(new UpdateIngredientResponse(toDto(result)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteIngredientResponse> delete(@PathVariable UUID id) {
        ingredientService.deleteIngredient(id);
        return ApiResponse.success(new DeleteIngredientResponse("Ingredient deleted"));
    }
}
