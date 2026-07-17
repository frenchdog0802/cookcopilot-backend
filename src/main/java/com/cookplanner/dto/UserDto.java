package com.cookplanner.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String name;
    private String email;
}
