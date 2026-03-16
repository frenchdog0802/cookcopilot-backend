package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.entity.Folder;
import com.cookplanner.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/folder")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Folder>>> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(folderService.getAllFolders(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Folder>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(folderService.getFolderById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Folder>> create(Authentication auth, @RequestBody Map<String, String> body) {
        UUID userId = (UUID) auth.getPrincipal();
        Folder folder = folderService.createFolder(userId, body.get("name"), body.get("color"), body.get("icon"));
        return ResponseEntity.ok(ApiResponse.success(folder));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Folder>> update(@PathVariable UUID id, @RequestBody Folder updates) {
        return ResponseEntity.ok(ApiResponse.success(folderService.updateFolder(id, updates)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(@PathVariable UUID id) {
        folderService.deleteFolder(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Folder deleted")));
    }
}
