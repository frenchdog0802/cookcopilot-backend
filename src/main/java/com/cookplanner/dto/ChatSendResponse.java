package com.cookplanner.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSendResponse {
    private String type;
    private String message;
    private Map<String, Object> data;
}
