package com.example.app.util;

import com.example.app.dto.MemoryDTO;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptAssemblerTest {

    private PromptAssembler promptAssembler;

    @BeforeEach
    void setUp() {
        TokenEstimator tokenEstimator = new SimpleTokenEstimator();
        promptAssembler = new PromptAssembler(tokenEstimator);
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
        
        assertTrue(result.get(0).text().contains("用户使用Java开发"));
        assertTrue(result.get(0).text().contains("用户喜欢简洁回答"));
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
        
        assertTrue(result.get(0).text().contains("无"));
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