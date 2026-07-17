package com.cookplanner.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIngredientRequest {
    private @NotBlank String name;
    private String category;
}
