package com.lardermind.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetUserPreferenceResponse {
    private UserPreferenceDto preferences;
}
