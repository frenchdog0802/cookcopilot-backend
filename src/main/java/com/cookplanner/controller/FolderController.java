package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.entity.Folder;
import com.cookplanner.dto.*;
import jakarta.validation.Valid;
import com.cookplanner.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/folder")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    private FolderDto toDto(Folder folder) {
        return FolderDto.builder()
                .id(folder.getId())
                .name(folder.getName())
                .color(folder.getColor())
                .icon(folder.getIcon())
                .build();
    }

    @GetMapping
    public ApiResponse<GetAllFoldersResponse> getAll(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<FolderDto> dtos = folderService.getAllFolders(userId).stream()
                .map(this::toDto)
                .toList();
        return ApiResponse.success(new GetAllFoldersResponse(dtos));
    }

    @GetMapping("/{id}")
    public ApiResponse<GetFolderByIdResponse> getById(@PathVariable UUID id) {
        Folder folder = folderService.getFolderById(id);
        return ApiResponse.success(new GetFolderByIdResponse(toDto(folder)));
    }

    @PostMapping
    public ApiResponse<CreateFolderResponse> create(Authentication auth, @Valid @RequestBody CreateFolderRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        Folder folder = folderService.createFolder(userId, request.getName(), request.getColor(), request.getIcon());
        return ApiResponse.success(new CreateFolderResponse(toDto(folder)));
    }

    @PutMapping("/{id}")
    public ApiResponse<UpdateFolderResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateFolderRequest request) {
        Folder updates = Folder.builder()
                .name(request.getName())
                .color(request.getColor())
                .icon(request.getIcon())
                .build();
        Folder folder = folderService.updateFolder(id, updates);
        return ApiResponse.success(new UpdateFolderResponse(toDto(folder)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteFolderResponse> delete(@PathVariable UUID id) {
        folderService.deleteFolder(id);
        return ApiResponse.success(new DeleteFolderResponse("Folder deleted"));
    }
}
