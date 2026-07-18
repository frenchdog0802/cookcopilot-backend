package com.lardermind.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "usage_quotas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    private String period_plan = "monthly"; // monthly, daily

    @Column(name = "period_start")
    private Long periodStart;

    @Column(name = "period_end")
    private Long periodEnd;

    @Column(name = "recipes_created")
    @Builder.Default
    private Integer recipesCreated = 0;

    @Column(name = "ai_message_sent")
    @Builder.Default
    private Integer aiMessageSent = 0;

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
