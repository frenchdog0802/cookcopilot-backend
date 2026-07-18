package com.cookcopilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIngredientRequest {
    private @NotBlank String name;
    private String category;

    @JsonProperty("unit_kind")
    private String unitKind;

    @JsonProperty("base_unit")
    private String baseUnit;

    @JsonProperty("default_display_unit")
    private String defaultDisplayUnit;

    @JsonProperty("default_unit")
    private String defaultUnit;
}
