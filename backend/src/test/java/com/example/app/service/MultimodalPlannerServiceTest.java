package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.MultimodalProperties;
import com.example.app.dto.MultimodalPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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

    @Test
    void includesConversationHistoryInPlannerPrompt() {
        List<ChatMessage> history = List.of(
                UserMessage.from("上一轮问题"),
                AiMessage.from("上一轮回答"));
        String planJson = """
                {"steps":[{"type":"text","prompt":null,"text":"回答当前问题","targetImage":null}]}
                """.trim();
        reset(ollamaClient);
        when(ollamaClient.generate(anyList(), nullable(String.class))).thenReturn(planJson);

        MultimodalPlan plan = service.plan("当前问题", List.of(), null, history);

        assertNotNull(plan);
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(ollamaClient).generate(captor.capture(), nullable(String.class));
        String prompt = ((UserMessage) captor.getValue().get(0)).text();
        assertTrue(prompt.contains("上一轮问题"));
        assertTrue(prompt.contains("上一轮回答"));
    }
}
