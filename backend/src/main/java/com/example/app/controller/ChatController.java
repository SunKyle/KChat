package com.example.app.controller;

import com.example.app.client.OllamaClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.dto.ConversationDTO;
import com.example.app.dto.MessageDTO;
import com.example.app.entity.Conversation;
import com.example.app.entity.Message;
import com.example.app.repository.ConversationRepository;
import com.example.app.repository.MessageRepository;
import com.example.app.service.ChatService;
import com.example.app.service.MemoryService;
import com.example.app.service.StreamingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private final ChatService chatService;
    private final StreamingService streamingService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MemoryService memoryService;
    private final OllamaClient ollamaClient;

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDTO> createConversation(@RequestBody(required = false) ConversationDTO request) {
        String title = request != null && request.getTitle() != null ? request.getTitle() : "新对话";

        Conversation conversation = Conversation.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .build();

        conversationRepository.save(conversation);

        return ResponseEntity.ok(ConversationDTO.fromEntity(conversation));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> listConversations() {
        List<Conversation> conversations = conversationRepository.findAllByOrderByUpdatedAtDesc();
        List<ConversationDTO> dtos = conversations.stream()
                .map(ConversationDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable String id) {
        return conversationRepository.findById(id)
                .map(conversation -> {
                    List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(id);
                    List<MessageDTO> messageDTOs = messages.stream()
                            .map(MessageDTO::fromEntity)
                            .collect(Collectors.toList());
                    return ConversationDTO.fromEntity(conversation, messageDTOs);
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/conversations/{id}")
    public ResponseEntity<ConversationDTO> updateConversation(@PathVariable String id,
            @RequestBody ConversationDTO request) {
        return conversationRepository.findById(id)
                .map(conversation -> {
                    if (request.getTitle() != null && !request.getTitle().isEmpty()) {
                        conversation.setTitle(request.getTitle());
                    }
                    Conversation updated = conversationRepository.save(conversation);
                    return ResponseEntity.ok(ConversationDTO.fromEntity(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String id) {
        if (conversationRepository.existsById(id)) {
            conversationRepository.deleteById(id);
            memoryService.clearMemory(id);
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
