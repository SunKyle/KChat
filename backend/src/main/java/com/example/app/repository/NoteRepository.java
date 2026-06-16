package com.example.app.repository;

import com.example.app.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<Note, String> {

    /**
     * 查询用户所有笔记，置顶优先，时间倒序
     */
    List<Note> findByUserIdOrderByPinnedDescUpdatedAtDesc(String userId);

    /**
     * 分页查询用户笔记
     */
    Page<Note> findByUserId(String userId, Pageable pageable);

    /**
     * 查询用户置顶笔记
     */
    List<Note> findByUserIdAndPinnedTrueOrderByUpdatedAtDesc(String userId);

    /**
     * 按分类查询用户笔记
     */
    List<Note> findByUserIdAndCategory(String userId, String category);

    /**
     * 关键词搜索笔记（标题或内容）
     */
    @Query("SELECT n FROM Note n WHERE n.userId = :userId AND " +
           "(LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Note> searchByUserIdAndKeyword(@Param("userId") String userId, @Param("keyword") String keyword);

    /**
     * 按ID和用户ID查询笔记
     */
    Optional<Note> findByIdAndUserId(String id, String userId);

    /**
     * 删除指定用户的笔记
     */
    void deleteByIdAndUserId(String id, String userId);

    /**
     * 统计用户笔记数量
     */
    long countByUserId(String userId);
}