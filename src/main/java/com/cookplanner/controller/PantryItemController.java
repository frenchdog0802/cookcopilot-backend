package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.entity.PantryItem;
import com.cookplanner.service.PantryItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> insertAll(
            Authentication auth, @RequestBody List<Map<String, Object>> items) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(pantryItemService.insertAllPantryItems(userId, items)));
    }

    @PutMapping("/bulk")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> updateAll(
            @RequestBody List<Map<String, Object>> items) {
        return ResponseEntity.ok(ApiResponse.success(pantryItemService.updateAllPantryItems(items)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(pantryItemService.getAllPantryItems(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PantryItem>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pantryItemService.getPantryItemById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            Authentication auth, @RequestBody Map<String, Object> body) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(pantryItemService.createPantryItem(userId, body)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PantryItem>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success(pantryItemService.updatePantryItem(id, body)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(@PathVariable UUID id) {
        pantryItemService.deletePantryItem(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Pantry item deleted")));
    }
}
