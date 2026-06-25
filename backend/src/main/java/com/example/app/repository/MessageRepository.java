package com.example.app.repository;

import com.example.app.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByConversationIdOrderByTimestampAsc(String conversationId);
    void deleteByConversationId(String conversationId);
    
    /**
     * 删除指定时间戳之后的所有消息（包含指定时间戳）
     */
    void deleteByConversationIdAndTimestampGreaterThanEqual(String conversationId, LocalDateTime timestamp);
    
    /**
     * 删除指定时间戳之后的所有消息（不包含指定时间戳）
     */
    void deleteByConversationIdAndTimestampGreaterThan(String conversationId, LocalDateTime timestamp);
    
    /**
     * 查找指定时间戳之前的消息
     */
    List<Message> findByConversationIdAndTimestampLessThanOrderByTimestampAsc(String conversationId, LocalDateTime timestamp);
}