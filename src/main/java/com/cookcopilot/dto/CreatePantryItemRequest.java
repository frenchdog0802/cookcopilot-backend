package com.cookcopilot.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePantryItemRequest {
    private @NotBlank String name;
    private Map<String, Object> details;
}
