package com.example.app.service;

import com.example.app.dto.ModelConfigDTO;
import com.example.app.entity.ModelConfig;
import com.example.app.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;

    @Transactional(readOnly = true)
    public List<ModelConfig> getAllEnabledConfigs() {
        return modelConfigRepository.findByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public List<ModelConfig> getAllConfigs() {
        return modelConfigRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ModelConfig getConfigById(Long id) {
        return modelConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型配置不存在: " + id));
    }

    @Transactional
    public ModelConfig createConfig(ModelConfigDTO dto) {
        if (dto.getApiKey() == null || dto.getApiKey().isEmpty()) {
            throw new IllegalArgumentException("API Key不能为空");
        }

        if (modelConfigRepository.existsByNameAndModelId(dto.getName(), dto.getModelId())) {
            throw new IllegalArgumentException("名称和模型ID组合已存在");
        }

        ModelConfig config = ModelConfig.builder()
                .name(dto.getName())
                .modelId(dto.getModelId())
                .baseUrl(dto.getBaseUrl())
                .apiKey(dto.getApiKey())
                .type(ModelConfig.ModelType.OPENAI_COMPATIBLE)
                .enabled(dto.getEnabled() != null ? dto.getEnabled() : true)
                .build();

        return modelConfigRepository.save(config);
    }

    @Transactional
    public ModelConfig updateConfig(Long id, ModelConfigDTO dto) {
        ModelConfig config = getConfigById(id);

        if (modelConfigRepository.existsByNameAndModelIdAndIdNot(dto.getName(), dto.getModelId(), id)) {
            throw new IllegalArgumentException("名称和模型ID组合已存在");
        }

        config.setName(dto.getName());
        config.setModelId(dto.getModelId());
        config.setBaseUrl(dto.getBaseUrl());
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            config.setApiKey(dto.getApiKey());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }

        return modelConfigRepository.save(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        if (!modelConfigRepository.existsById(id)) {
            throw new IllegalArgumentException("模型配置不存在: " + id);
        }
        modelConfigRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ModelConfig getConfigByModelId(String modelId) {
        return modelConfigRepository.findByEnabledTrue().stream()
                .filter(config -> modelId.startsWith(config.getName() + ":"))
                .findFirst()
                .orElse(null);
    }
}