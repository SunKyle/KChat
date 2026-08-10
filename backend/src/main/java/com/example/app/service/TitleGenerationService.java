package com.example.app.service;

import com.example.app.service.ai.AiServiceFactory;
import com.example.app.service.ai.TitleGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 标题生成服务
 *
 * LLM 调用与 prompt 模板由 LangChain4j {@link AiServiceFactory} +
 * {@link TitleGenerator} 统一处理，用 {@code @UserMessage} 模板替代原先手写的
 * prompt 拼装。模型路由由 {@link AiServiceFactory} 内部完成。
 * 文本截断与返回值清洗保持自实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TitleGenerationService {

    private final AiServiceFactory aiServiceFactory;

    public String generateTitle(String userMessage, String aiResponse, String model) {
        String truncatedUser = userMessage.length() > 200 ? userMessage.substring(0, 200) : userMessage;
        String truncatedAi = aiResponse.length() > 200 ? aiResponse.substring(0, 200) : aiResponse;

        TitleGenerator generator = aiServiceFactory.create(TitleGenerator.class, model);
        String raw = generator.generate(truncatedUser, truncatedAi);
        return cleanTitle(raw);
    }

    private String cleanTitle(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String title = raw.trim()
                .replaceAll("^[\"'\"《》\\[\\]\\s]+|[\"'\"《》\\[\\]\\s]+$", "")
                .replaceAll("^标题[：:]\\s*", "")
                .replaceAll("[\n\r]", " ");
        if (title.length() > 50) {
            title = title.substring(0, 50);
        }
        return title;
    }
}
