package com.lardermind.repository;

import com.lardermind.entity.Step;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StepRepository extends JpaRepository<Step, UUID> {
    List<Step> findByRecipeIdOrderByStepNoAsc(UUID recipeId);
    void deleteByRecipeId(UUID recipeId);
}
