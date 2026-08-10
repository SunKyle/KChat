package com.example.app.service.tool;

import com.example.app.pipeline.context.ConversationContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Tool 执行器
 *
 * 统一的工具调用入口：根据 {@link ConversationContext.ToolCallRecord} 中的工具名，
 * 从 {@link ToolRegistry} 查找对应实例与 Method，通过 LangChain4j
 * {@link DefaultToolExecutor} 反射执行，返回 {@link ConversationContext.ToolResultRecord}。
 *
 * 职责：
 * <ul>
 *   <li>工具查找失败 / 执行异常 → 返回 success=false 的 ToolResultRecord（不抛异常）</li>
 *   <li>记录调用日志（工具名、参数、耗时、成功/失败）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolExecutor {

    private final ToolRegistry toolRegistry;

    /**
     * 执行单次工具调用。
     *
     * @param call 工具调用记录（工具名、参数 JSON、调用 ID）
     * @return 工具执行结果记录
     */
    public ConversationContext.ToolResultRecord execute(ConversationContext.ToolCallRecord call) {
        String toolName = call.toolName();
        Optional<Object> toolOpt = toolRegistry.getTool(toolName);
        Optional<Method> methodOpt = toolRegistry.getMethod(toolName);

        if (toolOpt.isEmpty() || methodOpt.isEmpty()) {
            log.warn("[ToolExecutor] Tool not found: {}", toolName);
            return new ConversationContext.ToolResultRecord(
                    toolName, call.toolCallId(), null, false, "Tool not found: " + toolName);
        }

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(call.toolCallId())
                .name(toolName)
                .arguments(call.arguments())
                .build();

        long t0 = System.currentTimeMillis();
        try {
            DefaultToolExecutor delegate = new DefaultToolExecutor(toolOpt.get(), methodOpt.get());
            String result = delegate.execute(request, null);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("[ToolExecutor] '{}' executed in {}ms, result length={}",
                    toolName, elapsed, result != null ? result.length() : 0);
            return new ConversationContext.ToolResultRecord(
                    toolName, call.toolCallId(), result, true, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("[ToolExecutor] '{}' failed in {}ms: {}", toolName, elapsed, e.getMessage(), e);
            return new ConversationContext.ToolResultRecord(
                    toolName, call.toolCallId(), null, false, e.getMessage());
        }
    }
}
