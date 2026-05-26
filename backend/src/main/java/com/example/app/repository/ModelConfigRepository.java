package com.example.app.repository;

import com.example.app.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
    List<ModelConfig> findByEnabledTrue();
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}