package com.example.app.pipeline.stage.assembly;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.AgentFrame;
import com.example.app.pipeline.context.ConversationContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class MessageAssemblyStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() { return Phase.ASSEMBLY; }

    public String getName() {
        return "messageAssemblyStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        AgentFrame currentFrame = ctx.getAgentStack().peek();

        // SPECIALIST 帧：SkillExecutor 已设置好 assembledMessages（[UserMessage(instruction)]），
        // 这里只需把 SystemPromptAssemblyStage 产出的 SystemMessage 前置插入即可。
        // 不重建列表，避免引入 ORCHESTRATOR 的 shortTermMemory 和原始 userMessage。
        if (currentFrame.getRole() == AgentFrame.Role.SPECIALIST) {
            List<ChatMessage> existing = ctx.getAssembledMessages();
            if (existing == null) {
                existing = new ArrayList<>();
            }
            SystemMessage systemMsg = (SystemMessage) ctx.getAgentState()
                    .get(ConversationContext.KEY_SYSTEM_MESSAGE);
            if (systemMsg != null) {
                // 避免重复插入（内层循环第二轮时 SystemMessage 已在列表中）
                boolean alreadyHasSystem = !existing.isEmpty()
                        && existing.get(0) instanceof SystemMessage;
                if (!alreadyHasSystem) {
                    existing.add(0, systemMsg);
                }
            }
            ctx.setAssembledMessages(existing);
            log.debug("[MessageAssembly] SPECIALIST frame: using existing {} message(s), prepended SystemMessage={}",
                    existing.size(), systemMsg != null);
            return;
        }

        // ORCHESTRATOR 帧：从 scratch 重建消息列表（原逻辑）
        List<ChatMessage> messages = new ArrayList<>();

        SystemMessage systemMsg = (SystemMessage) ctx.getAgentState().get(ConversationContext.KEY_SYSTEM_MESSAGE);
        if (systemMsg != null) {
            messages.add(systemMsg);
        }

        if (ctx.getShortTermMemory() != null) {
            int beforeFilter = ctx.getShortTermMemory().size();
            List<ChatMessage> filtered = new ArrayList<>();
            for (ChatMessage msg : ctx.getShortTermMemory()) {
                if (isEffectivelyEmpty(msg)) {
                    log.debug("[MessageAssembly] Skipping blank/empty message of type {}: {}",
                            msg.getClass().getSimpleName(), describeMessage(msg));
                    continue;
                }
                filtered.add(msg);
            }
            int removed = beforeFilter - filtered.size();
            if (removed > 0) {
                log.info("[MessageAssembly] Filtered out {} blank/empty messages from short-term memory", removed);
            }
            messages.addAll(filtered);
        }

        if (ctx.getUserMessage() != null) {
            messages.add(UserMessage.from(ctx.getUserMessage()));
        }

        ctx.setAssembledMessages(messages);
    }

    /**
     * 判断一条消息是否"实质为空"——即对 LLM 没有任何信息贡献。
     * 空消息会污染上下文窗口、消耗 token，甚至误导模型。
     *
     * 判定规则：
     * - AiMessage: text 为空且无 toolExecutionRequests
     * - UserMessage: text 为空
     * - 其他类型: 无内容
     */
    private boolean isEffectivelyEmpty(ChatMessage msg) {
        if (msg == null) {
            return true;
        }
        if (msg instanceof AiMessage ai) {
            boolean noText = ai.text() == null || ai.text().isBlank();
            boolean noTools = !ai.hasToolExecutionRequests();
            return noText && noTools;
        }
        if (msg instanceof UserMessage user) {
            String text = user.singleText();
            return text == null || text.isBlank();
        }
        return false;
    }

    private String describeMessage(ChatMessage msg) {
        if (msg instanceof AiMessage ai) {
            return "text='" + (ai.text() != null ? ai.text().substring(0, Math.min(ai.text().length(), 50)) : "null")
                    + "', hasTools=" + ai.hasToolExecutionRequests();
        }
        if (msg instanceof UserMessage user) {
            return "text='" + user.singleText() + "'";
        }
        return msg.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 430;
    }
}
