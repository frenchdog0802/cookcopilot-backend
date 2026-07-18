package com.cookcopilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientDto {
    private UUID id;
    private String name;
    private String category;

    @JsonProperty("unit_kind")
    private String unitKind;

    @JsonProperty("base_unit")
    private String baseUnit;

    @JsonProperty("default_display_unit")
    private String defaultDisplayUnit;

    /** Alias of baseUnit for older clients. */
    @JsonProperty("default_unit")
    private String defaultUnit;

    @JsonProperty("kind_locked")
    private Boolean kindLocked;
}
