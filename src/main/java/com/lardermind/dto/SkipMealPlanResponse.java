package com.lardermind.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkipMealPlanResponse {
    private MealPlanDto mealPlan;
    private boolean alreadySkipped;
}
