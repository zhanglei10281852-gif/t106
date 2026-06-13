package com.company.material.repository;

import com.company.material.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKey(String configKey);
    boolean existsByConfigKey(String configKey);
}
