package com.example.app.service;

import com.example.app.config.MultimodalProperties;
import com.example.app.dto.MultimodalPlan;
import com.example.app.dto.MultimodalPlanStep;
import com.example.app.service.ai.AiServiceFactory;
import com.example.app.service.ai.MultimodalPlannerAI;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultimodalPlannerServiceTest {

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private AiServiceFactory aiServiceFactory;

    @Mock
    private MultimodalPlannerAI plannerAI;

    private MultimodalPlannerService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        MultimodalProperties properties = new MultimodalProperties();
        properties.setMaxSteps(5);
        service = new MultimodalPlannerService(properties, modelConfigService, aiServiceFactory);
        // 默认 LLM 调用失败，触发 fallback 规划
        when(aiServiceFactory.create(eq(MultimodalPlannerAI.class), any()))
                .thenThrow(new RuntimeException("llm down"));
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
        MultimodalPlan expectedPlan = new MultimodalPlan(List.of(
                new MultimodalPlanStep("text", null, "回答当前问题", null)));
        // 覆盖默认 stub：让 AiServiceFactory 返回可用的 planner
        when(aiServiceFactory.create(eq(MultimodalPlannerAI.class), any())).thenReturn(plannerAI);
        when(plannerAI.plan(anyString())).thenReturn(expectedPlan);

        MultimodalPlan plan = service.plan("当前问题", List.of(), null, history);

        assertNotNull(plan);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(plannerAI).plan(captor.capture());
        String prompt = captor.getValue();
        assertTrue(prompt.contains("上一轮问题"));
        assertTrue(prompt.contains("上一轮回答"));
    }
}
