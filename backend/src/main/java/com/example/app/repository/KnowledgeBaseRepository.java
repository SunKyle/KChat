package com.example.app.repository;

import com.example.app.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, String> {

    /** 查询用户所有知识库，按更新时间倒序 */
    List<KnowledgeBase> findByUserIdOrderByUpdatedAtDesc(String userId);

    /** 按ID和用户ID查询 */
    Optional<KnowledgeBase> findByIdAndUserId(String id, String userId);

    /** 删除指定用户的知识库 */
    void deleteByIdAndUserId(String id, String userId);

    /** 统计用户知识库数量 */
    long countByUserId(String userId);
}
