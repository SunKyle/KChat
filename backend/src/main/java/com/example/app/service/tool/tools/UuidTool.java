package com.example.app.service.tool.tools;

import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * UUID 生成工具
 *
 * 提供 UUID 生成功能，支持生成单个或多个 UUID。
 * 当用户需要生成唯一标识符时调用。
 */
@Slf4j
@Component
public class UuidTool implements ToolComponent {

    @Tool("""
            生成 UUID（通用唯一标识符）。
            可指定生成数量（默认 1 个，最多 100 个）。
            每个 UUID 为标准的 36 位格式（如 550e8400-e29b-41d4-a716-446655440000）。
            适用于：生成数据库主键、会话标识、交易流水号等场景。
            """)
    public String generateUuid(Integer count) {
        int num = (count != null && count > 0) ? Math.min(count, 100) : 1;

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < num; i++) {
                if (i > 0) sb.append("\n");
                sb.append(UUID.randomUUID().toString());
            }

            log.info("[UuidTool] Generated {} UUID(s)", num);
            return sb.toString();

        } catch (Exception e) {
            log.error("[UuidTool] Error: {}", e.getMessage());
            return "UUID 生成失败：" + e.getMessage();
        }
    }
}