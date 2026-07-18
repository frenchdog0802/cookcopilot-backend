package com.lardermind.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateIngredientRequest {
    private String name;
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
