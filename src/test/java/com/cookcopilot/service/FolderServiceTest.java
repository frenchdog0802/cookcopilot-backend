package com.cookcopilot.service;

import com.cookcopilot.entity.Folder;
import com.cookcopilot.entity.Recipe;
import com.cookcopilot.repository.FolderRepository;
import com.cookcopilot.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private RecipeRepository recipeRepository;

    @InjectMocks
    private FolderService folderService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void getAllFolders_dedupesDuplicateNamesAndKeepsOldest() {
        Folder keep = Folder.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Lunch")
                .createdAt(100L)
                .build();
        Folder duplicate = Folder.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Lunch")
                .createdAt(200L)
                .build();
        Folder uncategorized = Folder.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Uncategorized")
                .createdAt(50L)
                .build();

        when(folderRepository.findByUserId(userId))
                .thenReturn(List.of(keep, duplicate, uncategorized))
                .thenReturn(List.of(keep, uncategorized));
        when(recipeRepository.findByUserIdAndFolderId(userId, duplicate.getId())).thenReturn(List.of());
        when(folderRepository.findByUserIdAndNameIgnoreCase(eq(userId), any())).thenAnswer(invocation -> {
            String name = invocation.getArgument(1);
            if (name.equalsIgnoreCase("Uncategorized")) {
                return Optional.of(uncategorized);
            }
            if (name.equalsIgnoreCase("Lunch")) {
                return Optional.of(keep);
            }
            return Optional.empty();
        });
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            if (folder.getId() == null) {
                folder.setId(UUID.randomUUID());
            }
            return folder;
        });
        when(recipeRepository.findByUserId(userId)).thenReturn(List.of());

        List<Folder> result = folderService.getAllFolders(userId);

        verify(folderRepository).delete(duplicate);
        assertTrue(result.stream().noneMatch(f -> f.getId().equals(duplicate.getId())));
        assertEquals(1, result.stream().filter(f -> f.getName().equalsIgnoreCase("Lunch")).count());
    }

    @Test
    void getOrCreateUncategorized_createsWhenMissing() {
        when(folderRepository.findByUserIdAndNameIgnoreCase(userId, "Uncategorized"))
                .thenReturn(Optional.empty());
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            folder.setId(UUID.randomUUID());
            return folder;
        });

        Folder created = folderService.getOrCreateUncategorized(userId);

        assertEquals("Uncategorized", created.getName());
        ArgumentCaptor<Folder> captor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository).save(captor.capture());
        assertEquals("Uncategorized", captor.getValue().getName());
    }

    @Test
    void getAllFolders_assignsOrphanRecipesToUncategorized() {
        Folder uncategorized = Folder.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Uncategorized")
                .createdAt(1L)
                .build();
        Recipe orphan = Recipe.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .mealName("Imported Soup")
                .folderId(null)
                .build();

        when(folderRepository.findByUserId(userId)).thenReturn(List.of(uncategorized));
        when(folderRepository.findByUserIdAndNameIgnoreCase(eq(userId), any())).thenAnswer(invocation -> {
            String name = invocation.getArgument(1);
            if (name.equalsIgnoreCase("Uncategorized")) {
                return Optional.of(uncategorized);
            }
            return Optional.empty();
        });
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            if (folder.getId() == null) {
                folder.setId(UUID.randomUUID());
            }
            return folder;
        });
        when(recipeRepository.findByUserId(userId)).thenReturn(List.of(orphan));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        folderService.getAllFolders(userId);

        assertEquals(uncategorized.getId(), orphan.getFolderId());
        verify(recipeRepository).save(orphan);
    }
}
