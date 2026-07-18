package com.cookcopilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSendRequest {
    @NotBlank
    @Size(max = 4000)
    private String message;

    @JsonProperty("recipe_context")
    private Map<String, Object> recipeContext;
}
