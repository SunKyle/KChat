package com.example.app.repository;

import com.example.app.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
    List<ModelConfig> findByEnabledTrue();
    boolean existsByNameAndModelId(String name, String modelId);
    boolean existsByNameAndModelIdAndIdNot(String name, String modelId, Long id);
    List<ModelConfig> findByType(ModelConfig.ModelType type);
    List<ModelConfig> findByTypeAndEnabledTrue(ModelConfig.ModelType type);
    List<ModelConfig> findByEnabledTrueOrderByType();
}