package com.example.app.pipeline.context;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 请求元数据，从 ChatRequest 透传的基础信息。
 */
@Data
@Builder(toBuilder = true)
public class RequestMetadata {
    private String conversationId;
    private String userId;
    private String userMessage;
    private String model;
    private List<String> imageUrls;

    /**
     * 用户显式引用的知识库 ID 列表，由 ChatRequest.knowledgeBaseIds 透传。
     * 非空时用于指定库片段召回 + 禁用 Agent 记忆兜底检索；
     * 为空时触发 Agent 自动兜底（main_dataset）。
     */
    private List<String> knowledgeBaseIds;

    /**
     * 引用的知识库名称列表（KnowledgeBaseRetrievalStage 填充），
     * 持久化为 Message.kbReferences，供前端展示"引用来源"标签。
     */
    private List<String> kbReferenceNames;

    /**
     * 用户语言偏好，从 user_profile 读取。
     */
    private String language;

    /**
     * 是否启用 Web 搜索（来自请求参数 webSearch）。
     */
    private boolean webSearchEnabled;
}
