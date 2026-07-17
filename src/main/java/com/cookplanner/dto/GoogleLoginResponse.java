package com.cookplanner.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginResponse {
    private String token;
    private UserDto user;
}
