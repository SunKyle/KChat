package com.example.app.service;

import com.example.app.dto.MemoryDTO;
import com.example.app.service.impl.MemoryRecallerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryRecallerTest {

    @Mock
    private LongTermMemoryService longTermMemoryService;

    @InjectMocks
    private MemoryRecallerImpl memoryRecaller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("召回记忆 - 成功")
    void recall_Success() {
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

        when(longTermMemoryService.recall("user123", "Java开发", 5))
                .thenReturn(Arrays.asList(memory1, memory2));

        List<MemoryDTO> result = memoryRecaller.recall("user123", "Java开发", 5);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("用户使用Java开发", result.get(0).getContent());
    }

    @Test
    @DisplayName("召回记忆 - 无结果")
    void recall_NoResults() {
        when(longTermMemoryService.recall("user123", "不存在的查询", 5))
                .thenReturn(Collections.emptyList());

        List<MemoryDTO> result = memoryRecaller.recall("user123", "不存在的查询", 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("召回记忆 - 带类型过滤")
    void recall_WithTypeFilter() {
        MemoryDTO memory = MemoryDTO.builder()
                .id(1L)
                .userId("user123")
                .content("用户使用Java开发")
                .type("SKILL")
                .importance(8)
                .build();

        when(longTermMemoryService.recall("user123", "Java", 5, Arrays.asList("SKILL")))
                .thenReturn(Collections.singletonList(memory));

        List<MemoryDTO> result = memoryRecaller.recall("user123", "Java", 5, Arrays.asList("SKILL"));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SKILL", result.get(0).getType());
    }

    @Test
    @DisplayName("获取高优先级记忆")
    void getHighPriorityMemories_Success() {
        MemoryDTO memory = MemoryDTO.builder()
                .id(1L)
                .userId("user123")
                .content("用户是高级工程师")
                .type("PROFILE")
                .importance(9)
                .build();

        when(longTermMemoryService.findByUserIdAndMinImportance("user123", 7))
                .thenReturn(Collections.singletonList(memory));

        List<MemoryDTO> result = memoryRecaller.getHighPriorityMemories("user123");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getImportance() >= 7);
    }
}