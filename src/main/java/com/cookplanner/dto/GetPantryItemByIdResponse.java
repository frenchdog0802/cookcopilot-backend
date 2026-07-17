package com.cookplanner.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetPantryItemByIdResponse {
    private PantryItemDto item;
}
