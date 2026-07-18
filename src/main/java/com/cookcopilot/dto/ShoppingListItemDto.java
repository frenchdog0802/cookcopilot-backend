package com.cookcopilot.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingListItemDto {
    private UUID id;
    private String name;
    private Map<String, Object> details;
}
