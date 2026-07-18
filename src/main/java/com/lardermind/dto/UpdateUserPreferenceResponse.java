package com.lardermind.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPreferenceResponse {
    private UserPreferenceDto preferences;
}
