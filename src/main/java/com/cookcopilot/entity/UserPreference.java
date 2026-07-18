package com.cookcopilot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_preferences", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_preferences_user_id", columnNames = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_preference_allergies", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "item")
    @Builder.Default
    private List<String> allergies = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_preference_dislikes", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "item")
    @Builder.Default
    private List<String> dislikes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_preference_likes", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "item")
    @Builder.Default
    private List<String> likes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_preference_dietary_restrictions", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "item")
    @Builder.Default
    private List<String> dietaryRestrictions = new ArrayList<>();

    @Column(name = "household_notes", columnDefinition = "TEXT")
    private String householdNotes;

    @Column(name = "measurement_unit")
    private String measurementUnit;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @PrePersist
    protected void onCreate() {
        long now = System.currentTimeMillis() / 1000;
        this.createdAt = now;
        this.updatedAt = now;
        if (this.measurementUnit == null || this.measurementUnit.isBlank()) {
            this.measurementUnit = "metric";
        }
        if (this.allergies == null) this.allergies = new ArrayList<>();
        if (this.dislikes == null) this.dislikes = new ArrayList<>();
        if (this.likes == null) this.likes = new ArrayList<>();
        if (this.dietaryRestrictions == null) this.dietaryRestrictions = new ArrayList<>();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = System.currentTimeMillis() / 1000;
    }
}
