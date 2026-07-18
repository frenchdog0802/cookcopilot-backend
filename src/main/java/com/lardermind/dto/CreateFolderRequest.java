package com.lardermind.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {
    private @NotBlank String name;
    private String color;
    private String icon;
}
