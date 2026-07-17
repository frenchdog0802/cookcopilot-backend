package com.cookplanner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Auth0LoginRequest {
    @JsonProperty("id_token")
    private @NotBlank String idToken;
}
