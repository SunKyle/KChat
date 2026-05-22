package com.example.app.service;

import com.example.app.TestConfig;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("测试同步消息生成 - 新建对话")
    void testGenerateResponse_NewConversation() {
        ChatRequest request = new ChatRequest();
        request.setMessage("你好");
        
        ChatResponse response = chatService.generateResponse(request);
        
        assertNotNull(response);
        assertNotNull(response.getMessageId());
        assertNotNull(response.getContent());
        assertEquals("assistant", response.getRole());
    }

    @Test
    @DisplayName("测试同步消息生成 - 已有对话")
    void testGenerateResponse_ExistingConversation() {
        ChatRequest request1 = new ChatRequest();
        request1.setMessage("第一次消息");
        
        ChatResponse response1 = chatService.generateResponse(request1);
        assertNotNull(response1);
        
        ChatRequest request2 = new ChatRequest();
        request2.setConversationId(response1.getConversationId());
        request2.setMessage("第二次消息");
        
        ChatResponse response2 = chatService.generateResponse(request2);
        
        assertNotNull(response2);
        assertNotNull(response2.getMessageId());
        assertEquals(response1.getConversationId(), response2.getConversationId());
    }

    @Test
    @DisplayName("测试空消息验证")
    void testGenerateResponse_EmptyMessage() {
        ChatRequest request = new ChatRequest();
        request.setMessage("");
        
        assertThrows(Exception.class, () -> {
            chatService.generateResponse(request);
        });
    }
}