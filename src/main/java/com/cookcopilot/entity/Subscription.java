package com.cookcopilot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false)
    private String status; // trialing, active, past_due, canceled, incomplete

    @Column(name = "start_at")
    private Long startAt;

    @Column(name = "current_period_start")
    private Long currentPeriodStart;

    @Column(name = "current_period_end")
    private Long currentPeriodEnd;

    @Column(name = "cancel_at")
    private Long cancelAt;

    @Builder.Default
    private String provider = "stripe";

    @Column(name = "provider_customer_id")
    private String providerCustomerId;

    @Column(name = "provider_subscription_id")
    private String providerSubscriptionId;

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
