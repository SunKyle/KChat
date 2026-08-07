package com.example.app.repository;

import com.example.app.entity.MultimodalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MultimodalConfigRepository extends JpaRepository<MultimodalConfig, String> {

    Optional<MultimodalConfig> findByUserId(String userId);
}
