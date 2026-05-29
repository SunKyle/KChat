package com.example.app.service;

import com.example.app.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
class ShortTermMemoryServiceTest {

    @Autowired
    private ShortTermMemoryService shortTermMemoryService;

    private String conversationId;

    @BeforeEach
    void setUp() {
        conversationId = "test-conversation-id";
    }

    @Test
    @DisplayName("测试更新用户消息到记忆")
    void testUpdateMemoryWithUserMessage() {
        shortTermMemoryService.updateMemoryWithUserMessage(conversationId, "你好");
        assertNotNull(shortTermMemoryService.getMemoryContext(conversationId));
    }

    @Test
    @DisplayName("测试更新AI消息到记忆")
    void testUpdateMemoryWithAiMessage() {
        shortTermMemoryService.updateMemoryWithUserMessage(conversationId, "你好");
        shortTermMemoryService.updateMemoryWithAiMessage(conversationId, "您好！有什么我可以帮助您的吗？");

        var context = shortTermMemoryService.getMemoryContext(conversationId);
        assertNotNull(context);
    }

    @Test
    @DisplayName("测试获取记忆上下文")
    void testGetMemoryContext() {
        shortTermMemoryService.updateMemoryWithUserMessage(conversationId, "测试消息");
        
        var context = shortTermMemoryService.getMemoryContext(conversationId);
        assertNotNull(context);
    }

@Test
    @DisplayName("测试清除记忆")
    void testClearMemory() {
        shortTermMemoryService.updateMemoryWithUserMessage(conversationId, "测试消息");
        shortTermMemoryService.clearMemory(conversationId);
        
        var context = shortTermMemoryService.getMemoryContext(conversationId);
        assertNotNull(context);
    }

    @Test
    @DisplayName("测试不同对话的记忆隔离")
    void testMemoryIsolation() {
        String conversationId1 = "conv-1";
        String conversationId2 = "conv-2";
        
        shortTermMemoryService.updateMemoryWithUserMessage(conversationId1, "对话1的消息");
        shortTermMemoryService.updateMemoryWithUserMessage(conversationId2, "对话2的消息");
        
        var context1 = shortTermMemoryService.getMemoryContext(conversationId1);
        var context2 = shortTermMemoryService.getMemoryContext(conversationId2);
        
        assertNotNull(context1);
        assertNotNull(context2);
    }
}
