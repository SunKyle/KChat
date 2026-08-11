package com.example.app.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Query 分析 AI 接口
 *
 * <p>通过 LangChain4j {@link dev.langchain4j.service.AiServices} 代理调用 LLM，
 * 对用户 query 进行意图分类和改写，产出结构化的 {@link QueryAnalysisResult}。
 *
 * <p>当规则匹配置信度不足时，由 {@code QueryAnalyzer} 调用此接口做深度分析。
 */
public interface QueryAnalysisAI {

    @SystemMessage("""
            你是一个 Query 分析专家。请分析用户的输入，判断其意图类型，并输出 JSON 结构。

            意图类型说明：
            - KNOWLEDGE_QUERY: 知识询问，用户想了解某个概念、技术、事实等
            - PROFILE_QUERY: 用户档案查询，用户询问自己的昵称、偏好等个人信息
            - TASK_EXECUTION: 任务执行，用户要求总结、翻译、处理、生成等操作
            - CONTEXT_DEPENDENT: 上下文依赖，用户使用代词（这个、那个、刚才等）指代之前的内容
            - CHAT_SMALLTALK: 闲聊/问候，如打招呼、寒暄
            - MATH_CALCULATION: 简单数学计算
            - GENERAL: 其他通用查询

            输出严格 JSON 格式，不要包含任何解释性文字：
            {
              "intentType": "KNOWLEDGE_QUERY",
              "keywords": {"Java": 1.0, "编程语言": 0.8},
              "rewrittenQuery": "Java 编程语言",
              "requiresMemory": true,
              "reasoning": "用户询问 Java 是什么，属于知识询问"
            }
            """)
    QueryAnalysisResultDTO analyze(@UserMessage String userQuery);

    /**
     * Query 分析结果 DTO（与前端 DTO 分离，专为 LLM 输出设计）
     */
    record QueryAnalysisResultDTO(
            String intentType,
            java.util.Map<String, Double> keywords,
            String rewrittenQuery,
            boolean requiresMemory,
            String reasoning
    ) {
    }
}