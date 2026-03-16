package com.cookplanner.repository;

import com.cookplanner.entity.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {
    List<MealPlan> findByUserId(UUID userId);
    List<MealPlan> findByUserIdAndServingDateGreaterThanEqual(UUID userId, String servingDate);
    Optional<MealPlan> findByIdAndUserId(UUID id, UUID userId);
}
