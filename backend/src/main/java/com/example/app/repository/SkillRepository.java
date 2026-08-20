package com.example.app.repository;

import com.example.app.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Skill 仓库。
 *
 * <p>查询规则：
 * <ul>
 *   <li>用户可见 Skill = 自己的 Skill + isPublic=true 的公共 Skill
 *   <li>激活匹配时只在 isEnabled=true 的 Skill 中查找
 * </ul>
 */
@Repository
public interface SkillRepository extends JpaRepository<Skill, String> {

    /** 查询用户所有可用 Skill（自己的 + 公共的），按更新时间倒序 */
    List<Skill> findByUserIdOrIsPublicTrueOrderByUpdatedAtDesc(String userId);

    /** 查询用户自己的 Skill，按更新时间倒序 */
    List<Skill> findByUserIdOrderByUpdatedAtDesc(String userId);

    /** 按 ID 和用户 ID 查询（只返回用户自己的 Skill） */
    Optional<Skill> findByIdAndUserId(String id, String userId);

    /** 按 ID 查询用户可用 Skill（自己的或公共的） */
    Optional<Skill> findByIdAndUserIdOrIsPublicTrue(String id, String userId);

    /** 删除指定用户的 Skill */
    void deleteByIdAndUserId(String id, String userId);

    /** 统计用户自己的 Skill 数量 */
    long countByUserId(String userId);
}
