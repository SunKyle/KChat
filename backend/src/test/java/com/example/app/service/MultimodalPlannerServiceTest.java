package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.MultimodalProperties;
import com.example.app.dto.MultimodalPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class MultimodalPlannerServiceTest {

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private OpenAICompatibleClient openAICompatibleClient;

    @Mock
    private OllamaClient ollamaClient;

    private MultimodalPlannerService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        MultimodalProperties properties = new MultimodalProperties();
        properties.setMaxSteps(5);
        service = new MultimodalPlannerService(
                properties, modelConfigService, openAICompatibleClient, ollamaClient, new ObjectMapper());
        when(ollamaClient.generate(anyList(), isNull()))
                .thenThrow(new RuntimeException("ollama down"));
    }

    @Test
    void fallsBackToImageGenerationPlanWhenLlmFails() {
        MultimodalPlan plan = service.plan("生成一张猫的图片", List.of());

        assertNotNull(plan);
        assertTrue(plan.steps().stream().anyMatch(step -> "image_gen".equals(step.type())));
    }

    @Test
    void fallsBackToVisionPlanWhenImageUploaded() {
        MultimodalPlan plan = service.plan("分析这张图", List.of("/api/images/cat.png"));

        assertNotNull(plan);
        assertTrue(plan.steps().stream().anyMatch(step -> "vision".equals(step.type())));
    }
}
