package com.cookcopilot.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmMealPlanResponse {
    private MealPlanDto mealPlan;
    private List<Map<String, Object>> shortages;
    private List<Map<String, Object>> deducted;
    private boolean alreadyConfirmed;
}
