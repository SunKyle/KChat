package com.example.app.controller;

import com.example.app.TestConfig;
import com.example.app.dto.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("测试创建对话接口")
    void testCreateConversation() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"测试对话\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("测试对话"));
    }

    @Test
    @DisplayName("测试获取对话列表接口")
    void testGetConversations() throws Exception {
        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("测试获取对话详情接口")
    void testGetConversation() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"测试对话\"}"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = createResult.getResponse().getContentAsString();
        String conversationId = objectMapper.readTree(responseBody).get("id").asText();
        
        mockMvc.perform(get("/api/conversations/{id}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId));
    }

    @Test
    @DisplayName("测试删除对话接口")
    void testDeleteConversation() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"测试对话\"}"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = createResult.getResponse().getContentAsString();
        String conversationId = objectMapper.readTree(responseBody).get("id").asText();
        
        mockMvc.perform(delete("/api/conversations/{id}", conversationId))
                .andExpect(status().isNoContent());
        
        mockMvc.perform(get("/api/conversations/{id}", conversationId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("测试同步消息发送接口")
    void testSendMessage() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setMessage("你好");
        
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").isNotEmpty())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.role").value("assistant"));
    }

    @Test
    @DisplayName("测试流式消息发送接口")
    void testStreamMessage() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setMessage("你好");
        
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
}