package com.cookcopilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMealPlanRequest {
    @JsonProperty("recipe_id")
    private @NotNull UUID recipeId;
    @JsonProperty("meal_type")
    private @NotBlank String mealType;
    @JsonProperty("serving_date")
    private @NotBlank String servingDate;
}
