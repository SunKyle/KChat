package com.example.app.service;

import com.example.app.dto.ContentOptimizationRequest;
import com.example.app.dto.ContentOptimizationResponse;

/**
 * 内容优化服务接口
 */
public interface ContentOptimizationService {

    /**
     * 优化文本内容
     * 
     * @param request 优化请求
     * @return 优化响应
     */
    ContentOptimizationResponse optimizeContent(ContentOptimizationRequest request);
}