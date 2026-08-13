package com.example.app.service.impl;

import com.example.app.config.MemoryExtractorConfig;
import com.example.app.dto.MemoryDTO;
import com.example.app.service.LongTermMemoryService;
import com.example.app.service.MemoryExtractor;
import com.example.app.service.ai.AiServiceFactory;
import com.example.app.service.ai.MemoryExtractionAI;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆提取服务实现类，用于从对话中提取重要信息并保存为长期记忆
 *
 * LLM 调用与结构化输出由 LangChain4j {@link AiServiceFactory} +
 * {@link MemoryExtractionAI} 统一处理，框架自动注入 JSON Schema 并反序列化为
 * {@link MemoryExtractionAI.MemoryExtractionResult}，替代原先手写的 JSON 解析。
 * 业务流程（上下文窗口、去重、阈值过滤、降级规则提取）保持自实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryExtractorImpl implements MemoryExtractor {

    /**
     * AiServices 工厂，按 modelId 动态构建 LLM 代理
     */
    private final AiServiceFactory aiServiceFactory;
    /**
     * 长期记忆服务，用于保存和管理记忆
     */
    private final LongTermMemoryService longTermMemoryService;
    /**
     * 记忆提取配置，包含提取规则和阈值设置
     */
    private final MemoryExtractorConfig config;

    /**
     * 从消息列表中提取记忆（应用上下文窗口限制）
     *
     * @param messages 聊天消息列表
     * @return 提取的记忆结果列表
     */
    @Override
    public List<MemoryExtractionResult> extract(List<ChatMessage> messages) {
        return extract(messages, null);
    }

    public List<MemoryExtractionResult> extract(List<ChatMessage> messages, String model) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatMessage> windowedMessages = applyContextWindow(messages);
        String conversation = formatConversation(windowedMessages);

        try {
            log.info("[记忆提取] 发送提取请求到LLM (窗口大小: {})", windowedMessages.size());
            MemoryExtractionAI extractor = aiServiceFactory.create(MemoryExtractionAI.class, model);
            MemoryExtractionAI.MemoryExtractionResult result = extractor.extract(conversation);
            log.info("[记忆提取] LLM响应接收, summary: {}",
                    result.summary() != null && result.summary().length() > 50
                            ? result.summary().substring(0, 50) + "..."
                            : result.summary());
            return convertResult(result);
        } catch (Exception e) {
            log.warn("[记忆提取] LLM提取失败，降级到规则提取: {}", e.getMessage());
            return extractFallback(windowedMessages);
        }
    }

    /**
     * 将 LangChain4j 反序列化后的 {@link MemoryExtractionAI.MemoryExtractionResult}
     * 转换为业务层使用的 {@link MemoryExtractionResult} 列表，过滤无效条目。
     */
    private List<MemoryExtractionResult> convertResult(MemoryExtractionAI.MemoryExtractionResult result) {
        if (result == null || result.memories() == null) {
            return Collections.emptyList();
        }
        return result.memories().stream()
                .filter(item -> item.content() != null && !item.content().isBlank() && item.type() != null)
                .map(item -> new MemoryExtractionResult(
                        item.content().trim(),
                        item.type().toUpperCase(),
                        item.importance(),
                        item.confidence()))
                .collect(Collectors.toList());
    }

    /**
     * 应用上下文窗口限制
     *
     * @param messages 原始消息列表
     * @return 截取后的消息列表（保留最近的N条）
     */
    private List<ChatMessage> applyContextWindow(List<ChatMessage> messages) {
        int windowSize = config.getContextWindowSize();
        if (messages.size() <= windowSize) {
            return messages;
        }
        log.info("[Memory Extract] Applying context window: {} -> {}", messages.size(), windowSize);
        return messages.subList(messages.size() - windowSize, messages.size());
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
        return extractAndSaveDtos(conversationId, messages, userId, null).size();
    }

    @Override
    public int extractAndSave(String conversationId, List<ChatMessage> messages, String userId, String model) {
        return extractAndSaveDtos(conversationId, messages, userId, model).size();
    }

    @Override
    public List<MemoryDTO> extractAndSaveDtos(
            String conversationId, List<ChatMessage> messages, String userId) {
        return extractAndSaveDtos(conversationId, messages, userId, null);
    }

    @Override
    public List<MemoryDTO> extractAndSaveDtos(
            String conversationId, List<ChatMessage> messages, String userId, String model) {
        log.info("[记忆提取] 开始提取 - 会话: {}, 用户: {}, 消息数: {}", conversationId, userId, messages.size());

        List<MemoryExtractionResult> results = extract(messages, model);
        log.info("[记忆提取] 提取到 {} 条潜在记忆", results.size());

        if (!results.isEmpty()) {
            for (MemoryExtractionResult r : results) {
                log.info("[记忆提取] - 内容: '{}', 类型: {}, 重要性: {}, 置信度: {}",
                        r.content(), r.type(), r.importance(), r.confidence());
            }
        }

        if (results.isEmpty()) {
            log.info("[记忆提取] 未提取到任何记忆");
            return List.of();
        }

        List<MemoryDTO> existingMemories = longTermMemoryService.findByUserId(userId);
        Set<String> existingContents = existingMemories.stream()
                .map(MemoryDTO::getContent)
                .map(this::normalizeContent)
                .collect(Collectors.toSet());
        log.info("[记忆提取] 发现 {} 条已有记忆用于去重", existingContents.size());

        double confidenceThreshold = config.getMinConfidence() / 100.0;
        int importanceThreshold = config.getMinImportance();
        double dedupThreshold = config.getDedupSimilarityThreshold();

        List<MemoryDTO> toSave = new ArrayList<>();
        for (MemoryExtractionResult result : results) {
            if (result.confidence() < confidenceThreshold) {
                log.info("[记忆提取] 跳过低置信度记忆 ({} < {}): '{}'",
                        result.confidence(), confidenceThreshold, result.content());
                continue;
            }

            if (result.importance() < importanceThreshold) {
                log.info("[记忆提取] 跳过低重要性记忆 ({} < {}): '{}'",
                        result.importance(), importanceThreshold, result.content());
                continue;
            }

            String normalizedContent = normalizeContent(result.content());
            if (existingContents.contains(normalizedContent)) {
                log.info("[记忆提取] 跳过重复记忆: '{}'", normalizedContent);
                continue;
            }

            // 语义去重：向量相似度 ≥ 阈值则判定为语义重复，拒绝存储
            if (dedupThreshold > 0 && longTermMemoryService.hasSimilarMemory(
                    userId, normalizedContent, dedupThreshold)) {
                log.info("[记忆提取] 跳过语义相似记忆 (threshold={}): '{}'",
                        dedupThreshold, normalizedContent);
                continue;
            }

            MemoryDTO dto = MemoryDTO.builder()
                    .userId(userId)
                    .content(normalizedContent)
                    .type(result.type())
                    .importance(result.importance())
                    .confidence(result.confidence())
                    .source("对话记忆提取")
                    .build();
            toSave.add(dto);
            existingContents.add(normalizedContent);
        }

        if (!toSave.isEmpty()) {
            log.info("[记忆提取] 保存 {} 条新记忆", toSave.size());
            return longTermMemoryService.saveAll(toSave);
        } else {
            log.info("[记忆提取] 没有新记忆需要保存");
            return List.of();
        }
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
            sb.append(role).append(": ").append(getMessageText(message)).append("\n");
        }
        return sb.toString();
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

            String text = ((UserMessage) message).singleText();
            List<MemoryExtractionResult> extracted = extractFromText(text);
            results.addAll(extracted);
        }

        return results;
    }

    /**
     * 从文本中提取记忆（增强版，支持多种模式识别）
     *
     * @param text 输入文本
     * @return 提取的记忆结果列表
     */
    private List<MemoryExtractionResult> extractFromText(String text) {
        List<MemoryExtractionResult> results = new ArrayList<>();

        // 识别技能信息（使用XX开发/编程）
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

        // 识别身份信息（我是/我叫）
        if (text.contains("我是") || text.contains("我叫")) {
            String profile = text.substring(0, Math.min(text.length(), 30));
            results.add(new MemoryExtractionResult(profile, "PROFILE", 9, 0.9));
        }

        // 识别职业信息（职业是/工作是）
        if (text.contains("职业是") || text.contains("工作是")) {
            int idx = text.contains("职业是") ? text.indexOf("职业是") + 3 : text.indexOf("工作是") + 3;
            String career = text.substring(idx).trim();
            if (!career.isEmpty()) {
                results.add(new MemoryExtractionResult("用户职业：" + career, "PROFILE", 8, 0.85));
            }
        }

        // 识别项目信息（项目/开发/做）
        if (text.contains("项目") || text.contains("开发") || text.contains("做")) {
            String project = text.substring(0, Math.min(text.length(), 50));
            results.add(new MemoryExtractionResult(project, "PROJECT", 7, 0.7));
        }

        // 识别偏好信息（喜欢/偏好/习惯）
        if (text.contains("喜欢") || text.contains("偏好") || text.contains("习惯")) {
            String preference = text.substring(0, Math.min(text.length(), 40));
            results.add(new MemoryExtractionResult(preference, "PREFERENCE", 6, 0.75));
        }

        // 识别任务信息（需要/必须/应该）
        if (text.contains("需要") || text.contains("必须") || text.contains("应该")) {
            String task = text.substring(0, Math.min(text.length(), 50));
            results.add(new MemoryExtractionResult(task, "TASK", 7, 0.7));
        }

        // 识别日期/事件信息（明天/下周/计划）
        if (text.contains("明天") || text.contains("下周") || text.contains("计划") ||
                text.contains("会议") || text.contains("出差")) {
            String event = text.substring(0, Math.min(text.length(), 50));
            results.add(new MemoryExtractionResult(event, "EVENT", 7, 0.65));
        }

        // 识别关系信息（朋友/同事/客户）
        if (text.contains("朋友") || text.contains("同事") || text.contains("客户")) {
            String relation = text.substring(0, Math.min(text.length(), 40));
            results.add(new MemoryExtractionResult(relation, "RELATION", 6, 0.7));
        }

        // 识别知识领域（熟悉/了解/掌握）
        if (text.contains("熟悉") || text.contains("了解") || text.contains("掌握")) {
            int idx = text.contains("熟悉") ? text.indexOf("熟悉") + 2
                    : text.contains("了解") ? text.indexOf("了解") + 2 : text.indexOf("掌握") + 2;
            String knowledge = text.substring(idx).trim();
            if (!knowledge.isEmpty()) {
                results.add(new MemoryExtractionResult("用户熟悉" + knowledge, "KNOWLEDGE", 7, 0.75));
            }
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

    private static String getMessageText(ChatMessage message) {
        if (message instanceof UserMessage userMsg) {
            return userMsg.singleText();
        } else if (message instanceof AiMessage aiMsg) {
            return aiMsg.text();
        } else if (message instanceof SystemMessage sysMsg) {
            return sysMsg.text();
        }
        return null;
    }
}
