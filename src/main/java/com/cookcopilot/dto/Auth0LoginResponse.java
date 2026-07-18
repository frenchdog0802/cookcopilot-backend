package com.cookcopilot.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Auth0LoginResponse {
    private String token;
    private UserDto user;
}
