package com.example.app.dto;

/**
 * 知识库引用来源记录（文档层级）。
 *
 * <p>{@code kbName} 为知识库名称，{@code docName} 为命中的具体文档名。
 * 当溯源元数据缺失（Cognee 未返回 document_name）时 {@code docName} 为 null，
 * 前端应降级为仅展示知识库层级。
 *
 * <p>序列化为 JSON 数组存于 Message.kbReferences 列，并通过 SSE done 事件
 * 透传给前端渲染"引用来源"标签。
 */
public record KbReference(String kbName, String docName) {

    /** 便捷构造：仅知识库层级（无文档信息）。 */
    public static KbReference of(String kbName) {
        return new KbReference(kbName, null);
    }

    /** 便捷构造：知识库 + 文档层级。 */
    public static KbReference of(String kbName, String docName) {
        return new KbReference(kbName, docName == null || docName.isBlank() ? null : docName);
    }
}
