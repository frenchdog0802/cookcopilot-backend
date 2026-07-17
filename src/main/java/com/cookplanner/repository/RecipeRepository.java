package com.cookplanner.repository;

import com.cookplanner.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    List<Recipe> findByUserId(UUID userId);

    List<Recipe> findByUserIdAndFolderId(UUID userId, UUID folderId);

    Optional<Recipe> findByIdAndUserId(UUID id, UUID userId);
}
