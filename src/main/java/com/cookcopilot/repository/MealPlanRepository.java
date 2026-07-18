package com.cookcopilot.repository;

import com.cookcopilot.entity.MealPlan;
import com.cookcopilot.entity.MealPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {
    List<MealPlan> findByUserId(UUID userId);
    List<MealPlan> findByUserIdAndServingDateGreaterThanEqual(UUID userId, String servingDate);
    List<MealPlan> findByUserIdAndStatus(UUID userId, MealPlanStatus status);
    List<MealPlan> findByStatusAndServingDateLessThan(MealPlanStatus status, String servingDate);
    List<MealPlan> findByStatusAndServingDateLessThanEqual(MealPlanStatus status, String servingDate);
    Optional<MealPlan> findByIdAndUserId(UUID id, UUID userId);
}
