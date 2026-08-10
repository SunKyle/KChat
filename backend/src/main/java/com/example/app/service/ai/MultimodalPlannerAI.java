package com.example.app.service.ai;

import com.example.app.dto.MultimodalPlan;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 多模态任务规划 AI 接口
 *
 * 通过 LangChain4j {@link dev.langchain4j.service.AiServices} 代理调用 LLM，
 * 框架自动注入 JSON Schema 并将返回值反序列化为 {@link MultimodalPlan}，
 * 替代原先手写的 {@code objectMapper.readValue} 解析逻辑。
 *
 * 调用方负责将对话历史、用户输入、图片数量、最大步数拼接成完整 prompt 传入，
 * 失败时降级到 {@code MultimodalPlannerService.fallbackPlan} 的规则规划。
 */
public interface MultimodalPlannerAI {

    @SystemMessage("""
            你是一个多模态任务规划器。根据用户提供的对话历史、用户输入和图片数量，输出一个 JSON 计划。

            要求：
            - 只输出 JSON，不要输出其他文字
            - steps 是数组，每项包含 type、prompt、text、targetImage
            - type 只能是 vision、image_gen、text 之一
            - vision：需要理解用户上传图片时使用，targetImage 是图片索引（从 0 开始）
            - image_gen：需要生成图片时使用，prompt 是生成图片的描述
            - image_gen 的 prompt 必须是自包含的完整画面描述，不能依赖对话历史，因为它会直接发送给图像生成模型
            - text：需要文本回答时使用
            """)
    MultimodalPlan plan(@UserMessage String prompt);
}
