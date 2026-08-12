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

/**
 * 对话服务，负责对话的 CRUD 操作
 * 
 * <功能说明>
 * - 核心职责：管理对话的创建、查询、更新、删除
 * - 设计模式：标准服务模式，封装数据访问逻辑
 * - 依赖关系：依赖 ConversationRepository、MessageRepository、ShortTermMemoryService
 * 
 * <使用场景>
 * - 创建新对话
 * - 获取对话列表
 * - 获取对话详情（包含消息）
 * - 更新对话标题
 * - 删除对话（级联删除消息和记忆）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    /**
     * 对话数据访问层
     */
    private final ConversationRepository conversationRepository;

    /**
     * 消息数据访问层
     */
    private final MessageRepository messageRepository;

    /**
     * 短期记忆服务，用于删除对话时清理记忆
     */
    private final ShortTermMemoryService shortTermMemoryService;

    /**
     * 创建新对话
     * 
     * @param title 对话标题，为空时使用默认值"新对话"
     * @return 创建的对话 DTO
     */
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

    /**
     * 获取对话列表，按更新时间降序排列
     * 
     * @return 对话列表
     */
    @Transactional(readOnly = true)
    public List<ConversationDTO> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(ConversationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取单个对话详情，包含消息列表
     * 
     * @param id 对话 ID
     * @return 对话详情，包含消息列表；如果不存在返回 null
     */
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

    /**
     * 更新对话标题、置顶状态或自定义规则
     * 
     * @param id          对话 ID
     * @param title       新标题（可选）
     * @param pinned      置顶状态（可选）
     * @param customRules 自定义规则（可选，null 表示不更新，空字符串表示清空）
     * @return 更新后的对话；如果不存在返回 null
     */
    @Transactional
    public ConversationDTO updateConversation(String id, String title, Boolean pinned, String customRules) {
        return conversationRepository.findById(id)
                .map(conversation -> {
                    if (title != null && !title.isBlank()) {
                        conversation.setTitle(title);
                    }
                    if (pinned != null) {
                        conversation.setPinned(pinned);
                    }
                    if (customRules != null) {
                        conversation.setCustomRules(customRules.isBlank() ? null : customRules);
                    }
                    Conversation updated = conversationRepository.save(conversation);
                    log.info("Updated conversation: {}", id);
                    return ConversationDTO.fromEntity(updated);
                })
                .orElse(null);
    }

    /**
     * 删除对话
     * 
     * 级联操作：
     * 1. 删除对话记录
     * 2. 删除关联的消息记录
     * 3. 清理短期记忆
     * 
     * @param id 对话 ID
     * @return 删除成功返回 true，不存在返回 false
     */
    @Transactional
    public boolean deleteConversation(String id) {
        if (conversationRepository.existsById(id)) {
            conversationRepository.deleteById(id);
            messageRepository.deleteByConversationId(id);
            shortTermMemoryService.clearMemory(id);
            log.info("Deleted conversation: {}", id);
            return true;
        }
        return false;
    }

    /**
     * 检查对话是否存在
     * 
     * @param id 对话 ID
     * @return 存在返回 true，不存在返回 false
     */
    @Transactional(readOnly = true)
    public boolean existsById(String id) {
        return conversationRepository.existsById(id);
    }
}
