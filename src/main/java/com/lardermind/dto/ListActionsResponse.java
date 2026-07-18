package com.lardermind.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListActionsResponse {
    private List<String> actions;
    private String description;
}
