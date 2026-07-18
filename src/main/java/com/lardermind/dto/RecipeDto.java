package com.lardermind.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeDto {
    private UUID id;

    @JsonProperty("meal_name")
    private String mealName;

    private String instructions;

    @JsonProperty("folder_id")
    private UUID folderId;

    private Map<String, String> image;
    private List<Map<String, Object>> ingredients;
}
