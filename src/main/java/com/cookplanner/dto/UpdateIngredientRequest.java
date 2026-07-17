package com.cookplanner.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateIngredientRequest {
    private String name;
    private String category;
}
