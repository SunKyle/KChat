package com.example.app.repository;

import com.example.app.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Prompt 模板数据访问层
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, String> {

    /**
     * 根据名称查询模板
     * 
     * @param name 模板名称
     * @return 模板列表
     */
    List<PromptTemplate> findByName(String name);

    /**
     * 查询所有启用的模板
     * 
     * @return 启用的模板列表
     */
    List<PromptTemplate> findByActiveTrue();

    /**
     * 根据分类查询模板
     * 
     * @param category 分类名称
     * @return 模板列表
     */
    List<PromptTemplate> findByCategory(String category);

    /**
     * 根据分类查询启用的模板
     * 
     * @param category 分类名称
     * @return 启用的模板列表
     */
    List<PromptTemplate> findByCategoryAndActiveTrue(String category);

    /**
     * 查询指定名称的最新版本模板
     * 
     * @param name 模板名称
     * @return 最新版本模板
     */
    @Query("SELECT p FROM PromptTemplate p WHERE p.name = :name ORDER BY p.version DESC LIMIT 1")
    Optional<PromptTemplate> findLatestVersionByName(String name);

    /**
     * 查询指定名称的启用模板的最新版本
     * 
     * @param name 模板名称
     * @return 最新版本的启用模板
     */
    @Query("SELECT p FROM PromptTemplate p WHERE p.name = :name AND p.active = true ORDER BY p.version DESC LIMIT 1")
    Optional<PromptTemplate> findActiveLatestVersionByName(String name);

    /**
     * 检查名称是否存在
     * 
     * @param name 模板名称
     * @return true 如果存在
     */
    boolean existsByName(String name);
}