package com.cookplanner.repository;

import com.cookplanner.entity.GlobalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GlobalConfigRepository extends JpaRepository<GlobalConfig, UUID> {
}
