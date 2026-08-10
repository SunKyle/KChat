package com.example.app.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 文本优化 AI 接口
 *
 * 通过 LangChain4j {@link dev.langchain4j.service.AiServices} 代理调用 LLM，
 * 用 {@code @SystemMessage} 模板替代原先手写的 {@code buildSystemPrompt}。
 *
 * 调用方根据 {@code optimizationType} 拼装 {@code instruction}（"语法纠错" / "语义优化" 等），
 * 失败时降级到 {@code ContentOptimizationServiceImpl.fallbackOptimization} 的字符串清洗。
 */
public interface ContentOptimizer {

    @SystemMessage("""
            你是文本优化助手。请根据以下要求优化文本：{{instruction}}
            保持原意，只输出优化后的文本，不解释，不说明。
            """)
    String optimize(@V("instruction") String instruction, @UserMessage String content);
}
