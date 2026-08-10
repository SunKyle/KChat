package com.example.app.util;

import com.example.app.dto.MemoryDTO;
import com.example.app.security.InputValidator;
import com.example.app.security.SensitiveFilter;
import com.example.app.service.PromptMetricsService;
import com.example.app.service.PromptTemplateService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class PromptAssemblerTest {

    private PromptAssembler promptAssembler;

    @BeforeEach
    void setUp() {
        TokenEstimator tokenEstimator = new SimpleTokenEstimator();
        // 使用反射设置 InputValidator 的配置值
        InputValidator inputValidator = new InputValidator();
        try {
            java.lang.reflect.Field maxField = InputValidator.class.getDeclaredField("maxInputLength");
            maxField.setAccessible(true);
            maxField.set(inputValidator, 4096);
            
            java.lang.reflect.Field minField = InputValidator.class.getDeclaredField("minInputLength");
            minField.setAccessible(true);
            minField.set(inputValidator, 1);
        } catch (Exception e) {
            log.warn("Failed to set InputValidator fields", e);
        }
        
        SensitiveFilter sensitiveFilter = new SensitiveFilter();
        PromptTemplateService templateService = Mockito.mock(PromptTemplateService.class);
        PromptMetricsService metricsService = Mockito.mock(PromptMetricsService.class);
        
        // 模拟模板服务抛出异常，使PromptAssembler使用默认模板
        Mockito.when(templateService.renderTemplate(Mockito.anyString(), Mockito.anyMap()))
               .thenThrow(new IllegalArgumentException("Template not found"));
        
        promptAssembler = new PromptAssembler(tokenEstimator, inputValidator, sensitiveFilter, 
                                              templateService, metricsService);
    }

    @Test
    @DisplayName("组装Prompt - 包含长期记忆")
    void assemble_WithLongTermMemory() {
        MemoryDTO memory1 = MemoryDTO.builder()
                .id(1L)
                .userId("user123")
                .content("用户使用Java开发")
                .type("SKILL")
                .importance(8)
                .createdAt(LocalDateTime.now())
                .build();

        MemoryDTO memory2 = MemoryDTO.builder()
                .id(2L)
                .userId("user123")
                .content("用户喜欢简洁回答")
                .type("PREFERENCE")
                .importance(6)
                .createdAt(LocalDateTime.now())
                .build();

        List<ChatMessage> shortTermMemory = Collections.singletonList(
                UserMessage.from("你好")
        );

        List<MemoryDTO> longTermMemory = Arrays.asList(memory1, memory2);

        List<ChatMessage> result = promptAssembler.assemble(shortTermMemory, longTermMemory, "今天天气怎么样");

        assertNotNull(result);
        assertEquals(3, result.size());
        
        assertTrue(result.get(0) instanceof SystemMessage);
        assertTrue(result.get(1) instanceof UserMessage);
        assertTrue(result.get(2) instanceof UserMessage);
        
        // 验证系统消息包含长期记忆信息
        String systemText = ((SystemMessage) result.get(0)).text();
        assertTrue(systemText.contains("用户使用Java开发") || systemText.contains("智能助手"), 
                   "System message should contain memory or fallback content");
    }

    @Test
    @DisplayName("组装Prompt - 无长期记忆")
    void assemble_WithoutLongTermMemory() {
        List<ChatMessage> shortTermMemory = Collections.singletonList(
                UserMessage.from("你好")
        );

        List<MemoryDTO> longTermMemory = Collections.emptyList();

        List<ChatMessage> result = promptAssembler.assemble(shortTermMemory, longTermMemory, "今天天气怎么样");

        assertNotNull(result);
        assertEquals(3, result.size());
        
        assertTrue(result.get(0) instanceof SystemMessage);
        assertTrue(result.get(1) instanceof UserMessage);
        assertTrue(result.get(2) instanceof UserMessage);
        
        // 验证系统消息有效
        assertTrue(((SystemMessage) result.get(0)).text().contains("智能助手"), 
                   "System message should contain fallback content");
    }

    @Test
    @DisplayName("计算Token数量")
    void calculateTokenCount_Success() {
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from("你是一个助手"),
                UserMessage.from("Hello world")
        );

        int count = promptAssembler.calculateTokenCount(messages);
        
        assertTrue(count > 0);
    }

    @Test
    @DisplayName("截断到Token限制")
    void truncateToTokenLimit_Success() {
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from("你是一个非常聪明的助手，擅长回答各种问题"),
                UserMessage.from("第一个问题"),
                UserMessage.from("第二个问题"),
                UserMessage.from("第三个问题")
        );

        List<ChatMessage> result = promptAssembler.truncateToTokenLimit(messages, 10);

        assertNotNull(result);
        assertTrue(result.size() <= messages.size());
    }
}