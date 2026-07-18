package com.cookcopilot.service;

import com.cookcopilot.common.GlobalExceptionHandler.*;
import com.cookcopilot.entity.Folder;
import com.cookcopilot.entity.Recipe;
import com.cookcopilot.repository.FolderRepository;
import com.cookcopilot.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {

    public static final String UNCATEGORIZED_NAME = "Uncategorized";

    private static final List<String> DEFAULT_FOLDER_NAMES = List.of(
            UNCATEGORIZED_NAME,
            "Favorites",
            "Breakfast",
            "Lunch",
            "Dinner");

    private final FolderRepository folderRepository;
    private final RecipeRepository recipeRepository;

    @Transactional
    public List<Folder> getAllFolders(UUID userId) {
        dedupeFoldersByName(userId);
        ensureDefaultFolders(userId);
        assignOrphanRecipesToUncategorized(userId);
        return folderRepository.findByUserId(userId);
    }

    public Folder getFolderById(UUID id) {
        return folderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
    }

    public Folder createFolder(UUID userId, String name, String color, String icon) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Folder name is required");
        }
        return folderRepository.findByUserIdAndNameIgnoreCase(userId, trimmed)
                .orElseGet(() -> folderRepository.save(Folder.builder()
                        .userId(userId)
                        .name(trimmed)
                        .color(color)
                        .icon(icon == null ? "FolderIcon" : icon)
                        .build()));
    }

    public Folder getOrCreateUncategorized(UUID userId) {
        return findOrCreateByName(userId, UNCATEGORIZED_NAME);
    }

    public Folder findOrCreateByName(UUID userId, String name) {
        String trimmed = name.trim();
        return folderRepository.findByUserIdAndNameIgnoreCase(userId, trimmed)
                .orElseGet(() -> folderRepository.save(Folder.builder()
                        .userId(userId)
                        .name(trimmed)
                        .icon("FolderIcon")
                        .build()));
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

    private void ensureDefaultFolders(UUID userId) {
        for (String name : DEFAULT_FOLDER_NAMES) {
            findOrCreateByName(userId, name);
        }
    }

    /**
     * Collapses duplicate folder names for a user (e.g. from client seed races),
     * keeping the oldest folder and moving recipes onto it.
     */
    private void dedupeFoldersByName(UUID userId) {
        List<Folder> folders = folderRepository.findByUserId(userId);
        Map<String, List<Folder>> byName = new LinkedHashMap<>();
        for (Folder folder : folders) {
            String key = folder.getName() == null ? "" : folder.getName().trim().toLowerCase(Locale.ROOT);
            byName.computeIfAbsent(key, ignored -> new ArrayList<>()).add(folder);
        }

        for (List<Folder> group : byName.values()) {
            if (group.size() <= 1) {
                continue;
            }
            group.sort(Comparator
                    .comparing(Folder::getCreatedAt, Comparator.nullsLast(Long::compareTo))
                    .thenComparing(Folder::getId, Comparator.comparing(UUID::toString)));
            Folder keep = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                Folder duplicate = group.get(i);
                List<Recipe> recipes = recipeRepository.findByUserIdAndFolderId(userId, duplicate.getId());
                for (Recipe recipe : recipes) {
                    recipe.setFolderId(keep.getId());
                    recipeRepository.save(recipe);
                }
                folderRepository.delete(duplicate);
            }
        }
    }

    private void assignOrphanRecipesToUncategorized(UUID userId) {
        Folder uncategorized = getOrCreateUncategorized(userId);
        for (Recipe recipe : recipeRepository.findByUserId(userId)) {
            if (recipe.getFolderId() == null) {
                recipe.setFolderId(uncategorized.getId());
                recipeRepository.save(recipe);
            }
        }
    }
}
