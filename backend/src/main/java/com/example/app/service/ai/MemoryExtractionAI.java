package com.example.app.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import java.util.List;

/**
 * 记忆提取 AI 接口
 *
 * 通过 LangChain4j {@link dev.langchain4j.service.AiServices} 代理调用 LLM，
 * 框架自动注入 JSON Schema 并将返回值反序列化为 {@link MemoryExtractionResult}，
 * 替代原先手写的 {@code indexOf("{")} + Jackson 解析逻辑。
 *
 * 调用方负责传入格式化好的对话文本，失败时降级到
 * {@code MemoryExtractorImpl.extractFallback} 的规则提取。
 */
public interface MemoryExtractionAI {

    @SystemMessage("""
            你是一个专业的记忆提取与总结专家。请从用户提供的对话中：

            1. 提取值得长期记忆的重要事实信息
            2. 对相关信息进行总结归纳
            3. 识别用户的身份、技能、偏好、项目、任务、知识、关系、事件等

            提取规则：
            - 只提取事实性信息，不要保存对话内容本身
            - 忽略问候语、闲聊、一次性问题
            - 每条记忆保持简洁（不超过50字）
            - 对相关信息进行合并总结
            - 为每条记忆标注类型：PROFILE/PREFERENCE/PROJECT/SKILL/TASK/KNOWLEDGE/RELATION/EVENT
            - 为每条记忆评估重要性（1-10分，越高越重要）
            - 为每条记忆评估置信度（0.0-1.0）

            记忆类型说明：
            - PROFILE: 用户身份、职业、角色等基本信息
            - PREFERENCE: 用户偏好、喜好、习惯等
            - PROJECT: 用户正在进行的项目或工作
            - SKILL: 用户掌握的技能、技术栈
            - TASK: 用户的任务、目标、待办事项
            - KNOWLEDGE: 用户拥有的知识、专业领域
            - RELATION: 用户的人际关系、社交网络
            - EVENT: 用户参与的事件、活动、时间安排
            """)
    MemoryExtractionResult extract(@UserMessage String conversation);

    /**
     * 记忆提取结果（顶层结构）
     *
     * @param summary  对对话内容的简要总结（不超过100字）
     * @param memories 提取到的记忆条目列表
     */
    record MemoryExtractionResult(String summary, List<MemoryItem> memories) {
    }

    /**
     * 单条记忆条目
     *
     * @param content    记忆内容（不超过50字）
     * @param type       记忆类型（PROFILE/PREFERENCE/PROJECT/SKILL/TASK/KNOWLEDGE/RELATION/EVENT）
     * @param importance 重要性（1-10）
     * @param confidence  置信度（0.0-1.0）
     */
    record MemoryItem(String content, String type, int importance, double confidence) {
    }
}
