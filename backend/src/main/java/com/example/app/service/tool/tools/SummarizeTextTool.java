package com.example.app.service.tool.tools;

import com.example.app.service.ai.AiServiceFactory;
import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文本摘要工具
 *
 * 使用 LLM 对长文本进行摘要，支持控制摘要长度和风格。
 * 当用户提供了长文本或从 parseFile/fetchUrl 获取了长内容时，
 * 可用此工具生成精炼摘要。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizeTextTool implements ToolComponent {

    private final AiServiceFactory aiServiceFactory;

    private static final int MAX_INPUT_LENGTH = 30000;
    private static final int DEFAULT_MAX_LENGTH = 500;

    @Tool("""
            对长文本进行摘要总结。输入待摘要的文本，返回精炼的摘要内容。
            可指定摘要的最大长度（默认 500 字）。
            适用于：总结长文章、提炼要点、生成内容摘要等场景。
            """)
    public String summarizeText(
            String text,
            Integer maxLength) {

        if (text == null || text.isBlank()) {
            return "错误：文本内容不能为空";
        }

        if (text.length() > MAX_INPUT_LENGTH) {
            return "错误：文本过长（最多 " + MAX_INPUT_LENGTH + " 字符，当前 " + text.length() + " 字符）";
        }

        int length = (maxLength != null && maxLength > 0) ? maxLength : DEFAULT_MAX_LENGTH;

        try {
            ChatModel model = aiServiceFactory.getChatModel(null);

            String prompt = """
                    请对以下文本进行摘要，摘要不超过 %d 字。
                    要求：
                    1. 提炼核心观点和关键信息
                    2. 保持原文的语言风格
                    3. 如果是列表内容，保留重要条目
                    4. 直接输出摘要，无需额外说明

                    --- 待摘要文本 ---
                    %s
                    --- 摘要开始 ---
                    """.formatted(length, text);

            log.info("[SummarizeText] Summarizing {} chars, maxLength={}", text.length(), length);
            String summary = model.chat(prompt);

            if (summary == null || summary.isBlank()) {
                return "摘要生成失败，请重试";
            }

            log.info("[SummarizeText] Summary generated: {} chars", summary.length());
            return "【摘要】（" + summary.length() + "字）\n" + summary;

        } catch (Exception e) {
            log.error("[SummarizeText] Summarization failed: {}", e.getMessage());
            return "摘要生成失败：" + e.getMessage();
        }
    }
}