package com.lardermind.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceDto {
    private UUID id;
    private List<String> allergies;
    private List<String> dislikes;
    private List<String> likes;
    private List<String> dietaryRestrictions;
    private String householdNotes;
    private String measurementUnit;
    private String notes;
}
