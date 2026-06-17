package com.example.app.service;

import com.example.app.entity.PromptTemplate;
import com.example.app.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prompt 模板服务层
 * 
 * 提供模板的 CRUD 操作、版本管理和缓存功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptTemplateService {

    private final PromptTemplateRepository repository;

    /**
     * 创建新模板
     * 
     * @param name 模板名称
     * @param content 模板内容
     * @param description 模板描述
     * @param category 模板分类
     * @param defaults 默认参数（JSON格式）
     * @return 创建的模板
     */
    @Transactional
    @CacheEvict(value = "promptTemplates", allEntries = true)
    public PromptTemplate create(String name, String content, String description, String category, String defaults) {
        // 检查名称是否已存在
        if (repository.existsByName(name)) {
            throw new IllegalArgumentException("模板名称已存在: " + name);
        }

        PromptTemplate template = PromptTemplate.builder()
                .name(name)
                .content(content)
                .description(description)
                .category(category)
                .defaults(defaults)
                .version(1)
                .active(true)
                .build();

        template = repository.save(template);
        log.info("Created new prompt template: id={}, name={}, version={}", 
                template.getId(), template.getName(), template.getVersion());
        return template;
    }

    /**
     * 更新模板内容（创建新版本）
     * 
     * @param id 模板ID
     * @param content 新的模板内容
     * @param description 新的描述
     * @return 更新后的模板（新版本）
     */
    @Transactional
    @CacheEvict(value = "promptTemplates", allEntries = true)
    public PromptTemplate update(String id, String content, String description) {
        PromptTemplate existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + id));

        // 创建新版本
        PromptTemplate newVersion = PromptTemplate.builder()
                .id(java.util.UUID.randomUUID().toString())
                .name(existing.getName())
                .content(content)
                .description(description != null ? description : existing.getDescription())
                .category(existing.getCategory())
                .defaults(existing.getDefaults())
                .version(existing.getVersion() + 1)
                .active(true)
                .build();

        // 禁用旧版本
        existing.setActive(false);
        repository.save(existing);

        newVersion = repository.save(newVersion);
        log.info("Updated prompt template: name={}, oldVersion={}, newVersion={}", 
                newVersion.getName(), existing.getVersion(), newVersion.getVersion());
        return newVersion;
    }

    /**
     * 更新模板状态（启用/禁用）
     * 
     * @param id 模板ID
     * @param active 是否启用
     * @return 更新后的模板
     */
    @Transactional
    @CacheEvict(value = "promptTemplates", allEntries = true)
    public PromptTemplate updateStatus(String id, boolean active) {
        PromptTemplate template = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + id));
        
        template.setActive(active);
        template = repository.save(template);
        log.info("Updated prompt template status: id={}, active={}", id, active);
        return template;
    }

    /**
     * 删除模板
     * 
     * @param id 模板ID
     */
    @Transactional
    @CacheEvict(value = "promptTemplates", allEntries = true)
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        repository.deleteById(id);
        log.info("Deleted prompt template: id={}", id);
    }

    /**
     * 根据ID查询模板
     * 
     * @param id 模板ID
     * @return 模板
     */
    @Transactional(readOnly = true)
    public Optional<PromptTemplate> findById(String id) {
        return repository.findById(id);
    }

    /**
     * 根据ID查询模板（强制）
     * 
     * @param id 模板ID
     * @return 模板
     * @throws IllegalArgumentException 如果不存在
     */
    @Transactional(readOnly = true)
    public PromptTemplate getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + id));
    }

    /**
     * 查询所有模板
     * 
     * @return 模板列表
     */
    @Transactional(readOnly = true)
    public List<PromptTemplate> findAll() {
        return repository.findAll();
    }

    /**
     * 查询所有启用的模板
     * 
     * @return 启用的模板列表
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "promptTemplates", key = "'active'")
    public List<PromptTemplate> findActive() {
        return repository.findByActiveTrue();
    }

    /**
     * 根据分类查询模板
     * 
     * @param category 分类名称
     * @return 模板列表
     */
    @Transactional(readOnly = true)
    public List<PromptTemplate> findByCategory(String category) {
        return repository.findByCategory(category);
    }

    /**
     * 根据名称查询模板的最新版本
     * 
     * @param name 模板名称
     * @return 最新版本模板
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "promptTemplates", key = "#name")
    public Optional<PromptTemplate> findLatestVersion(String name) {
        return repository.findLatestVersionByName(name);
    }

    /**
     * 根据名称查询启用模板的最新版本
     * 
     * @param name 模板名称
     * @return 最新版本的启用模板
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "promptTemplates", key = "'active_' + #name")
    public Optional<PromptTemplate> findActiveLatestVersion(String name) {
        return repository.findActiveLatestVersionByName(name);
    }

    /**
     * 获取模板内容并替换占位符
     * 
     * @param name 模板名称
     * @param params 替换参数
     * @return 替换后的模板内容
     */
    @Transactional(readOnly = true)
    public String renderTemplate(String name, Map<String, String> params) {
        PromptTemplate template = repository.findActiveLatestVersionByName(name)
                .orElseThrow(() -> new IllegalArgumentException("未找到启用的模板: " + name));

        String content = template.getContent();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                content = content.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return content;
    }

    /**
     * 获取默认系统模板内容
     * 
     * @param params 替换参数
     * @return 替换后的模板内容
     */
    @Transactional(readOnly = true)
    public String getDefaultSystemTemplate(Map<String, String> params) {
        return renderTemplate("default-system-prompt", params);
    }

    /**
     * 检查模板是否存在
     * 
     * @param id 模板ID
     * @return true 如果存在
     */
    @Transactional(readOnly = true)
    public boolean existsById(String id) {
        return repository.existsById(id);
    }

    /**
     * 检查名称是否已存在
     * 
     * @param name 模板名称
     * @return true 如果存在
     */
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }

    /**
     * 刷新模板缓存
     */
    @CacheEvict(value = "promptTemplates", allEntries = true)
    public void refreshCache() {
        log.info("Prompt template cache refreshed");
    }
}