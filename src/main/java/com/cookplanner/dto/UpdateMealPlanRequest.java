package com.cookplanner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMealPlanRequest {
    @JsonProperty("meal_type")
    private String mealType;
    @JsonProperty("serving_date")
    private String servingDate;
}
