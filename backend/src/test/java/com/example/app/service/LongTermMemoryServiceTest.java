package com.example.app.service;

import com.example.app.dto.MemoryDTO;
import com.example.app.entity.LongTermMemory;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.repository.LongTermMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LongTermMemoryServiceTest {

    @Mock
    private LongTermMemoryRepository repository;

    @Mock
    private VectorStoreWrapper vectorStoreWrapper;

    @InjectMocks
    private LongTermMemoryService memoryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("保存记忆 - 成功")
    void saveMemory_Success() {
        MemoryDTO dto = MemoryDTO.builder()
                .userId("user123")
                .content("用户使用Java开发")
                .type("SKILL")
                .importance(8)
                .build();

        LongTermMemory entity = LongTermMemory.builder()
                .id(1L)
                .userId("user123")
                .content("用户使用Java开发")
                .type(MemoryType.SKILL)
                .importance(8)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(LongTermMemory.class))).thenReturn(entity);

        MemoryDTO result = memoryService.save(dto);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("user123", result.getUserId());
        assertEquals("SKILL", result.getType());
        verify(repository, times(1)).save(any(LongTermMemory.class));
        verify(vectorStoreWrapper, times(1)).add(eq("user123"), eq("用户使用Java开发"), eq(1L));
    }

    @Test
    @DisplayName("根据用户ID查询记忆")
    void findByUserId_Success() {
        LongTermMemory memory1 = LongTermMemory.builder()
                .id(1L)
                .userId("user123")
                .content("记忆1")
                .type(MemoryType.PROFILE)
                .importance(8)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        LongTermMemory memory2 = LongTermMemory.builder()
                .id(2L)
                .userId("user123")
                .content("记忆2")
                .type(MemoryType.SKILL)
                .importance(7)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.findByUserIdOrderByCreatedAtDesc("user123"))
                .thenReturn(Arrays.asList(memory1, memory2));

        List<MemoryDTO> result = memoryService.findByUserId("user123");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("记忆1", result.get(0).getContent());
        assertEquals("记忆2", result.get(1).getContent());
    }

    @Test
    @DisplayName("根据ID查询记忆 - 存在")
    void findById_Exists() {
        LongTermMemory memory = LongTermMemory.builder()
                .id(1L)
                .userId("user123")
                .content("测试记忆")
                .type(MemoryType.KNOWLEDGE)
                .importance(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(memory));

        Optional<MemoryDTO> result = memoryService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("测试记忆", result.get().getContent());
    }

    @Test
    @DisplayName("根据ID查询记忆 - 不存在")
    void findById_NotExists() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        Optional<MemoryDTO> result = memoryService.findById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("删除记忆 - 成功")
    void deleteById_Success() {
        LongTermMemory memory = LongTermMemory.builder()
                .id(1L)
                .userId("user123")
                .content("测试记忆")
                .type(MemoryType.KNOWLEDGE)
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(memory));
        doNothing().when(repository).deleteById(1L);

        memoryService.deleteById(1L);

        verify(repository, times(1)).findById(1L);
        verify(repository, times(1)).deleteById(1L);
        verify(vectorStoreWrapper, times(1)).remove("user123", 1L);
    }

    @Test
    @DisplayName("清理过期记忆")
    void cleanExpired_Success() {
        when(repository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(5);

        int result = memoryService.cleanExpired();

        assertEquals(5, result);
        verify(repository, times(1)).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}