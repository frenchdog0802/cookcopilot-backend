package com.cookcopilot.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetUserPreferenceResponse {
    private UserPreferenceDto preferences;
}
