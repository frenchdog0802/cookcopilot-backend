package com.cookplanner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {
    @JsonProperty("first_name")
    private @NotBlank String firstName;
    @JsonProperty("last_name")
    private @NotBlank String lastName;
    private @NotBlank @Email String email;
    private @NotBlank String password;
}
