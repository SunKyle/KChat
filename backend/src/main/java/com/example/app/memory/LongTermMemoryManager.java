package com.example.app.memory;

import com.example.app.entity.LongTermMemory;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.repository.LongTermMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆管理器类，负责用户长期记忆的存储、检索和删除操作
 * 使用Spring框架的组件注解和日志注解
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryManager {

    /**
     * 长期记忆数据仓库，通过构造方法注入
     */
    private final LongTermMemoryRepository repository;

    /**
     * 存储用户的长期记忆，使用默认的记忆类型(KNOWLEDGE)
     *
     * @param userId  用户ID
     * @param content 记忆内容
     */
    public void store(String userId, String content) {
        store(userId, content, MemoryType.KNOWLEDGE);
    }

    /**
     * 存储用户的长期记忆，支持指定记忆类型的字符串形式
     * 如果指定的类型无效，则默认使用KNOWLEDGE类型
     *
     * @param userId  用户ID
     * @param content 记忆内容
     * @param type    记忆类型的字符串表示
     */
    public void store(String userId, String content, String type) {
        MemoryType memoryType;
        try {
            memoryType = MemoryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            memoryType = MemoryType.KNOWLEDGE;
        }
        store(userId, content, memoryType);
    }

    /**
     * 存储用户的长期记忆，使用指定的记忆类型
     *
     * @param userId  用户ID
     * @param content 记忆内容
     * @param type    记忆类型枚举
     */
    public void store(String userId, String content, MemoryType type) {
        LongTermMemory memory = LongTermMemory.builder()
                .userId(userId)
                .content(content)
                .type(type)
                .build();
        repository.save(memory);
        log.debug("Stored long-term memory for user: {}", userId);
    }

    /**
     * 检索用户的所有长期记忆，按创建时间降序排列
     *
     * @param userId 用户ID
     * @return 记忆内容列表
     */
    public List<String> retrieve(String userId) {
        List<LongTermMemory> memories = repository.findByUserIdOrderByCreatedAtDesc(userId);
        List<String> contents = new ArrayList<>();
        for (LongTermMemory memory : memories) {
            contents.add(memory.getContent());
        }
        return contents;
    }

    /**
     * 检索用户指定类型的长期记忆，按创建时间降序排列
     * 如果指定的类型无效，返回空列表
     *
     * @param userId 用户ID
     * @param type   记忆类型的字符串表示
     * @return 记忆内容列表
     */
    public List<String> retrieve(String userId, String type) {
        MemoryType memoryType;
        try {
            memoryType = MemoryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
        List<LongTermMemory> memories = repository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, memoryType);
        List<String> contents = new ArrayList<>();
        for (LongTermMemory memory : memories) {
            contents.add(memory.getContent());
        }
        return contents;
    }

    /**
     * 删除指定的长期记忆
     *
     * @param memoryId 记忆ID
     */
    public void delete(Long memoryId) {
        repository.deleteById(memoryId);
        log.debug("Deleted long-term memory: {}", memoryId);
    }

    /**
     * 清除用户的所有长期记忆
     *
     * @param userId 用户ID
     */
    public void clear(String userId) {
        repository.deleteByUserId(userId);
        log.debug("Cleared all long-term memories for user: {}", userId);
    }
}