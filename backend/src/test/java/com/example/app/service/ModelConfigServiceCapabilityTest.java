package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.config.ModelCapability;
import com.example.app.entity.ModelConfig;
import com.example.app.repository.ModelConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ModelConfigServiceCapabilityTest {

    @Mock
    private ModelConfigRepository repository;

    @Mock
    private OllamaClient ollamaClient;

    private ModelConfigService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ModelConfigService(repository, ollamaClient, new ObjectMapper());
    }

    @Test
    void legacyCapabilitiesAreNormalizedToInputOutputDimensions() {
        ModelConfig config = ModelConfig.builder()
                .name("legacy")
                .modelId("vision-model")
                .category(ModelConfig.ModelCategory.TEXT)
                .capabilities("[\"VISION\",\"IMAGE_GEN\",\"TTS\"]")
                .build();
        when(repository.findByEnabledTrue()).thenReturn(List.of(config));

        Set<String> capabilities = service.getCapabilities("legacy:vision-model");

        assertTrue(capabilities.contains(ModelCapability.IMAGE_IN));
        assertTrue(capabilities.contains(ModelCapability.IMAGE_OUT));
        assertTrue(capabilities.contains(ModelCapability.AUDIO_OUT));
        assertTrue(capabilities.contains(ModelCapability.TEXT_IN));
        assertTrue(capabilities.contains(ModelCapability.TEXT_OUT));
    }

    @Test
    void imageCategoryDefaultsToImageOutput() {
        ModelConfig config = ModelConfig.builder()
                .name("sd")
                .modelId("stable-diffusion")
                .category(ModelConfig.ModelCategory.IMAGE)
                .build();
        when(repository.findByEnabledTrue()).thenReturn(List.of(config));

        ModelConfig found = service.findFirstModelWithCapability(ModelCapability.IMAGE_OUT);

        assertNotNull(found);
        assertEquals("sd", found.getName());
    }

    @Test
    void modelListDoesNotContainAuto() {
        when(repository.findByEnabledTrue()).thenReturn(List.of());

        List<String> models = service.listModels(null);

        assertFalse(models.contains("auto"));
    }
}
