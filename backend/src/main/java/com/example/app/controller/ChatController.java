package com.example.app.controller;

import com.example.app.client.OllamaClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.dto.ConversationDTO;
import com.example.app.service.ChatService;
import com.example.app.service.ConversationService;
import com.example.app.service.StreamingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final StreamingService streamingService;
    private final ConversationService conversationService;
    private final OllamaClient ollamaClient;

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDTO> createConversation(@RequestBody(required = false) ConversationDTO request) {
        String title = request != null && request.getTitle() != null ? request.getTitle() : "新对话";
        ConversationDTO conversation = conversationService.createConversation(title);
        return ResponseEntity.ok(conversation);
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> listConversations() {
        List<ConversationDTO> dtos = conversationService.listConversations();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable String id) {
        ConversationDTO conversation = conversationService.getConversation(id);
        return conversation != null ? ResponseEntity.ok(conversation) : ResponseEntity.notFound().build();
    }

    @PutMapping("/conversations/{id}")
    public ResponseEntity<ConversationDTO> updateConversation(@PathVariable String id,
            @RequestBody ConversationDTO request) {
        ConversationDTO conversation = conversationService.updateConversation(id, request.getTitle());
        return conversation != null ? ResponseEntity.ok(conversation) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String id) {
        if (conversationService.deleteConversation(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.generateResponse(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody ChatRequest request) {
        return streamingService.streamResponse(request);
    }

    @GetMapping("/models")
    public ResponseEntity<List<String>> listModels() {
        List<String> models = ollamaClient.listModels();
        return ResponseEntity.ok(models);
    }

}
