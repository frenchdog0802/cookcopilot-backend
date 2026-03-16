package com.cookplanner.service;

import com.cookplanner.common.GlobalExceptionHandler.*;
import com.cookplanner.entity.Folder;
import com.cookplanner.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;

    public List<Folder> getAllFolders(UUID userId) {
        return folderRepository.findByUserId(userId);
    }

    public Folder getFolderById(UUID id) {
        return folderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
    }

    public Folder createFolder(UUID userId, String name, String color, String icon) {
        Folder folder = Folder.builder()
                .userId(userId)
                .name(name)
                .color(color)
                .icon(icon)
                .build();
        return folderRepository.save(folder);
    }

    public Folder updateFolder(UUID id, Folder updates) {
        Folder folder = getFolderById(id);
        if (updates.getName() != null) folder.setName(updates.getName());
        if (updates.getColor() != null) folder.setColor(updates.getColor());
        if (updates.getIcon() != null) folder.setIcon(updates.getIcon());
        return folderRepository.save(folder);
    }

    public void deleteFolder(UUID id) {
        if (!folderRepository.existsById(id)) throw new ResourceNotFoundException("Folder not found");
        folderRepository.deleteById(id);
    }
}
