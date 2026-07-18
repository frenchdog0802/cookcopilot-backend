package com.cookcopilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealPlanDto {
    private UUID id;

    @JsonProperty("recipe_id")
    private UUID recipeId;

    @JsonProperty("meal_type")
    private String mealType;

    @JsonProperty("serving_date")
    private String servingDate;

    @JsonProperty("meal_name")
    private String mealName;

    @JsonProperty("image_url")
    private Map<String, String> imageUrl;

    private String status;
}
