package com.example.app.repository;

import com.example.app.entity.Todo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TodoRepository extends JpaRepository<Todo, String> {

    /**
     * 查询用户所有待办，状态升序，优先级降序，时间倒序
     */
    List<Todo> findByUserIdOrderByStatusAscPriorityDescUpdatedAtDesc(String userId);

    /**
     * 分页查询用户待办
     */
    Page<Todo> findByUserId(String userId, Pageable pageable);

    /**
     * 按状态查询用户待办
     */
    List<Todo> findByUserIdAndStatus(String userId, String status);

    /**
     * 按优先级查询用户待办
     */
    List<Todo> findByUserIdAndPriority(String userId, String priority);

    /**
     * 按分类查询用户待办
     */
    List<Todo> findByUserIdAndCategory(String userId, String category);

    /**
     * 关键词搜索待办（标题或描述）
     */
    @Query("SELECT t FROM Todo t WHERE t.userId = :userId AND " +
           "(LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Todo> searchByUserIdAndKeyword(@Param("userId") String userId, @Param("keyword") String keyword);

    /**
     * 查询过期待办（截止日期小于当前时间且状态为pending）
     */
    @Query("SELECT t FROM Todo t WHERE t.userId = :userId AND t.dueDate IS NOT NULL AND " +
           "t.dueDate < CURRENT_TIMESTAMP AND t.status = 'pending'")
    List<Todo> findOverdueTodos(@Param("userId") String userId);

    /**
     * 按ID和用户ID查询待办
     */
    Optional<Todo> findByIdAndUserId(String id, String userId);

    /**
     * 删除指定用户的待办
     */
    void deleteByIdAndUserId(String id, String userId);

    /**
     * 统计用户指定状态的待办数量
     */
    long countByUserIdAndStatus(String userId, String status);
}