package com.example.app.pipeline.stage.assembly;

import org.springframework.stereotype.Component;

/**
 * OrchestratorSystemPromptProvider —— 为顶层编排帧生成 System Prompt。
 *
 * <p>在双层 ReAct 架构中，顶层 Orchestrator LLM 的身份是"智能助手兼任务编排器"，
 * 有两种工作模式（详见 prompt 正文）：
 * <ol>
 *   <li><b>模式一：直接回答</b>（默认）—— 普通对话、知识问答、闲聊、解释、翻译、写作、代码
 *       等不需要调用外部技能的场景，像正常 AI 助手一样直接答。</li>
 *   <li><b>模式二：技能路由</b> —— 用户明确请求操作性任务时，每轮选一个 Skill
 *       （伪 function calling）执行子任务，最后汇总结果。</li>
 * </ol>
 *
 * <p><b>上下文注入策略</b>：模式一直接答会用到 search_context / cognee_memory，
 * 模式二路由时可辅助判断。因此 {@link #build} 接收这两个参数，空值时对应块整体跳过，
 * 不会浪费 token；非空时注入以避免 Orchestrator 在模式一直接答时幻觉。
 *
 * <p>Orchestrator 层只暴露 call_skill_* 伪函数（由 ToolDefinitionStage 控制），
 * 看不到任何原子 Tool，因此具体执行能力始终受 Skill 约束边界限制。
 *
 * <p>本类为 Spring Component，单例无状态，由 SystemPromptAssemblyStage 在
 * 当前帧 role=ORCHESTRATOR 时调用。
 */
@Component
public class OrchestratorSystemPromptProvider {

    /**
     * 构建 Orchestrator 帧的 System Prompt 全文。
     *
     * <p><b>模式一兼容性</b>：prompt 允许 Orchestrator 在不涉及操作任务时直接回答用户
     * （模式一：直接答）。因此 search_context 和 cognee_memory 也需要注入，否则
     * Orchestrator 在直接回答依赖搜索/记忆的知识问答时会因上下文缺失而幻觉。
     * 空值时对应块整体跳过，不浪费 token。
     *
     * @param userLanguage    用户语言（zh-CN / en 等；可空）
     * @param customRules     会话级自定义指令（可空）
     * @param userProfileText 用户档案文本块（可空）
     * @param kbReferences    显式引用的知识库段落（可空）
     * @param searchContext   Web 搜索上下文（模式一直接答时用于时事/最新资料；可空）
     * @param cogneeMemory    Cognee 长期记忆（模式一直接答时用于跨会话偏好/事实；可空）
     * @return Orchestrator 专用 System Prompt 全文
     */
    public String build(String userLanguage,
                        String customRules,
                        String userProfileText,
                        String kbReferences,
                        String searchContext,
                        String cogneeMemory) {
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

        // ── Web 搜索上下文（可选）──────────────────────────────
        // 模式一直接答时用于时事/最新资料；模式二路由时可帮助 Orchestrator 判断
        // 是否需要把搜索结果传给 Skill（但目前 instruction 参数只接受自然语言描述，
        // 复杂搜索结果建议让 Skill 内部重新搜，这里主要供模式一直接答使用）
        if (searchContext != null && !searchContext.isBlank()) {
            sb.append("\n\n## Web 搜索结果（仅供直接回答参考）\n\n")
              .append(searchContext.trim())
              .append("\n\n若走模式一直接答，请优先引用以上结果；若走模式二分派技能，可让技能自行重新搜索获取更精准结果。\n");
        }

        // ── Cognee 长期记忆（可选）────────────────────────────
        // 模式一直接答时用于跨会话偏好/事实；模式二路由时可辅助判断 Skill 选择
        if (cogneeMemory != null && !cogneeMemory.isBlank()) {
            sb.append("\n\n## 长期记忆（跨会话偏好与事实）\n\n")
              .append(cogneeMemory.trim())
              .append("\n\n请基于这些长期记忆个性化你的回答与路由决策。\n");
        }

        sb.append("\n\n---\n现在开始。请阅读用户输入，判断是直接回答还是需要调用技能。\n");
        return sb.toString();
    }
}
