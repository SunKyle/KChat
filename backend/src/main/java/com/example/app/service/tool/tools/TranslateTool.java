package com.example.app.service.tool.tools;

import com.example.app.service.ai.AiServiceFactory;
import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文本翻译工具
 *
 * 使用 LLM 对文本进行多语言翻译，支持自动检测源语言和指定目标语言。
 * 当用户需要将文本翻译成其他语言时调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranslateTool implements ToolComponent {

    private final AiServiceFactory aiServiceFactory;

    private static final int MAX_INPUT_LENGTH = 50000;

    @Tool("""
            将文本翻译成指定语言。输入待翻译的文本和目标语言名称（如"英语"、"日语"、"法语"、"德语"、"韩语"等），
            返回翻译后的文本。支持自动检测源语言。
            如果未指定目标语言，默认翻译为中文。
            适用于：翻译外文内容、多语言交流、理解外语文档等场景。
            """)
    public String translateText(
            String text,
            String targetLanguage) {
        if (text == null || text.isBlank()) {
            return "错误：待翻译文本不能为空";
        }

        if (text.length() > MAX_INPUT_LENGTH) {
            return "错误：文本过长（最多 " + MAX_INPUT_LENGTH + " 字符，当前 " + text.length() + " 字符）";
        }

        String lang = (targetLanguage != null && !targetLanguage.isBlank()) ? targetLanguage : "中文";

        try {
            ChatModel model = aiServiceFactory.getChatModel(null);

            String prompt = """
                    你是一个专业的翻译助手。请将以下文本翻译成%s。
                    要求：
                    1. 准确传达原文含义，保持专业术语的正确性
                    2. 符合目标语言的表达习惯，自然流畅
                    3. 保留原文的格式（如换行、列表等）
                    4. 直接输出翻译结果，无需额外说明
                    5. 不要添加任何解释、注释或元信息

                    --- 待翻译文本 ---
                    %s
                    --- 翻译结果 ---
                    """.formatted(lang, text);

            log.info("[TranslateTool] Translating {} chars to {}", text.length(), lang);
            String result = model.chat(prompt);

            if (result == null || result.isBlank()) {
                return "翻译失败，请重试";
            }

            log.info("[TranslateTool] Translation result: {} chars", result.length());
            return result;

        } catch (Exception e) {
            log.error("[TranslateTool] Translation failed: {}", e.getMessage());
            return "翻译失败：" + e.getMessage();
        }
    }
}