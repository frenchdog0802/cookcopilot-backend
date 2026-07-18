package com.cookcopilot.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkInsertPantryItemsResponse {
    private List<PantryItemDto> items;
}
