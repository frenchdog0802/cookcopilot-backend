package com.lardermind.repository;

import com.lardermind.entity.InventoryAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAuditLogRepository extends JpaRepository<InventoryAuditLog, UUID> {
    List<InventoryAuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
