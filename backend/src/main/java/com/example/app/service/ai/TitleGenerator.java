package com.example.app.service.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 标题生成 AI 接口
 *
 * 通过 LangChain4j {@link dev.langchain4j.service.AiServices} 代理调用 LLM，
 * 用 {@code @UserMessage} 模板替代原先手写的 prompt 拼装。
 *
 * 调用方负责对过长的 user/ai 文本做截断，并对返回值做 {@code cleanTitle} 清洗。
 */
public interface TitleGenerator {

    @UserMessage("""
            根据以下对话内容，生成一个简短的标题（3-15个字）。直接输出标题，不要加引号、编号或其他修饰。

            用户：{{user}}
            AI：{{ai}}
            """)
    String generate(@V("user") String userMessage, @V("ai") String aiResponse);
}
