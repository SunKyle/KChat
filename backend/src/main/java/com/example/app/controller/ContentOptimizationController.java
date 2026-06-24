package com.example.app.controller;

import com.example.app.aspect.RateLimitAspect.RateLimited;
import com.example.app.dto.ContentOptimizationRequest;
import com.example.app.dto.ContentOptimizationResponse;
import com.example.app.service.ContentOptimizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 内容优化 API 控制器
 * 
 * <功能说明>
 * - 提供文本内容优化的 REST API
 * - 支持语法纠错、语义优化、格式规范化、关键词提取
 * - 集成请求频率限制机制
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class ContentOptimizationController {

    private final ContentOptimizationService contentOptimizationService;

    /**
     * 优化文本内容
     * 
     * @param request 优化请求
     * @param httpRequest HTTP 请求对象（用于限流）
     * @return 优化结果
     */
    @PostMapping("/optimize")
    @RateLimited
    public ResponseEntity<ContentOptimizationResponse> optimizeContent(
            @Valid @RequestBody ContentOptimizationRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("收到内容优化请求，内容长度: {} 字符", request.getContent().length());
        
        ContentOptimizationResponse response = contentOptimizationService.optimizeContent(request);
        
        if (response.isSuccess()) {
            log.info("内容优化成功，处理耗时: {}ms", response.getProcessingTimeMs());
            return ResponseEntity.ok(response);
        } else {
            log.warn("内容优化失败: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}