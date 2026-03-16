package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.service.ShoppingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> insertAll(
            Authentication auth, @RequestBody List<Map<String, Object>> items) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(shoppingListService.insertAllShoppingListItems(userId, items)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(shoppingListService.getAllShoppingListItems(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shoppingListService.getShoppingListItemById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            Authentication auth, @RequestBody Map<String, Object> body) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(shoppingListService.createShoppingListItem(userId, body)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success(shoppingListService.updateShoppingListItem(id, body)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(@PathVariable UUID id) {
        shoppingListService.deleteShoppingListItem(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Shopping list item deleted")));
    }
}
