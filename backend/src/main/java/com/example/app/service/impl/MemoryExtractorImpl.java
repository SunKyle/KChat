package com.example.app.service.impl;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.MemoryExtractorConfig;
import com.example.app.dto.MemoryDTO;
import com.example.app.entity.ModelConfig;
import com.example.app.service.LongTermMemoryService;
import com.example.app.service.MemoryExtractor;
import com.example.app.service.ModelConfigService;
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
     * Ollama 客户端，用于按前端选择的本地模型提取记忆
     */
    private final OllamaClient ollamaClient;
    /**
     * OpenAI 兼容客户端，用于按自定义模型配置提取记忆
     */
    private final OpenAICompatibleClient openAICompatibleClient;
    /**
     * 模型配置服务，用于查找自定义模型
     */
    private final ModelConfigService modelConfigService;
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
            你是一个专业的记忆提取与总结专家。请从以下对话中：

            1. 提取值得长期记忆的重要事实信息
            2. 对相关信息进行总结归纳
            3. 识别用户的身份、技能、偏好、项目、任务、知识、关系、事件等

            提取规则：
            - 只提取事实性信息，不要保存对话内容本身
            - 忽略问候语、闲聊、一次性问题
            - 每条记忆保持简洁（不超过50字）
            - 对相关信息进行合并总结
            - 为每条记忆标注类型：PROFILE/PREFERENCE/PROJECT/SKILL/TASK/KNOWLEDGE/RELATION/EVENT
            - 为每条记忆评估重要性（1-10分，越高越重要）
            - 为每条记忆评估置信度（0.0-1.0）

            记忆类型说明：
            - PROFILE: 用户身份、职业、角色等基本信息
            - PREFERENCE: 用户偏好、喜好、习惯等
            - PROJECT: 用户正在进行的项目或工作
            - SKILL: 用户掌握的技能、技术栈
            - TASK: 用户的任务、目标、待办事项
            - KNOWLEDGE: 用户拥有的知识、专业领域
            - RELATION: 用户的人际关系、社交网络
            - EVENT: 用户参与的事件、活动、时间安排

            对话：
            {conversation}

            请输出JSON格式：
            {
              "summary": "对对话内容的简要总结（不超过100字）",
              "memories": [
                {
                  "content": "用户使用Java开发",
                  "type": "SKILL",
                  "importance": 8,
                  "confidence": 0.95
                }
              ]
            }
            """;

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
            String prompt = EXTRACTION_PROMPT.replace("{conversation}", conversation);
            log.info("[记忆提取] 发送提取请求到LLM (窗口大小: {})", windowedMessages.size());
            String response = generateExtraction(prompt, model);
            log.info("[记忆提取] LLM响应接收: {}",
                    response.length() > 100 ? response.substring(0, 100) + "..." : response);
            return parseExtractionResult(response);
        } catch (Exception e) {
            log.warn("[记忆提取] LLM提取失败，降级到规则提取: {}", e.getMessage());
            return extractFallback(windowedMessages);
        }
    }

    /**
     * 优先使用前端选择的模型提取记忆：
     * 1. 自定义模型配置（OpenAI 兼容）命中时走 OpenAICompatibleClient
     * 2. 否则作为 Ollama 模型名调用
     * 3. 都失败时回退到默认 ChatLanguageModel
     */
    private String generateExtraction(String prompt, String model) {
        if (model != null && !model.isBlank()) {
            try {
                ModelConfig config = modelConfigService.getConfigByModelId(model);
                if (config != null) {
                    String actualModelId = model.startsWith(config.getName() + ":")
                            ? model.substring(config.getName().length() + 1)
                            : model;
                    log.info("[记忆提取] 使用自定义模型 {} 提取", actualModelId);
                    return openAICompatibleClient.chatCompletion(
                            actualModelId, config.getBaseUrl(), config.getApiKey(), null, prompt);
                }
            } catch (Exception e) {
                log.warn("[记忆提取] 自定义模型调用失败，回退到 Ollama/默认模型: {}", e.getMessage());
            }

            try {
                log.info("[记忆提取] 使用 Ollama 模型 {} 提取", model);
                return ollamaClient.generate(List.of(UserMessage.from(prompt)), model);
            } catch (Exception e) {
                log.warn("[记忆提取] Ollama 模型调用失败，回退到默认模型: {}", e.getMessage());
            }
        }
        return chatLanguageModel.generate(prompt);
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
        return extractAndSave(conversationId, messages, userId, null);
    }

    @Override
    public int extractAndSave(String conversationId, List<ChatMessage> messages, String userId, String model) {
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
            return 0;
        }

        List<MemoryDTO> existingMemories = longTermMemoryService.findByUserId(userId);
        Set<String> existingContents = existingMemories.stream()
                .map(MemoryDTO::getContent)
                .collect(Collectors.toSet());
        log.info("[记忆提取] 发现 {} 条已有记忆用于去重", existingContents.size());

        double confidenceThreshold = config.getMinConfidence() / 100.0;
        int importanceThreshold = config.getMinImportance();

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
            longTermMemoryService.saveAll(toSave);
        } else {
            log.info("[记忆提取] 没有新记忆需要保存");
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
     * 解析提取结果（支持包含summary的新格式）
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

            // 记录总结信息
            if (result.containsKey("summary")) {
                String summary = (String) result.get("summary");
                log.info("[Memory Extract] Conversation summary: {}",
                        summary.length() > 50 ? summary.substring(0, 50) + "..." : summary);
            }

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
}
