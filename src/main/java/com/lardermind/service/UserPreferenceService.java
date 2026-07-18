package com.lardermind.service;

import com.lardermind.entity.UserPreference;
import com.lardermind.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;

    @Transactional
    public UserPreference getOrCreate(UUID userId) {
        UserPreference prefs = userPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> userPreferenceRepository.save(UserPreference.builder()
                        .userId(userId)
                        .allergies(new ArrayList<>())
                        .dislikes(new ArrayList<>())
                        .likes(new ArrayList<>())
                        .dietaryRestrictions(new ArrayList<>())
                        .measurementUnit("metric")
                        .householdNotes("")
                        .notes("")
                        .build()));
        return detachCollections(prefs);
    }

    @Transactional
    public UserPreference update(UUID userId, PreferenceUpdate update) {
        UserPreference prefs = getOrCreate(userId);

        if (update.allergies() != null) {
            prefs.setAllergies(normalizeList(update.allergies()));
        }
        if (update.dislikes() != null) {
            prefs.setDislikes(normalizeList(update.dislikes()));
        }
        if (update.likes() != null) {
            prefs.setLikes(normalizeList(update.likes()));
        }
        if (update.dietaryRestrictions() != null) {
            prefs.setDietaryRestrictions(normalizeList(update.dietaryRestrictions()));
        }
        if (update.householdNotes() != null) {
            prefs.setHouseholdNotes(update.householdNotes().trim());
        }
        if (update.measurementUnit() != null && !update.measurementUnit().isBlank()) {
            prefs.setMeasurementUnit(normalizeUnit(update.measurementUnit()));
        }
        if (update.notes() != null) {
            prefs.setNotes(update.notes().trim());
        }

        return detachCollections(userPreferenceRepository.save(prefs));
    }

    /**
     * Copy ElementCollection lists into plain ArrayLists while the persistence
     * context is still open, so AI tool threads / controllers can read them safely.
     */
    private static UserPreference detachCollections(UserPreference prefs) {
        prefs.setAllergies(new ArrayList<>(safeList(prefs.getAllergies())));
        prefs.setDislikes(new ArrayList<>(safeList(prefs.getDislikes())));
        prefs.setLikes(new ArrayList<>(safeList(prefs.getLikes())));
        prefs.setDietaryRestrictions(new ArrayList<>(safeList(prefs.getDietaryRestrictions())));
        return prefs;
    }

    public Map<String, Object> toMap(UserPreference prefs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", prefs.getId() != null ? prefs.getId().toString() : null);
        map.put("allergies", safeList(prefs.getAllergies()));
        map.put("dislikes", safeList(prefs.getDislikes()));
        map.put("likes", safeList(prefs.getLikes()));
        map.put("dietaryRestrictions", safeList(prefs.getDietaryRestrictions()));
        map.put("householdNotes", prefs.getHouseholdNotes() != null ? prefs.getHouseholdNotes() : "");
        map.put("measurementUnit", prefs.getMeasurementUnit() != null ? prefs.getMeasurementUnit() : "metric");
        map.put("notes", prefs.getNotes() != null ? prefs.getNotes() : "");
        return map;
    }

    public record PreferenceUpdate(
            List<String> allergies,
            List<String> dislikes,
            List<String> likes,
            List<String> dietaryRestrictions,
            String householdNotes,
            String measurementUnit,
            String notes
    ) {
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .map(v -> v.replaceAll("\\s+", " "))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String normalizeUnit(String unit) {
        String normalized = unit.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("imp")) {
            return "imperial";
        }
        return "metric";
    }
}
