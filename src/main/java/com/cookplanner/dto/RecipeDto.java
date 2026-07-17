package com.cookplanner.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeDto {
    private UUID id;
    private String mealName;
    private String instructions;
    private UUID folderId;
    private Map<String, String> image;
    private List<Map<String, Object>> ingredients;
}
