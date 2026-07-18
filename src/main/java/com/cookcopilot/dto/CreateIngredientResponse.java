package com.cookcopilot.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIngredientResponse {
    private IngredientDto ingredient;
}
