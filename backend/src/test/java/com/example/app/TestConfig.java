package com.example.app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse chat(dev.langchain4j.model.chat.request.ChatRequest chatRequest) {
                String content = "这是测试响应";
                if (!chatRequest.messages().isEmpty()) {
                    ChatMessage last = chatRequest.messages().get(chatRequest.messages().size() - 1);
                    if (last instanceof UserMessage userMsg) {
                        content = "这是测试响应: " + userMsg.singleText();
                    }
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(content))
                        .build();
            }
        };
    }

    @Bean
    @Primary
    public StreamingChatModel mockStreamingChatModel() {
        return new StreamingChatModel() {
            @Override
            public void chat(dev.langchain4j.model.chat.request.ChatRequest chatRequest,
                             StreamingChatResponseHandler handler) {
                String content = "这是流式测试响应";
                if (!chatRequest.messages().isEmpty()) {
                    ChatMessage last = chatRequest.messages().get(chatRequest.messages().size() - 1);
                    if (last instanceof UserMessage userMsg) {
                        content = "这是流式测试响应: " + userMsg.singleText();
                    }
                }
                handler.onPartialResponse(content);
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from(content))
                        .build());
            }
        };
    }
}
