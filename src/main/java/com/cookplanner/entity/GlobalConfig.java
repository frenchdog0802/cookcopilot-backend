package com.cookplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "global_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "app_name", nullable = false)
    private String appName;

    @Column(name = "default_locale", nullable = false)
    @Builder.Default
    private String defaultLocale = "en-US";

    @Column(name = "default_time_zone", nullable = false)
    @Builder.Default
    private String defaultTimeZone = "UTC";

    @Column(name = "default_measurement_system", nullable = false)
    @Builder.Default
    private String defaultMeasurementSystem = "metric"; // metric, imperial

    @Column(name = "maintenance_mode")
    @Builder.Default
    private String maintenanceMode = "off"; // on, off

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @PrePersist
    protected void onCreate() {
        long now = System.currentTimeMillis() / 1000;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = System.currentTimeMillis() / 1000;
    }
}
