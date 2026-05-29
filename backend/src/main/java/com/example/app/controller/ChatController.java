package com.example.app.controller;

import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.dto.ConversationDTO;
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

import java.util.List;

/**
 * 对话 API 控制器
 *
 * 提供对话管理、消息发送、模型列表查询等功能
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final StreamingService streamingService;
    private final ConversationService conversationService;
    private final ModelConfigService modelConfigService;

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
        ConversationDTO conversation = conversationService.updateConversation(id, request.getTitle());
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

}
