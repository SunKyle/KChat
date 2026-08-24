package com.example.app.config;

import com.example.app.entity.Skill;
import com.example.app.repository.SkillRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Skill 种子数据初始化器。
 *
 * <p>在 Spring Boot 启动完成后（ApplicationRunner），以"按名称幂等"的方式，
 * 确保系统存在一组预置的公共 Skill（isPublic=true，所有用户可见）。
 *
 * <p>当前预置 Skill：
 * <ul>
 *   <li><b>知识库管理</b>：封装 KnowledgeBaseTool，让 Orchestrator 能主动创建/查询/管理知识库。</li>
 * </ul>
 *
 * <p>未来新增预置 Skill 时，在 {@link #SEED_SKILLS} 里加一条即可，启动时自动补全，
 * 不会覆盖已有的同名称公共 Skill（用户可自行修改）。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
@Slf4j
public class SkillSeedInitializer implements ApplicationRunner {

    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        for (Runnable seeder : SEED_SKILLS) {
            try {
                seeder.run();
            } catch (Exception e) {
                log.error("[SkillSeed] Failed to seed skill: {}", e.getMessage(), e);
            }
        }
    }

    // ── 幂等种子：公共 Skill ─────────────────────────────────

    private final List<Runnable> SEED_SKILLS = List.of(
            this::seedKnowledgeBaseSkill
    );

    // ── 知识库管理 Skill ────────────────────────────────────

    private void seedKnowledgeBaseSkill() {
        final String name = "知识库管理";
        if (skillRepository.findByNameAndIsPublicTrue(name).isPresent()) {
            log.debug("[SkillSeed] Public skill '{}' already exists, skip", name);
            return;
        }

        String systemPromptTemplate = """
                你是知识库管理专家，帮助用户管理他们的知识库和文档。
                
                你的职责：
                1. 主动查询：当用户想从知识库里找资料时，先确认知识库ID，再调 searchInKb；
                   如果不确定信息在哪个库，调 searchAllKb 做全局搜索。
                2. 清单查询：当用户问"我有哪些知识库/文档"时，用 listKb / listKbDocuments。
                3. 创建知识库：当用户说"建一个库叫 XX"时，先 createKb，再告知 ID。
                4. 保存文档：当用户说"把这段文字存到 XX 库"时，先 listKb 确认库 ID，
                   再用 uploadKbDocument 把内容存进去。
                5. 删除文档：先用 listKbDocuments 让用户确认文档 ID，再调 deleteKbDocument
                   （删除不可恢复，请务必提醒用户确认）。
                6. 入库进度：上传后若用户问"有没有完成索引"，调 getKbDocumentStatus。
                
                关键规则：
                - 用户没有显式 @ 知识库 → 可以主动调 searchInKb / searchAllKb 查资料。
                - 用户显式 @ 了知识库 → 片段已由系统注入上下文，通常不需要再主动搜；
                  若需要跨库补充信息，仍可搜其他库。
                - 创建知识库 / 上传文档 / 删除文档 都是写操作，执行前请用自然语言
                  简单复述一次操作内容再真正调用，避免误操作（例如："我将创建知识库「项目笔记」，是否确认？"）
                  但用户明确说"创建"、"删除"、"上传"时不用再确认，直接执行。
                - 搜索结果要引用来源：告诉用户内容来自哪个知识库、哪个文档。
                """;

        List<String> allowedTools = List.of(
                "searchInKb",
                "searchAllKb",
                "listKb",
                "listKbDocuments",
                "createKb",
                "uploadKbDocument",
                "deleteKbDocument",
                "getKbDocumentStatus"
        );

        List<String> keywords = List.of(
                "知识库", "文档", "库", "dataset",
                "上传", "索引", "检索", "搜库",
                "创建知识库", "删除文档", "查知识库"
        );

        String allowedToolNamesJson;
        String triggerKeywordsJson;
        try {
            allowedToolNamesJson = objectMapper.writeValueAsString(allowedTools);
            triggerKeywordsJson = objectMapper.writeValueAsString(keywords);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化知识库管理 Skill 配置失败", e);
        }

        Skill skill = Skill.builder()
                .id(UUID.randomUUID().toString())
                .userId("system")
                .name(name)
                .description("管理个人知识库：创建知识库、上传文档、搜索内容、查看/删除文档和索引状态。")
                .icon("BookOpen")
                .systemPromptTemplate(systemPromptTemplate)
                .allowedToolNamesJson(allowedToolNamesJson)
                .triggerKeywordsJson(triggerKeywordsJson)
                .completionHookType(Skill.CompletionHookType.NONE)
                .maxIterations(5)
                .isEnabled(true)
                .isPublic(true)
                .build();

        skillRepository.save(skill);
        log.info("[SkillSeed] Seeded public skill: name='{}', id={}, allowedTools={}",
                name, skill.getId(), allowedTools.size());
    }
}
