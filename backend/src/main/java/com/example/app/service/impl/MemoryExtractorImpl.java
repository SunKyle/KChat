package com.example.app.service.impl;

import com.example.app.config.MemoryExtractorConfig;
import com.example.app.dto.MemoryDTO;
import com.example.app.service.LongTermMemoryService;
import com.example.app.service.MemoryExtractor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆提取服务实现类，用于从对话中提取重要信息并保存为长期记忆
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryExtractorImpl implements MemoryExtractor {

    /**
     * 聊天语言模型，用于AI对话处理
     */
    private final ChatLanguageModel chatLanguageModel;
    /**
     * 长期记忆服务，用于保存和管理记忆
     */
    private final LongTermMemoryService longTermMemoryService;
    /**
     * 对象映射器，用于JSON处理
     */
    private final ObjectMapper objectMapper;
    /**
     * 记忆提取配置，包含提取规则和阈值设置
     */
    private final MemoryExtractorConfig config;

    /**
     * 记忆提取的提示词模板，定义了提取规则和输出格式
     */
    private static final String EXTRACTION_PROMPT = """
            你是一个记忆提取专家。请从以下对话中提取值得长期记忆的重要信息。

            规则：
            1. 只提取事实性信息，不要保存对话内容本身
            2. 提取用户的身份、技能、偏好、项目、任务等
            3. 忽略问候语、闲聊、一次性问题
            4. 每条记忆保持简洁（不超过50字）
            5. 为每条记忆标注类型：PROFILE/PREFERENCE/PROJECT/SKILL/TASK/KNOWLEDGE/RELATION/EVENT
            6. 为每条记忆评估重要性（1-10分，越高越重要）
            7. 为每条记忆评估置信度（0.0-1.0）

            对话：
            {conversation}

            请输出JSON格式：
            {
              "memories": [
                {
                  "content": "用户使用Java开发",
                  "type": "PROFILE",
                  "importance": 8,
                  "confidence": 0.95
                }
              ]
            }
            """;

    /**
     * 从消息列表中提取记忆
     *
     * @param messages 聊天消息列表
     * @return 提取的记忆结果列表
     */
    @Override
    public List<MemoryExtractionResult> extract(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        String conversation = formatConversation(messages);

        try {
            String prompt = EXTRACTION_PROMPT.replace("{conversation}", conversation);
            log.info("[Memory Extract] Sending extraction request to LLM");
            String response = chatLanguageModel.generate(prompt);
            log.info("[Memory Extract] LLM response received: {}",
                    response.length() > 100 ? response.substring(0, 100) + "..." : response);
            return parseExtractionResult(response);
        } catch (Exception e) {
            log.warn("[Memory Extract] LLM extraction failed, falling back to rule-based extraction: {}",
                    e.getMessage());
            return extractFallback(messages);
        }
    }

    /**
     * 提取并保存记忆
     *
     * @param conversationId 对话ID
     * @param messages       聊天消息列表
     * @param userId         用户ID
     * @return 保存的记忆数量
     */
    @Override
    public int extractAndSave(String conversationId, List<ChatMessage> messages, String userId) {
        log.info("[Memory Extract] Starting memory extraction - conversation: {}, user: {}, message count: {}",
                conversationId, userId, messages.size());

        List<MemoryExtractionResult> results = extract(messages);
        log.info("[Memory Extract] Extracted {} potential memories", results.size());

        if (!results.isEmpty()) {
            for (MemoryExtractionResult r : results) {
                log.info("[Memory Extract] - content: '{}', type: {}, importance: {}, confidence: {}",
                        r.content(), r.type(), r.importance(), r.confidence());
            }
        }

        if (results.isEmpty()) {
            return 0;
        }

        List<MemoryDTO> existingMemories = longTermMemoryService.findByUserId(userId);
        Set<String> existingContents = existingMemories.stream()
                .map(MemoryDTO::getContent)
                .collect(Collectors.toSet());
        log.info("[Memory Extract] Found {} existing memories for deduplication", existingContents.size());

        double confidenceThreshold = config.getMinConfidence() / 100.0;
        int importanceThreshold = config.getMinImportance();

        List<MemoryDTO> toSave = new ArrayList<>();
        for (MemoryExtractionResult result : results) {
            if (result.confidence() < confidenceThreshold) {
                log.info("[Memory Extract] Skipping low confidence memory ({} < {}): '{}'",
                        result.confidence(), confidenceThreshold, result.content());
                continue;
            }

            if (result.importance() < importanceThreshold) {
                log.info("[Memory Extract] Skipping low importance memory ({} < {}): '{}'",
                        result.importance(), importanceThreshold, result.content());
                continue;
            }

            String normalizedContent = normalizeContent(result.content());
            if (existingContents.contains(normalizedContent)) {
                log.info("[Memory Extract] Skipping duplicate memory: '{}'", normalizedContent);
                continue;
            }

            MemoryDTO dto = MemoryDTO.builder()
                    .userId(userId)
                    .content(normalizedContent)
                    .type(result.type())
                    .importance(result.importance())
                    .build();
            toSave.add(dto);
            existingContents.add(normalizedContent);
        }

        if (!toSave.isEmpty()) {
            log.info("[Memory Extract] Saving {} new memories", toSave.size());
            longTermMemoryService.saveAll(toSave);
        } else {
            log.info("[Memory Extract] No new memories to save");
        }

        return toSave.size();
    }

    /**
     * 格式化对话内容
     *
     * @param messages 聊天消息列表
     * @return 格式化后的对话文本
     */
    private String formatConversation(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            String role = message instanceof UserMessage ? "用户" : message instanceof AiMessage ? "AI" : "系统";
            sb.append(role).append(": ").append(message.text()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析提取结果
     *
     * @param response LLM返回的响应
     * @return 解析后的记忆结果列表
     */
    private List<MemoryExtractionResult> parseExtractionResult(String response) {
        try {
            String jsonContent = extractJson(response);
            log.info("[Memory Extract] Extracted JSON: {}",
                    jsonContent.length() > 50 ? jsonContent.substring(0, 50) + "..." : jsonContent);

            Map<String, Object> result = objectMapper.readValue(jsonContent,
                    new TypeReference<Map<String, Object>>() {
                    });

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> memories = (List<Map<String, Object>>) result.get("memories");

            if (memories == null || memories.isEmpty()) {
                return Collections.emptyList();
            }

            return memories.stream()
                    .map(this::parseMemoryItem)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            log.warn("[Memory Extract] Failed to parse extraction result: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从响应中提取JSON内容
     *
     * @param response LLM返回的响应
     * @return JSON字符串
     */
    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");

        if (start == -1 || end == -1 || start > end) {
            return response;
        }

        return response.substring(start, end + 1);
    }

    /**
     * 解析单个记忆项
     *
     * @param item 记忆项的Map表示
     * @return 记忆结果对象
     */
    private MemoryExtractionResult parseMemoryItem(Map<String, Object> item) {
        try {
            String content = (String) item.get("content");
            String type = (String) item.get("type");
            int importance = item.get("importance") instanceof Number
                    ? ((Number) item.get("importance")).intValue()
                    : 5;
            double confidence = item.get("confidence") instanceof Number
                    ? ((Number) item.get("confidence")).doubleValue()
                    : 0.5;

            if (content == null || content.trim().isEmpty() || type == null) {
                log.warn("[Memory Extract] Skipping memory item with empty content");
                return null;
            }

            return new MemoryExtractionResult(content.trim(), type.toUpperCase(), importance, confidence);
        } catch (Exception e) {
            log.warn("[Memory Extract] Failed to parse memory item: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 备用提取方法，当LLM提取失败时使用规则提取
     *
     * @param messages 聊天消息列表
     * @return 提取的记忆结果列表
     */
    private List<MemoryExtractionResult> extractFallback(List<ChatMessage> messages) {
        List<MemoryExtractionResult> results = new ArrayList<>();

        for (ChatMessage message : messages) {
            if (!(message instanceof UserMessage)) {
                continue;
            }

            String text = message.text();
            List<MemoryExtractionResult> extracted = extractFromText(text);
            results.addAll(extracted);
        }

        return results;
    }

    /**
     * 从文本中提取记忆
     *
     * @param text 输入文本
     * @return 提取的记忆结果列表
     */
    private List<MemoryExtractionResult> extractFromText(String text) {
        List<MemoryExtractionResult> results = new ArrayList<>();

        if (text.contains("使用") && (text.contains("开发") || text.contains("编程"))) {
            int idx = text.indexOf("使用");
            String skill = text.substring(idx + 2).trim();
            if (skill.contains("开发")) {
                skill = skill.substring(0, skill.indexOf("开发")).trim();
            } else if (skill.contains("编程")) {
                skill = skill.substring(0, skill.indexOf("编程")).trim();
            }
            if (!skill.isEmpty()) {
                results.add(new MemoryExtractionResult("用户使用" + skill + "开发", "SKILL", 8, 0.8));
            }
        }

        if (text.contains("我是") || text.contains("我叫")) {
            String profile = text.substring(0, Math.min(text.length(), 30));
            results.add(new MemoryExtractionResult(profile, "PROFILE", 9, 0.9));
        }

        if (text.contains("项目") || text.contains("开发") || text.contains("做")) {
            String project = text.substring(0, Math.min(text.length(), 50));
            results.add(new MemoryExtractionResult(project, "PROJECT", 7, 0.7));
        }

        return results;
    }

    /**
     * 规范化记忆内容
     *
     * @param content 原始内容
     * @return 规范化后的内容
     */
    private String normalizeContent(String content) {
        return content.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}