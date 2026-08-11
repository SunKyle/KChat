package com.example.app.pipeline.stage.preprocess;

import com.example.app.config.MemoryExtractorConfig;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.QueryAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Query 分析 Stage（PREPROCESS 阶段，order=290）
 *
 * <p>在长期记忆召回之前（LongTermMemoryStage at 310）执行，
 * 对用户 query 进行意图分类和改写，产出 {@link QueryAnalysisResult}，
 * 供后续 Stage 做记忆类型筛选和门控决策。
 *
 * <p>执行顺序：
 * <pre>
 * LanguageDetectionStage (110) → ShortTermMemoryStage (300)
 *   → QueryAnalyzerStage (290) ← 新增，在 LongTermMemoryStage (310) 之前
 *   → LongTermMemoryStage (310)
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryAnalyzerStage implements ContextPipelineStage {

    private final QueryAnalyzer queryAnalyzer;
    private final MemoryExtractorConfig config;

    @Override
    public Phase getPhase() {
        return Phase.PREPROCESS;
    }

    @Override
    public String getName() {
        return "queryAnalyzerStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        try {
            if (!config.isQueryAnalysisEnabled()) {
                log.debug("[QueryAnalyzer] Disabled by config, skipping");
                return;
            }

            QueryAnalysisResult result = queryAnalyzer.analyze(
                    ctx.getUserMessage(), ctx.getModel());

            ctx.setQueryAnalysisResult(result);

            if (result.isRequiresMemory()) {
                log.info("[QueryAnalyzer] query='{}' intent={} source={} requiresMemory=true " +
                                "types={} excluded={} confidence={}",
                        truncate(ctx.getUserMessage(), 50),
                        result.getIntentType(),
                        result.getSource(),
                        result.getRequiredTypes().size(),
                        result.getExcludedTypes().size(),
                        result.getConfidence());
            } else {
                log.info("[QueryAnalyzer] query='{}' intent={} source={} requiresMemory=false " +
                        "(memory injection skipped)",
                        truncate(ctx.getUserMessage(), 50),
                        result.getIntentType(),
                        result.getSource());
            }
        } catch (Exception e) {
            log.warn("[QueryAnalyzer] Failed to analyze query: {}", e.getMessage());
            // 降级：不设置 queryAnalysisResult，LongTermMemoryStage 会使用默认行为
        }
    }

    @Override
    public int getOrder() {
        return 290;
    }

    @Override
    public boolean isCritical() {
        return false;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}