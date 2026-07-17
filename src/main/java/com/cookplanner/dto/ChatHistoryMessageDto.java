package com.cookplanner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryMessageDto {
    private UUID id;
    private String role;
    private String content;
    private Long createdAt;
}
