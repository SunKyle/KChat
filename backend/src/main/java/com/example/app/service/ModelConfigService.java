package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.config.ModelCapability;
import com.example.app.dto.ModelConfigDTO;
import com.example.app.dto.ModelCapabilityInfo;
import com.example.app.entity.ModelConfig;
import com.example.app.repository.ModelConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

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
                .capabilities(serializeCapabilities(dto.getCapabilities()))
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

    /**
     * 返回所有模型及其输入/输出能力，供前端按能力筛选。
     */
    @Transactional(readOnly = true)
    public List<ModelCapabilityInfo> listModelsWithCapabilities() {
        return listModels(null).stream()
                .map(model -> new ModelCapabilityInfo(model, getCapabilities(model)))
                .toList();
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
        if (dto.getCapabilities() != null) {
            config.setCapabilities(serializeCapabilities(dto.getCapabilities()));
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
                .filter(config -> modelId.equals(config.getName() + ":" + config.getModelId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 返回模型能力集合；Auto 模式返回全部多模态能力。
     */
    public Set<String> getCapabilities(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Collections.emptySet();
        }
        ModelConfig config = getConfigByModelId(modelId);
        if (config != null) {
            return resolveCapabilities(config);
        }
        return inferOllamaCapabilities(modelId);
    }

    /**
     * 查找第一个具备指定能力的已启用模型配置，未配置时返回 null。
     */
    public ModelConfig findFirstModelWithCapability(String capability) {
        return modelConfigRepository.findByEnabledTrue().stream()
                .filter(config -> resolveCapabilities(config).contains(capability))
                .findFirst()
                .orElse(null);
    }

    /**
     * 返回第一个可用的文本模型标识（name:modelId），没有自定义配置时返回 null。
     */
    public String findDefaultTextModelId() {
        return modelConfigRepository.findByEnabledTrue().stream()
                .filter(config -> config.getCategory() == null
                        || config.getCategory() == ModelConfig.ModelCategory.TEXT)
                .map(config -> config.getName() + ":" + config.getModelId())
                .findFirst()
                .orElse(null);
    }

    private Set<String> resolveCapabilities(ModelConfig config) {
        Set<String> capabilities = parseCapabilities(config.getCapabilities());
        if (config.getCategory() != null) {
            switch (config.getCategory()) {
                case TEXT -> {
                    capabilities.add(ModelCapability.TEXT_IN);
                    capabilities.add(ModelCapability.TEXT_OUT);
                }
                case IMAGE -> capabilities.add(ModelCapability.IMAGE_OUT);
                case VIDEO -> {
                    capabilities.add(ModelCapability.VIDEO_IN);
                    capabilities.add(ModelCapability.VIDEO_OUT);
                }
            }
        }
        return capabilities;
    }

    private Set<String> inferOllamaCapabilities(String modelId) {
        String lower = modelId.toLowerCase();
        Set<String> capabilities = new HashSet<>();
        capabilities.add(ModelCapability.TEXT_IN);
        capabilities.add(ModelCapability.TEXT_OUT);
        if (lower.contains("llava")
                || lower.contains("vision")
                || lower.contains("-vl")
                || lower.contains("minicpm")
                || lower.contains("qwen2.5-vl")) {
            capabilities.add(ModelCapability.IMAGE_IN);
        }
        return capabilities;
    }

    private Set<String> parseCapabilities(String capabilities) {
        if (capabilities == null || capabilities.isBlank()) {
            return new HashSet<>();
        }
        try {
            Set<String> parsed = new HashSet<>(objectMapper.readValue(capabilities, new TypeReference<List<String>>() {
            }));
            Set<String> normalized = new HashSet<>();
            for (String capability : parsed) {
                normalized.add(normalizeCapability(capability));
            }
            return normalized;
        } catch (Exception e) {
            log.warn("Failed to parse model capabilities: {}", e.getMessage());
            return new HashSet<>();
        }
    }

    private String normalizeCapability(String capability) {
        if (capability == null) {
            return "";
        }
        return switch (capability) {
            case ModelCapability.LEGACY_VISION -> ModelCapability.IMAGE_IN;
            case ModelCapability.LEGACY_IMAGE_GEN -> ModelCapability.IMAGE_OUT;
            case ModelCapability.LEGACY_TTS -> ModelCapability.AUDIO_OUT;
            case ModelCapability.LEGACY_VIDEO -> ModelCapability.VIDEO_OUT;
            default -> capability;
        };
    }

    private String serializeCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(capabilities);
        } catch (Exception e) {
            log.warn("Failed to serialize model capabilities: {}", e.getMessage());
            return null;
        }
    }
}
