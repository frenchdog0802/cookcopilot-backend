package com.lardermind.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetAllPantryItemsResponse {
    private List<PantryItemDto> items;
}
