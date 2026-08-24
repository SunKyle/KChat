package com.example.app.pipeline.context;

import java.util.List;

/**
 * Cognee 长期记忆在 Pipeline 上下文中的中性载体。
 *
 * <p>解耦设计：Pipeline 上下文层不应依赖 {@code com.example.app.service.CogneeClient}
 * 这一具体服务实现（及其内嵌 record）。本类只持有原始字段（文本 / 分数 / 来源 /
 * 实体 / 关系），由 {@code LongTermMemoryStage} 在 Pipeline 边界把
 * {@code CogneeClient.RecallResult} 转成 {@link Fragment} 后写入
 * {@code ConversationContext}，{@code MemoryFormatStage} 也只感知本中性类型。
 *
 * <p>这样 Cognee 服务实现可独立替换 / mock，无需改动 Pipeline 上下文层。
 *
 * @param fragments  召回的文本片段（带分数与来源元数据）
 * @param entities   解析出的实体名集合
 * @param relations  解析出的关系三元组集合
 */
public record CogneeMemoryContext(
        List<Fragment> fragments,
        List<String> entities,
        List<Relation> relations) {

    /**
     * 单条召回片段的中性表示。
     *
     * @param text          片段正文
     * @param score         相关性分数
     * @param source        来源标签（"graph" / "session" / ...）
     * @param dataId        入库 Data item id（对应 KnowledgeDocument.cogneeDataId，可空）
     * @param documentName  源文档名（可空）
     * @param datasetName   命中结果所属数据集名（可空）
     */
    public record Fragment(
            String text,
            double score,
            String source,
            String dataId,
            String documentName,
            String datasetName) {
    }

    /** 知识图谱关系三元组。 */
    public record Relation(String source, String relation, String target) {
    }

    public boolean isEmpty() {
        return (fragments == null || fragments.isEmpty())
                && (entities == null || entities.isEmpty())
                && (relations == null || relations.isEmpty());
    }

    /** 空上下文常量，避免各处重复 new。 */
    public static CogneeMemoryContext empty() {
        return new CogneeMemoryContext(List.of(), List.of(), List.of());
    }
}
