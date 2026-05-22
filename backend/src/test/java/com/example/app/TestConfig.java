package com.example.app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public ChatLanguageModel mockChatLanguageModel() {
        return messages -> {
            String responseContent = "这是测试响应: " + messages.get(messages.size() - 1).text();
            return Response.from(dev.langchain4j.data.message.AiMessage.from(responseContent));
        };
    }

    @Bean
    @Primary
    public StreamingChatLanguageModel mockStreamingChatLanguageModel() {
        return (messages, handler) -> {
            String responseContent = "这是流式测试响应: " + messages.get(messages.size() - 1).text();
            handler.onNext(responseContent);
            handler.onComplete(Response.from(dev.langchain4j.data.message.AiMessage.from(responseContent)));
        };
    }
}