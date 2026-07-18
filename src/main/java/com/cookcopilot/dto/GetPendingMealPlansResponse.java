package com.cookcopilot.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetPendingMealPlansResponse {
    private List<MealPlanDto> mealPlans;
}
