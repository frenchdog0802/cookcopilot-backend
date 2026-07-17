package com.cookplanner.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShoppingListItemResponse {
    private ShoppingListItemDto item;
}
