package com.example.app.repository;

import com.example.app.entity.SkillUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SkillUsage 仓库 —— 记录 Skill 调用历史。
 */
@Repository
public interface SkillUsageRepository extends JpaRepository<SkillUsage, Long> {

    /** 按 Skill ID 查询调用历史，按开始时间倒序 */
    List<SkillUsage> findBySkillIdOrderByStartedAtDesc(String skillId);

    /** 按会话 ID 查询 Skill 调用历史 */
    List<SkillUsage> findByConversationIdOrderByStartedAtDesc(String conversationId);

    /** 按用户 ID 查询 Skill 调用历史 */
    List<SkillUsage> findByUserIdOrderByStartedAtDesc(String userId);
}
