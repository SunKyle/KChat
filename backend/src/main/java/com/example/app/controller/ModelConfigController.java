package com.example.app.controller;

import com.example.app.dto.ModelConfigDTO;
import com.example.app.entity.ModelConfig;
import com.example.app.service.ModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping({ "/api/model-configs", "/api/models/configs" })
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping
    public ResponseEntity<List<ModelConfig>> getAllConfigs() {
        return ResponseEntity.ok(modelConfigService.getAllConfigs());
    }

    @GetMapping("/enabled")
    public ResponseEntity<List<ModelConfig>> getEnabledConfigs() {
        return ResponseEntity.ok(modelConfigService.getAllEnabledConfigs());
    }

    @GetMapping("/types")
    public ResponseEntity<List<String>> getAllTypes() {
        List<String> types = Arrays.stream(ModelConfig.ModelType.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(types);
    }

    @GetMapping("/by-type/{type}")
    public ResponseEntity<List<ModelConfig>> getConfigsByType(@PathVariable String type) {
        try {
            ModelConfig.ModelType modelType = ModelConfig.ModelType.valueOf(type.toUpperCase());
            return ResponseEntity.ok(modelConfigService.getConfigsByType(modelType));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAllCategories() {
        List<String> categories = Arrays.stream(ModelConfig.ModelCategory.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<List<ModelConfig>> getConfigsByCategory(@PathVariable String category) {
        try {
            ModelConfig.ModelCategory modelCategory = ModelConfig.ModelCategory.valueOf(category.toUpperCase());
            return ResponseEntity.ok(modelConfigService.getConfigsByCategory(modelCategory));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModelConfig> getConfigById(@PathVariable Long id) {
        return ResponseEntity.ok(modelConfigService.getConfigById(id));
    }

    @PostMapping
    public ResponseEntity<ModelConfig> createConfig(@Valid @RequestBody ModelConfigDTO dto) {
        return ResponseEntity.ok(modelConfigService.createConfig(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelConfig> updateConfig(@PathVariable Long id, @Valid @RequestBody ModelConfigDTO dto) {
        return ResponseEntity.ok(modelConfigService.updateConfig(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        modelConfigService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }
}