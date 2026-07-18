package com.cookcopilot.controller;

import com.cookcopilot.common.ApiResponse;
import com.cookcopilot.dto.GetUserPreferenceResponse;
import com.cookcopilot.dto.UpdateUserPreferenceRequest;
import com.cookcopilot.dto.UpdateUserPreferenceResponse;
import com.cookcopilot.dto.UserPreferenceDto;
import com.cookcopilot.entity.UserPreference;
import com.cookcopilot.service.UserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user-preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public ApiResponse<GetUserPreferenceResponse> get(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        UserPreference prefs = userPreferenceService.getOrCreate(userId);
        return ApiResponse.success(new GetUserPreferenceResponse(toDto(prefs)));
    }

    @PutMapping
    public ApiResponse<UpdateUserPreferenceResponse> update(
            Authentication auth,
            @Valid @RequestBody UpdateUserPreferenceRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        UserPreference prefs = userPreferenceService.update(userId, new UserPreferenceService.PreferenceUpdate(
                request.getAllergies(),
                request.getDislikes(),
                request.getLikes(),
                request.getDietaryRestrictions(),
                request.getHouseholdNotes(),
                request.getMeasurementUnit(),
                request.getNotes()
        ));
        return ApiResponse.success(new UpdateUserPreferenceResponse(toDto(prefs)));
    }

    private UserPreferenceDto toDto(UserPreference prefs) {
        return UserPreferenceDto.builder()
                .id(prefs.getId())
                .allergies(copy(prefs.getAllergies()))
                .dislikes(copy(prefs.getDislikes()))
                .likes(copy(prefs.getLikes()))
                .dietaryRestrictions(copy(prefs.getDietaryRestrictions()))
                .householdNotes(prefs.getHouseholdNotes() != null ? prefs.getHouseholdNotes() : "")
                .measurementUnit(prefs.getMeasurementUnit() != null ? prefs.getMeasurementUnit() : "metric")
                .notes(prefs.getNotes() != null ? prefs.getNotes() : "")
                .build();
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
