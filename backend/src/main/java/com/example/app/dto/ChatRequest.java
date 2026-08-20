package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String conversationId;

    private String message;

    private String model;

    private List<String> imageUrls;
    
    private String userId;

    private boolean webSearch = false;

    private boolean agentMode = false;

    /**
     * 用户显式引用的知识库 ID 列表（前端 @ 唤起选择后随请求透传）。
     * 非空 → 注入指定库片段并禁用 Agent 记忆兜底检索；
     * 为空 → 保留 Agent 记忆检索工具做 main_dataset 自动兜底。
     */
    private List<String> knowledgeBaseIds;

    /**
     * 手动激活的 Skill ID（前端在技能中心选择后随请求透传）。
     * 非空 → SkillResolutionStage 直接激活该 Skill，覆盖默认 system prompt
     * 和工具白名单；为空 → 走关键词匹配或默认通用模式。
     */
    private String skillId;
}
