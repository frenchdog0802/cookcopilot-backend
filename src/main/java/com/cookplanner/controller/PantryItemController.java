package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.entity.PantryItem;
import com.cookplanner.dto.*;
import jakarta.validation.Valid;
import com.cookplanner.service.PantryItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pantry-item")
@RequiredArgsConstructor
public class PantryItemController {

    private final PantryItemService pantryItemService;

    private PantryItemDto toDto(PantryItem item) {
        Map<String, Object> details = new java.util.HashMap<>();
        if (item.getQuantity() != null) details.put("quantity", item.getQuantity());
        if (item.getUnit() != null) details.put("unit", item.getUnit());
        
        return PantryItemDto.builder()
                .id(item.getId())
                .name(null)
                .details(details)
                .build();
    }

    private PantryItemDto toDtoFromMap(Map<String, Object> map) {
        Object idObj = map.get("id");
        UUID id = null;
        if (idObj instanceof UUID) id = (UUID) idObj;
        else if (idObj != null) id = UUID.fromString(idObj.toString());

        return PantryItemDto.builder()
                .id(id)
                .name((String) map.get("name"))
                .details(map)
                .build();
    }

    private Map<String, Object> toMap(PantryItemDto dto) {
        Map<String, Object> map = new java.util.HashMap<>();
        if (dto.getDetails() != null) map.putAll(dto.getDetails());
        if (dto.getId() != null) map.put("id", dto.getId());
        if (dto.getName() != null) map.put("name", dto.getName());
        return map;
    }

    @PostMapping("/bulk")
    public ApiResponse<BulkInsertPantryItemsResponse> insertAll(
            Authentication auth, @Valid @RequestBody BulkInsertPantryItemsRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        List<Map<String, Object>> itemsToInsert = request.getItems().stream().map(this::toMap).toList();
        List<PantryItemDto> dtos = pantryItemService.insertAllPantryItems(userId, itemsToInsert).stream()
                .map(this::toDtoFromMap).toList();
        return ApiResponse.success(new BulkInsertPantryItemsResponse(dtos));
    }

    @PutMapping("/bulk")
    public ApiResponse<BulkUpdatePantryItemsResponse> updateAll(
            @Valid @RequestBody BulkUpdatePantryItemsRequest request) {
        List<Map<String, Object>> itemsToUpdate = request.getItems().stream().map(this::toMap).toList();
        List<PantryItemDto> dtos = pantryItemService.updateAllPantryItems(itemsToUpdate).stream()
                .map(this::toDtoFromMap).toList();
        return ApiResponse.success(new BulkUpdatePantryItemsResponse(dtos));
    }

    @GetMapping
    public ApiResponse<GetAllPantryItemsResponse> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<PantryItemDto> dtos = pantryItemService.getAllPantryItems(userId).stream()
                .map(this::toDtoFromMap).toList();
        return ApiResponse.success(new GetAllPantryItemsResponse(dtos));
    }

    @GetMapping("/{id}")
    public ApiResponse<GetPantryItemByIdResponse> getById(@PathVariable UUID id) {
        PantryItem item = pantryItemService.getPantryItemById(id);
        return ApiResponse.success(new GetPantryItemByIdResponse(toDto(item)));
    }

    @PostMapping
    public ApiResponse<CreatePantryItemResponse> create(
            Authentication auth, @Valid @RequestBody CreatePantryItemRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> body = new java.util.HashMap<>();
        if (request.getDetails() != null) body.putAll(request.getDetails());
        body.put("name", request.getName());
        
        Map<String, Object> result = pantryItemService.createPantryItem(userId, body);
        return ApiResponse.success(new CreatePantryItemResponse(toDtoFromMap(result)));
    }

    @PutMapping("/{id}")
    public ApiResponse<UpdatePantryItemResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdatePantryItemRequest request) {
        Map<String, Object> body = new java.util.HashMap<>();
        if (request.getDetails() != null) body.putAll(request.getDetails());
        if (request.getName() != null) body.put("name", request.getName());
        
        PantryItem result = pantryItemService.updatePantryItem(id, body);
        return ApiResponse.success(new UpdatePantryItemResponse(toDto(result)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeletePantryItemResponse> delete(@PathVariable UUID id) {
        pantryItemService.deletePantryItem(id);
        return ApiResponse.success(new DeletePantryItemResponse("Pantry item deleted"));
    }
}
