package com.example.app.service;

import com.example.app.dto.ContentOptimizationRequest;
import com.example.app.dto.ContentOptimizationResponse;
import com.example.app.service.impl.ContentOptimizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内容优化服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class ContentOptimizationServiceTest {

    @Mock
    private ContentOptimizationService contentOptimizationService;

    @Test
    void testOptimizeContent_EmptyContent_ShouldFail() {
        ContentOptimizationRequest request = ContentOptimizationRequest.builder()
                .content("")
                .build();

        assertThrows(Exception.class, () -> {
            contentOptimizationService.optimizeContent(request);
        });
    }

    @Test
    void testOptimizeContent_NullContent_ShouldFail() {
        ContentOptimizationRequest request = ContentOptimizationRequest.builder()
                .content(null)
                .build();

        assertThrows(Exception.class, () -> {
            contentOptimizationService.optimizeContent(request);
        });
    }

    @Test
    void testOptimizationResponse_Success() {
        ContentOptimizationResponse response = ContentOptimizationResponse.success(
                "优化后的内容",
                "原始内容",
                java.util.List.of(),
                100L
        );

        assertTrue(response.isSuccess());
        assertEquals("优化后的内容", response.getOptimizedContent());
        assertEquals("原始内容", response.getOriginalContent());
        assertEquals(100L, response.getProcessingTimeMs());
        assertNull(response.getError());
        assertNull(response.getMessage());
    }

    @Test
    void testOptimizationResponse_Failure() {
        ContentOptimizationResponse response = ContentOptimizationResponse.failure(
                "OPTIMIZATION_FAILED",
                "优化失败"
        );

        assertFalse(response.isSuccess());
        assertEquals("OPTIMIZATION_FAILED", response.getError());
        assertEquals("优化失败", response.getMessage());
        assertNull(response.getOptimizedContent());
    }

    @Test
    void testOptimizationResponse_RateLimitExceeded() {
        ContentOptimizationResponse response = ContentOptimizationResponse.rateLimitExceeded(60);

        assertFalse(response.isSuccess());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getError());
        assertEquals("请求过于频繁，请稍后重试", response.getMessage());
        assertEquals(60, response.getRetryAfterSeconds());
    }

    @Test
    void testOptimizationDetail() {
        ContentOptimizationResponse.OptimizationDetail detail = ContentOptimizationResponse.OptimizationDetail.builder()
                .type("grammar")
                .description("语法错误修正")
                .build();

        assertEquals("grammar", detail.getType());
        assertEquals("语法错误修正", detail.getDescription());
    }

    @Test
    void testRequestWithUserId() {
        ContentOptimizationRequest request = ContentOptimizationRequest.builder()
                .content("测试内容")
                .userId("user123")
                .build();

        assertEquals("测试内容", request.getContent());
        assertEquals("user123", request.getUserId());
    }

    @Test
    void testRequestWithOptimizationType() {
        ContentOptimizationRequest request = ContentOptimizationRequest.builder()
                .content("测试内容")
                .optimizationType("grammar")
                .build();

        assertEquals("grammar", request.getOptimizationType());
    }
}