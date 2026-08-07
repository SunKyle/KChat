package com.example.app.service;

import com.example.app.dto.MultimodalConfigDTO;
import com.example.app.entity.MultimodalConfig;
import com.example.app.repository.MultimodalConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultimodalConfigService {

    private final MultimodalConfigRepository repository;

    @Transactional
    public MultimodalConfigDTO getByUserId(String userId) {
        MultimodalConfig config = repository.findByUserId(userId)
                .orElseGet(() -> createDefault(userId));
        return toDTO(config);
    }

    @Transactional
    public MultimodalConfigDTO update(String userId, MultimodalConfigDTO dto) {
        MultimodalConfig config = repository.findByUserId(userId)
                .orElseGet(() -> createDefault(userId));

        if (dto.getPlannerModel() != null) {
            config.setPlannerModel(blankToNull(dto.getPlannerModel()));
        }
        if (dto.getVisionModel() != null) {
            config.setVisionModel(blankToNull(dto.getVisionModel()));
        }
        if (dto.getImageModel() != null) {
            config.setImageModel(blankToNull(dto.getImageModel()));
        }
        if (dto.getTextModel() != null) {
            config.setTextModel(blankToNull(dto.getTextModel()));
        }
        if (dto.getMaxSteps() != null && dto.getMaxSteps() > 0) {
            config.setMaxSteps(dto.getMaxSteps());
        }

        repository.save(config);
        log.info("Updated multimodal config for user: {}", userId);
        return toDTO(config);
    }

    private MultimodalConfig createDefault(String userId) {
        MultimodalConfig config = MultimodalConfig.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .maxSteps(5)
                .build();
        return repository.save(config);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MultimodalConfigDTO toDTO(MultimodalConfig config) {
        return MultimodalConfigDTO.builder()
                .userId(config.getUserId())
                .plannerModel(config.getPlannerModel())
                .visionModel(config.getVisionModel())
                .imageModel(config.getImageModel())
                .textModel(config.getTextModel())
                .maxSteps(config.getMaxSteps())
                .build();
    }
}
