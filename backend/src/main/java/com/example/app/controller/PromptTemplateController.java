package com.example.app.controller;

import com.example.app.entity.PromptTemplate;
import com.example.app.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Prompt 模板管理控制器
 * 
 * 提供模板的 CRUD 操作 API
 */
@RestController
@RequestMapping("/api/prompt-templates")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class PromptTemplateController {

    private final PromptTemplateService templateService;

    /**
     * 创建新模板
     * 
     * @param request 创建请求
     * @return 创建的模板
     */
    @PostMapping
    public ResponseEntity<PromptTemplate> create(@RequestBody CreateTemplateRequest request) {
        log.info("Creating prompt template: {}", request.name());
        PromptTemplate template = templateService.create(
                request.name(),
                request.content(),
                request.description(),
                request.category(),
                request.defaults()
        );
        return ResponseEntity.ok(template);
    }

    /**
     * 获取所有模板
     * 
     * @return 模板列表
     */
    @GetMapping
    public ResponseEntity<List<PromptTemplate>> getAll() {
        log.info("Getting all prompt templates");
        List<PromptTemplate> templates = templateService.findAll();
        return ResponseEntity.ok(templates);
    }

    /**
     * 获取所有启用的模板
     * 
     * @return 启用的模板列表
     */
    @GetMapping("/active")
    public ResponseEntity<List<PromptTemplate>> getActive() {
        log.info("Getting active prompt templates");
        List<PromptTemplate> templates = templateService.findActive();
        return ResponseEntity.ok(templates);
    }

    /**
     * 根据ID获取模板
     * 
     * @param id 模板ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<PromptTemplate> getById(@PathVariable String id) {
        log.info("Getting prompt template by id: {}", id);
        return templateService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据名称获取模板的最新版本
     * 
     * @param name 模板名称
     * @return 最新版本模板
     */
    @GetMapping("/name/{name}")
    public ResponseEntity<PromptTemplate> getByName(@PathVariable String name) {
        log.info("Getting prompt template by name: {}", name);
        return templateService.findLatestVersion(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据分类获取模板
     * 
     * @param category 分类名称
     * @return 模板列表
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<PromptTemplate>> getByCategory(@PathVariable String category) {
        log.info("Getting prompt templates by category: {}", category);
        List<PromptTemplate> templates = templateService.findByCategory(category);
        return ResponseEntity.ok(templates);
    }

    /**
     * 更新模板（创建新版本）
     * 
     * @param id 模板ID
     * @param request 更新请求
     * @return 更新后的模板（新版本）
     */
    @PutMapping("/{id}")
    public ResponseEntity<PromptTemplate> update(@PathVariable String id, @RequestBody UpdateTemplateRequest request) {
        log.info("Updating prompt template: {}", id);
        PromptTemplate template = templateService.update(id, request.content(), request.description());
        return ResponseEntity.ok(template);
    }

    /**
     * 更新模板状态（启用/禁用）
     * 
     * @param id 模板ID
     * @param request 状态更新请求
     * @return 更新后的模板
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<PromptTemplate> updateStatus(@PathVariable String id, @RequestBody UpdateStatusRequest request) {
        log.info("Updating prompt template status: {}, active={}", id, request.active());
        PromptTemplate template = templateService.updateStatus(id, request.active());
        return ResponseEntity.ok(template);
    }

    /**
     * 删除模板
     * 
     * @param id 模板ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        log.info("Deleting prompt template: {}", id);
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 渲染模板（替换占位符）
     * 
     * @param name 模板名称
     * @param params 替换参数
     * @return 渲染后的模板内容
     */
    @PostMapping("/render/{name}")
    public ResponseEntity<RenderResult> render(@PathVariable String name, @RequestBody(required = false) Map<String, String> params) {
        log.info("Rendering prompt template: {}", name);
        String content = templateService.renderTemplate(name, params);
        return ResponseEntity.ok(new RenderResult(content));
    }

    /**
     * 获取默认系统模板
     * 
     * @param params 替换参数
     * @return 渲染后的模板内容
     */
    @PostMapping("/default-system")
    public ResponseEntity<RenderResult> getDefaultSystemTemplate(@RequestBody(required = false) Map<String, String> params) {
        log.info("Getting default system template");
        String content = templateService.getDefaultSystemTemplate(params);
        return ResponseEntity.ok(new RenderResult(content));
    }

    /**
     * 刷新模板缓存
     * 
     * @return 200 OK
     */
    @PostMapping("/refresh-cache")
    public ResponseEntity<Void> refreshCache() {
        log.info("Refreshing prompt template cache");
        templateService.refreshCache();
        return ResponseEntity.ok().build();
    }

    // ==================== Request/Response DTOs ====================

    /**
     * 创建模板请求
     */
    public record CreateTemplateRequest(
            String name,
            String content,
            String description,
            String category,
            String defaults
    ) {}

    /**
     * 更新模板请求
     */
    public record UpdateTemplateRequest(
            String content,
            String description
    ) {}

    /**
     * 更新状态请求
     */
    public record UpdateStatusRequest(
            Boolean active
    ) {}

    /**
     * 渲染结果
     */
    public record RenderResult(
            String content
    ) {}
}