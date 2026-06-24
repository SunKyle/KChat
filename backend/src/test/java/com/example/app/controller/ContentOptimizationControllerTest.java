package com.example.app.controller;

import com.example.app.dto.ContentOptimizationRequest;
import com.example.app.dto.ContentOptimizationResponse;
import com.example.app.service.ContentOptimizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 内容优化控制器集成测试
 */
@WebMvcTest(ContentOptimizationController.class)
class ContentOptimizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContentOptimizationService contentOptimizationService;

    @Test
    void testOptimizeContent_Success() throws Exception {
        ContentOptimizationResponse mockResponse = ContentOptimizationResponse.success(
                "优化后的内容",
                "原始内容",
                List.of(
                        ContentOptimizationResponse.OptimizationDetail.builder()
                                .type("grammar")
                                .description("语法错误修正")
                                .build()
                ),
                120L
        );

        when(contentOptimizationService.optimizeContent(any())).thenReturn(mockResponse);

        ContentOptimizationRequest request = ContentOptimizationRequest.builder()
                .content("这是一段需要优化的文本内容。")
                .userId("test-user")
                .build();

        mockMvc.perform(post("/api/chat/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.optimizedContent").value("优化后的内容"))
                .andExpect(jsonPath("$.originalContent").value("原始内容"))
                .andExpect(jsonPath("$.processingTimeMs").value(120))
                .andExpect(jsonPath("$.optimizations[0].type").value("grammar"))
                .andExpect(jsonPath("$.optimizations[0].description").value("语法错误修正"));
    }

    @Test
    void testOptimizeContent_Failure() throws Exception {
        ContentOptimizationResponse mockResponse = ContentOptimizationResponse.failure(
                "OPTIMIZATION_FAILED",
                "内部错误"
        );

        when(contentOptimizationService.optimizeContent(any())).thenReturn(mockResponse);

        ContentOptimizationRequest request = ContentOptimizationRequest.builder()
                .content("测试内容")
                .build();

        mockMvc.perform(post("/api/chat/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("OPTIMIZATION_FAILED"))
                .andExpect(jsonPath("$.message").value("内部错误"));
    }

    @Test
    void testOptimizeContent_EmptyContent_ShouldReturnBadRequest() throws Exception {
        ContentOptimizationRequest request = ContentOptimizationRequest.builder()
                .content("")
                .build();

        mockMvc.perform(post("/api/chat/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testOptimizeContent_NullContent_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testOptimizeContent_RateLimitExceeded() throws Exception {
        ContentOptimizationResponse mockResponse = ContentOptimizationResponse.rateLimitExceeded(60);

        when(contentOptimizationService.optimizeContent(any())).thenReturn(mockResponse);

        ContentOptimizationRequest request = ContentOptimizationRequest.builder()
                .content("测试内容")
                .build();

        mockMvc.perform(post("/api/chat/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));
    }
}