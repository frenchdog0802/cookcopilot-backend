package com.cookcopilot.controller;

import com.cookcopilot.common.ApiResponse;
import com.cookcopilot.entity.ShoppingListItem;
import com.cookcopilot.service.ShoppingListService;
import com.cookcopilot.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/shopping-list")
@RequiredArgsConstructor
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    private ShoppingListItemDto toDtoFromMap(Map<String, Object> map) {
        Object idObj = map.get("id");
        UUID id = null;
        if (idObj instanceof UUID) id = (UUID) idObj;
        else if (idObj != null) id = UUID.fromString(idObj.toString());

        return ShoppingListItemDto.builder()
                .id(id)
                .name((String) map.get("name"))
                .details(map)
                .build();
    }

    private Map<String, Object> toMap(ShoppingListItemDto dto) {
        Map<String, Object> map = new java.util.HashMap<>();
        if (dto.getDetails() != null) map.putAll(dto.getDetails());
        if (dto.getId() != null) map.put("id", dto.getId());
        if (dto.getName() != null) map.put("name", dto.getName());
        return map;
    }

    @PostMapping("/bulk")
    public ApiResponse<BulkInsertShoppingListItemsResponse> insertAll(
            Authentication auth, @Valid @RequestBody BulkInsertShoppingListItemsRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        List<Map<String, Object>> itemsToInsert = request.getItems().stream().map(this::toMap).toList();
        List<ShoppingListItemDto> dtos = shoppingListService.insertAllShoppingListItems(userId, itemsToInsert).stream()
                .map(this::toDtoFromMap).toList();
        return ApiResponse.success(new BulkInsertShoppingListItemsResponse(dtos));
    }

    @GetMapping
    public ApiResponse<GetAllShoppingListItemsResponse> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<ShoppingListItemDto> dtos = shoppingListService.getAllShoppingListItems(userId).stream()
                .map(this::toDtoFromMap).toList();
        return ApiResponse.success(new GetAllShoppingListItemsResponse(dtos));
    }

    @GetMapping("/{id}")
    @SuppressWarnings("unchecked")
    public ApiResponse<GetShoppingListItemByIdResponse> getById(@PathVariable UUID id) {
        Object obj = shoppingListService.getShoppingListItemById(id);
        ShoppingListItemDto dto = null;
        if (obj instanceof ShoppingListItem) {
            ShoppingListItem item = (ShoppingListItem) obj;
            Map<String, Object> details = new java.util.HashMap<>();
            if (item.getQuantity() != null) details.put("quantity", item.getQuantity());
            if (item.getUnit() != null) details.put("unit", item.getUnit());
            dto = ShoppingListItemDto.builder()
                    .id(item.getId())
                    .name(null)
                    .details(details).build();
        } else if (obj instanceof Map) {
            dto = toDtoFromMap((Map<String, Object>) obj);
        }
        return ApiResponse.success(new GetShoppingListItemByIdResponse(dto));
    }

    @PostMapping
    public ApiResponse<CreateShoppingListItemResponse> create(
            Authentication auth, @Valid @RequestBody CreateShoppingListItemRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> body = new java.util.HashMap<>();
        if (request.getDetails() != null) {
            body.putAll(request.getDetails());
        }
        if (request.getName() != null) {
            body.put("name", request.getName());
        }

        Map<String, Object> result = shoppingListService.createShoppingListItem(userId, body);
        return ApiResponse.success(new CreateShoppingListItemResponse(toDtoFromMap(result)));
    }

    @PutMapping("/{id}")
    public ApiResponse<UpdateShoppingListItemResponse> update(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {
        // Accept both { name, details: {...} } and flat { name, quantity, unit, checked }.
        Map<String, Object> body = new java.util.HashMap<>();
        Object details = request.get("details");
        if (details instanceof Map<?, ?> detailsMap) {
            for (Map.Entry<?, ?> entry : detailsMap.entrySet()) {
                if (entry.getKey() != null) {
                    body.put(entry.getKey().toString(), entry.getValue());
                }
            }
        }
        for (String key : List.of("name", "quantity", "unit", "checked", "ingredient_id")) {
            if (request.containsKey(key) && request.get(key) != null) {
                body.put(key, request.get(key));
            }
        }

        Map<String, Object> result = shoppingListService.updateShoppingListItem(id, body);
        return ApiResponse.success(new UpdateShoppingListItemResponse(toDtoFromMap(result)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteShoppingListItemResponse> delete(@PathVariable UUID id) {
        shoppingListService.deleteShoppingListItem(id);
        return ApiResponse.success(new DeleteShoppingListItemResponse("Shopping list item deleted"));
    }
}
