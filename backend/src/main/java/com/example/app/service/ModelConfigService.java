package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.dto.ModelConfigDTO;
import com.example.app.entity.ModelConfig;
import com.example.app.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;
    private final OllamaClient ollamaClient;

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

        ModelConfig.ModelType type = parseModelType(dto.getType());
        ModelConfig.ModelCategory category = parseModelCategory(dto.getCategory());

        ModelConfig config = ModelConfig.builder()
                .name(dto.getName())
                .modelId(dto.getModelId())
                .baseUrl(dto.getBaseUrl())
                .apiKey(dto.getApiKey())
                .type(type)
                .category(category)
                .enabled(dto.getEnabled() != null ? dto.getEnabled() : true)
                .build();

        return modelConfigRepository.save(config);
    }

    @Transactional(readOnly = true)
    public List<ModelConfig> getConfigsByType(ModelConfig.ModelType type) {
        return modelConfigRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<ModelConfig> getEnabledConfigsByType(ModelConfig.ModelType type) {
        return modelConfigRepository.findByTypeAndEnabledTrue(type);
    }

    @Transactional(readOnly = true)
    public List<ModelConfig> getAllEnabledConfigsGrouped() {
        return modelConfigRepository.findByEnabledTrueOrderByType();
    }

    @Transactional(readOnly = true)
    public List<ModelConfig> getConfigsByCategory(ModelConfig.ModelCategory category) {
        return modelConfigRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<ModelConfig.ModelType> getAllTypes() {
        return java.util.Arrays.asList(ModelConfig.ModelType.values());
    }

    @Transactional(readOnly = true)
    public List<ModelConfig.ModelCategory> getAllCategories() {
        return java.util.Arrays.asList(ModelConfig.ModelCategory.values());
    }

    public List<String> listModels() {
        return listModels(null);
    }

    public List<String> listModels(ModelConfig.ModelCategory category) {
        List<String> models = new ArrayList<>();

        if (category == null || category == ModelConfig.ModelCategory.TEXT) {
            List<String> ollamaModels = ollamaClient.listModels();
            models.addAll(ollamaModels);
        }

        List<ModelConfig> enabledConfigs;
        if (category != null) {
            enabledConfigs = modelConfigRepository.findByCategoryAndEnabledTrue(category);
        } else {
            enabledConfigs = modelConfigRepository.findByEnabledTrue();
        }

        for (ModelConfig config : enabledConfigs) {
            models.add(config.getName() + ":" + config.getModelId());
        }

        return models;
    }

    private ModelConfig.ModelType parseModelType(String type) {
        if (type == null || type.isEmpty()) {
            return ModelConfig.ModelType.OPENAI;
        }
        // 向后兼容：OPENAI_COMPATIBLE 映射为 OPENAI
        if ("OPENAI_COMPATIBLE".equalsIgnoreCase(type)) {
            return ModelConfig.ModelType.OPENAI;
        }
        try {
            return ModelConfig.ModelType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ModelConfig.ModelType.CUSTOM;
        }
    }

    private ModelConfig.ModelCategory parseModelCategory(String category) {
        if (category == null || category.isEmpty()) {
            return ModelConfig.ModelCategory.TEXT;
        }
        try {
            return ModelConfig.ModelCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ModelConfig.ModelCategory.TEXT;
        }
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
        if (dto.getType() != null && !dto.getType().isEmpty()) {
            config.setType(parseModelType(dto.getType()));
        }
        if (dto.getCategory() != null && !dto.getCategory().isEmpty()) {
            config.setCategory(parseModelCategory(dto.getCategory()));
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