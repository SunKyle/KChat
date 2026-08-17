package com.example.app.pipeline.context;

import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Prompt 组装状态，包括组装后的消息列表、Token 计数和截断状态。
 */
@Data
@Builder(toBuilder = true)
public class AssemblyState {
    private List<ChatMessage> assembledMessages;
    private int tokenCount;
    private boolean truncated;
    private String aiMessageId;
}