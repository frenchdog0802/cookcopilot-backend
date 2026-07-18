package com.lardermind.repository;

import com.lardermind.entity.AIMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIMessageRepository extends JpaRepository<AIMessage, UUID> {
    List<AIMessage> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<AIMessage> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AIMessage> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    void deleteByUserId(UUID userId);
}
