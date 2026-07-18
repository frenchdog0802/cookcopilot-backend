package com.cookcopilot.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigninRequest {
    private @NotBlank @Email String email;
    private @NotBlank String password;
}
