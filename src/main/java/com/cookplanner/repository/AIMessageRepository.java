package com.cookplanner.repository;

import com.cookplanner.entity.AIMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AIMessageRepository extends JpaRepository<AIMessage, UUID> {
    List<AIMessage> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
