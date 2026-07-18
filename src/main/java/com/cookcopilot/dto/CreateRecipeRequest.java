package com.cookcopilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRecipeRequest {
    @JsonProperty("meal_name")
    private @NotBlank String mealName;
    private String instructions;
    @JsonProperty("folder_id")
    private UUID folderId;
    private Map<String, String> image;
    private List<Map<String, Object>> ingredients;
}
