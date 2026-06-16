package com.example.app.controller;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.dto.ConversationDTO;
import com.example.app.dto.SummarizeRequest;
import com.example.app.dto.SummarizeResponse;
import com.example.app.entity.ModelConfig;
import com.example.app.service.ChatService;
import com.example.app.service.ConversationService;
import com.example.app.service.ModelConfigService;
import com.example.app.service.StreamingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;

/**
 * 对话 API 控制器
 * 
 * <功能说明>
 * - 核心职责：提供对话管理、消息发送、模型列表查询等 REST API
 * - 设计模式：RESTful 控制器模式
 * - 依赖关系：依赖 ChatService、StreamingService、ConversationService、ModelConfigService
 * 
 * <API 端点>
 * - POST /api/conversations - 创建新对话
 * - GET /api/conversations - 获取对话列表
 * - GET /api/conversations/{id} - 获取单个对话详情
 * - PUT /api/conversations/{id} - 更新对话
 * - DELETE /api/conversations/{id} - 删除对话
 * - POST /api/chat - 发送消息并获取完整回复
 * - POST /api/chat/stream - 发送消息并流式获取回复
 * - GET /api/models - 获取可用模型列表
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class ChatController {

    /**
     * 聊天服务，处理同步消息响应
     */
    private final ChatService chatService;

    private final StreamingService streamingService;

    private final ConversationService conversationService;

    private final ModelConfigService modelConfigService;

    private final OllamaClient ollamaClient;

    private final OpenAICompatibleClient openAICompatibleClient;

    /**
     * 创建新对话
     * 
     * @param request 可选的对话信息，包含标题
     * @return 创建的对话
     */
    @PostMapping("/conversations")
    public ResponseEntity<ConversationDTO> createConversation(@RequestBody(required = false) ConversationDTO request) {
        String title = request != null && request.getTitle() != null ? request.getTitle() : "新对话";
        ConversationDTO conversation = conversationService.createConversation(title);
        return ResponseEntity.ok(conversation);
    }

    /**
     * 获取对话列表
     * 
     * @return 对话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> listConversations() {
        List<ConversationDTO> dtos = conversationService.listConversations();
        return ResponseEntity.ok(dtos);
    }

    /**
     * 获取单个对话详情
     * 
     * @param id 对话 ID
     * @return 对话详情或 404
     */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable String id) {
        ConversationDTO conversation = conversationService.getConversation(id);
        return conversation != null ? ResponseEntity.ok(conversation) : ResponseEntity.notFound().build();
    }

    /**
     * 更新对话
     * 
     * @param id      对话 ID
     * @param request 包含新标题的请求
     * @return 更新后的对话或 404
     */
    @PutMapping("/conversations/{id}")
    public ResponseEntity<ConversationDTO> updateConversation(@PathVariable String id,
            @RequestBody ConversationDTO request) {
        ConversationDTO conversation = conversationService.updateConversation(id, request.getTitle(),
                request.getPinned());
        return conversation != null ? ResponseEntity.ok(conversation) : ResponseEntity.notFound().build();
    }

    /**
     * 删除对话
     * 
     * @param id 对话 ID
     * @return 204 删除成功或 404
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String id) {
        if (conversationService.deleteConversation(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 发送消息并获取完整回复
     * 
     * @param request 聊天请求
     * @return AI 回复
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.generateResponse(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 发送消息并流式获取回复
     * 
     * @param request 聊天请求
     * @return SSE 流式响应
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody ChatRequest request) {
        return streamingService.streamResponse(request);
    }

    /**
     * 获取可用模型列表
     * 
     * @return 模型名称列表
     */
    @GetMapping("/models")
    public ResponseEntity<List<String>> listModels() {
        List<String> models = modelConfigService.listModels();
        return ResponseEntity.ok(models);
    }

    /**
     * 将 AI 回复内容总结为 Markdown 笔记
     *
     * @param request 包含原始内容和当前选中模型
     * @return 标题 + 精炼后的 Markdown 笔记内容
     */
    @PostMapping("/chat/summarize")
    public ResponseEntity<SummarizeResponse> summarize(@Valid @RequestBody SummarizeRequest request) {
        String model = request.getModel();
        String content = request.getContent();

        String systemPrompt = """
                你是一个专业的笔记整理助手。请将用户提供的 AI 对话回复内容整理为一份结构清晰的 Markdown 笔记。
                
                要求：
                1. 首先输出笔记标题（不超过50字，不加 # 号，单独一行）
                2. 然后用 --- 分隔线隔开
                3. 接着输出整理后的 Markdown 笔记正文
                
                笔记正文格式规范：
                - 使用合适的 Markdown 标题层级（##、###）组织内容结构
                - 保留关键信息、代码示例、要点列表
                - 对重要概念使用**加粗**标记
                - 如有代码，使用 ```语言 代码块格式
                - 保持语言与原文一致
                - 去除对话式的寒暄和冗余表述，只保留核心知识内容
                """;

        String llmResponse;

        ModelConfig customConfig = modelConfigService.getConfigByModelId(model);
        if (customConfig != null) {
            // 自定义/云端模型 (OpenAI, Anthropic, etc.)
            String actualModelId = model.substring(customConfig.getName().length() + 1);
            llmResponse = openAICompatibleClient.chatCompletion(
                    actualModelId,
                    customConfig.getBaseUrl(),
                    customConfig.getApiKey(),
                    systemPrompt,
                    content);
        } else {
            // Ollama 本地模型
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(content));
            llmResponse = ollamaClient.generate(messages, model);
        }

        // 解析 LLM 响应：按 --- 分隔提取标题和内容
        String title;
        String summary;
        String[] parts = llmResponse.split("---", 2);
        if (parts.length >= 2) {
            title = parts[0].trim().replaceAll("^#+\\s*", "");
            summary = parts[1].trim();
        } else {
            title = content.length() > 50 ? content.substring(0, 50) + "..." : content;
            summary = llmResponse.trim();
        }

        return ResponseEntity.ok(SummarizeResponse.builder()
                .title(title)
                .summary(summary)
                .build());
    }

}
