package com.example.app.dto;

/**
 * 用户消息引用记录（知识库 / 技能）。
 *
 * <p>记录用户在发送消息时显式引用的资源，序列化为 JSON 数组存于
 * Message.references 列，供前端在历史会话中展示"当时引用了什么"。
 *
 * <ul>
 *   <li>{@code type="knowledge_base"} — 用户通过 @ 引用的知识库</li>
 *   <li>{@code type="skill"} — 用户通过技能选择器激活的技能</li>
 * </ul>
 */
public record MessageReference(String id, String name, String type) {

    public static final String TYPE_KNOWLEDGE_BASE = "knowledge_base";
    public static final String TYPE_SKILL = "skill";

    public static MessageReference knowledgeBase(String id, String name) {
        return new MessageReference(id, name, TYPE_KNOWLEDGE_BASE);
    }

    public static MessageReference skill(String id, String name) {
        return new MessageReference(id, name, TYPE_SKILL);
    }
}
