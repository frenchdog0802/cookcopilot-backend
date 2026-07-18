package com.lardermind.repository;

import com.lardermind.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByUserId(UUID userId);

    Optional<Folder> findByUserIdAndNameIgnoreCase(UUID userId, String name);
}
