package com.lardermind.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPreferenceRequest {
    private List<String> allergies;
    private List<String> dislikes;
    private List<String> likes;
    private List<String> dietaryRestrictions;
    private String householdNotes;
    private String measurementUnit;
    private String notes;
}
