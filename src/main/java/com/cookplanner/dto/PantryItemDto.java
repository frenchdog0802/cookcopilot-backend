package com.cookplanner.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PantryItemDto {
    private UUID id;
    private String name;
    private Map<String, Object> details;
}
