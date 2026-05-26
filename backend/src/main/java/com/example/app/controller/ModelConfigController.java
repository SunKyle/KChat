package com.example.app.controller;

import com.example.app.dto.ModelConfigDTO;
import com.example.app.entity.ModelConfig;
import com.example.app.service.ModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/model-configs")
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