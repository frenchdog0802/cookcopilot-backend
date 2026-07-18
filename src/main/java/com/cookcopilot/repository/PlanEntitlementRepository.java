package com.cookcopilot.repository;

import com.cookcopilot.entity.PlanEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, UUID> {
    List<PlanEntitlement> findByPlanId(UUID planId);
}
