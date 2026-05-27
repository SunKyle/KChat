
package com.example.app.service;

import com.example.app.dto.ConversationDTO;
import com.example.app.dto.MessageDTO;
import com.example.app.entity.Conversation;
import com.example.app.entity.Message;
import com.example.app.repository.ConversationRepository;
import com.example.app.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MemoryService memoryService;

    @Transactional
    public ConversationDTO createConversation(String title) {
        String conversationId = UUID.randomUUID().toString();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .title(title != null && !title.isBlank() ? title : "新对话")
                .build();
        conversationRepository.save(conversation);
        log.info("Created new conversation: {}", conversationId);
        return ConversationDTO.fromEntity(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationDTO> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(ConversationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConversationDTO getConversation(String id) {
        return conversationRepository.findById(id)
                .map(conversation -> {
                    List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(id);
                    List<MessageDTO> messageDTOs = messages.stream()
                            .map(MessageDTO::fromEntity)
                            .collect(Collectors.toList());
                    return ConversationDTO.fromEntity(conversation, messageDTOs);
                })
                .orElse(null);
    }

    @Transactional
    public ConversationDTO updateConversation(String id, String title) {
        return conversationRepository.findById(id)
                .map(conversation -> {
                    if (title != null && !title.isBlank()) {
                        conversation.setTitle(title);
                    }
                    Conversation updated = conversationRepository.save(conversation);
                    log.info("Updated conversation: {}", id);
                    return ConversationDTO.fromEntity(updated);
                })
                .orElse(null);
    }

    @Transactional
    public boolean deleteConversation(String id) {
        if (conversationRepository.existsById(id)) {
            conversationRepository.deleteById(id);
            messageRepository.deleteByConversationId(id);
            memoryService.clearMemory(id);
            log.info("Deleted conversation: {}", id);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean existsById(String id) {
        return conversationRepository.existsById(id);
    }
}
