package com.example.app.service.tool.tools;

import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 内置日期时间工具
 *
 * 提供 {@code getCurrentDateTime} 工具，供 LLM 在 Agent 模式下查询当前时间。
 * 用于验证 Agent 工具调用闭环（ModelRoutingStage → ToolCallDetection →
 * ToolInvocation → ToolResultAssembly → 下一轮 LLM 调用）。
 */
@Component
public class DateTimeTool implements ToolComponent {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool("获取当前的日期和时间，格式为 yyyy-MM-dd HH:mm:ss（时区 Asia/Shanghai）")
    String getCurrentDateTime() {
        return LocalDateTime.now(ZONE).format(FORMATTER);
    }
}
