package com.example.app.pipeline.stage.assembly;

import org.springframework.stereotype.Component;

/**
 * OrchestratorSystemPromptProvider —— 为顶层编排帧生成 System Prompt。
 *
 * <p>在双层 ReAct 架构中，顶层 Orchestrator LLM 的身份不是"万能助手"，而是
 * "任务分派器（Orchestrator / Router）"。它的职责是：
 * <ol>
 *   <li>理解用户的多领域复合需求</li>
 *   <li>将任务拆分为若干子任务</li>
 *   <li>每轮选择最合适的 <b>一个 Skill</b>（伪 function calling）来执行子任务</li>
 *   <li>收集所有 Skill 的执行结果，汇总为用户可读的最终回答</li>
 * </ol>
 *
 * <p>Orchestrator 层 <b>严禁</b> 自己直接回答知识性问题。
 * 它"只做路由不做干活"：任何具体执行都要分派给 Skill（或当确实无匹配 Skill
 * 时，走 fallback 给出通用回答）。这样才能：
 * <ul>
 *   <li>让 Skill 内部的 systemPrompt / 工具白名单真正生效（约束生效边界）</li>
 *   <li>让 trace / usage 统计在 Skill 粒度可观测</li>
 *   <li>避免 Orchestrator 在没带精确记忆时"幻觉作答"</li>
 * </ul>
 *
 * <p>本类为 Spring Component，单例无状态，由 SystemPromptAssemblyStage 在
 * 当前帧 role=ORCHESTRATOR 时调用。
 */
@Component
public class OrchestratorSystemPromptProvider {

    /**
     * 构建 Orchestrator 帧的 System Prompt 全文。
     *
     * @param userLanguage    用户语言（zh-CN / en 等；可空）
     * @param customRules     会话级自定义指令（可空）
     * @param userProfileText 用户档案文本块（可空）
     * @param kbReferences    显式引用的知识库段落（可空）
     * @return Orchestrator 专用 System Prompt 全文
     */
    public String build(String userLanguage,
                        String customRules,
                        String userProfileText,
                        String kbReferences) {
        StringBuilder sb = new StringBuilder();

        // ── 角色与核心规则 ──────────────────────────────────────
        sb.append("""
                # 角色：智能助手兼任务编排器

                你是一个智能助手。你有两种工作模式，根据用户输入自动切换：

                ## 模式一：直接回答（默认）

                对于**普通对话、知识问答、闲聊、解释、翻译、写作、代码**等不需要调用外部技能的场景，
                **直接回答用户**，就像一个正常的 AI 助手一样。不要画蛇添足去调用技能。

                ## 模式二：技能路由（仅当用户需求匹配某个 Skill 时）

                当用户的请求**明确需要执行某个特定领域的操作**（如创建提醒、管理待办、记笔记等），
                且存在匹配的技能（以 call_skill_xxx 函数形式暴露）时，通过调用技能来完成：

                1. **判断是否需要技能**：用户是否在请求一个你自身无法直接完成的**操作性任务**？
                   （例："帮我设个提醒" → 需要技能；"什么是光合作用" → 不需要，直接答）
                2. **分派执行**：每一轮从可用的技能中选择**最合适的一个**去执行。
                   多个子任务分多轮串行执行（ReAct 模式），每轮最多调用 1 个技能。
                3. **汇总输出**：技能执行完毕后，把结果整理为用户友好的回答。

                ## 核心原则

                - **优先直接回答**：绝大多数用户输入是普通对话或知识问答，直接回答即可，
                  不需要调用任何技能。只有在用户**明确请求操作性任务**且存在匹配技能时才路由。
                - **一次只调一个技能**：多轮次串行，每轮最多调用 1 个技能。
                - **不过度拆分**：同领域的一个复合动作（如"明天3点提醒我+后天4点再提醒"）
                  属于同一个技能的职责范围，不要拆成两次调用；
                  但跨领域（"先提醒我开会，再帮我写个周报待办"）要分两次调用不同技能。
                - **结果忠实回传**：技能返回的结果是什么，你就用什么做后续推理/汇总，
                  不要脑补、篡改、夸大技能返回的内容。
                - **无合适技能时直接回答**：如果用户的需求不需要技能，或者没有匹配的技能，
                  就像正常助手一样直接回答，不要说"没有合适的技能"这种话。

                ## 判断示例

                - "法国首都是哪里" → 直接回答（知识问答，不需要技能）
                - "帮我写一首关于秋天的诗" → 直接回答（写作能力，不需要技能）
                - "明天下午3点提醒我开会" → 调用提醒技能
                - "帮我建一个待办：买菜" → 调用待办技能
                - "先提醒我明天开会，再帮我查一下什么是量子计算" → 第一轮调提醒技能，第二轮直接回答
                """);

        // ── 语言约束 ──────────────────────────────────────────────
        if (userLanguage != null && !userLanguage.isBlank()) {
            sb.append("\n\n## 语言\n\n请始终用 **")
              .append(userLanguage)
              .append("** 进行输出（包括中间思考的自然语言部分和最终汇总）。\n");
        }

        // ── 用户档案（可选）──────────────────────────────────────
        if (userProfileText != null && !userProfileText.isBlank()) {
            sb.append("\n\n## 用户档案（供你在分派时做个性化参考）\n\n")
              .append(userProfileText.trim())
              .append("\n");
        }

        // ── 自定义指令（可选）───────────────────────────────────
        if (customRules != null && !customRules.isBlank()) {
            sb.append("\n\n## 会话自定义指令\n\n")
              .append(customRules.trim())
              .append("\n");
        }

        // ── 显式知识库引用（可选）───────────────────────────────
        if (kbReferences != null && !kbReferences.isBlank()) {
            sb.append("\n\n## 用户显式引用的知识库片段\n\n")
              .append(kbReferences.trim())
              .append("\n\n请在分派技能时，将这些片段作为上下文传递给对应 Skill（通过 instruction 参数自然语言描述即可）。\n");
        }

        sb.append("\n\n---\n现在开始。请阅读用户输入，判断是直接回答还是需要调用技能。\n");
        return sb.toString();
    }
}
