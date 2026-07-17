package com.cookplanner.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealPlanDto {
    private UUID id;
    private UUID recipeId;
    private String mealType;
    private String servingDate;
}
