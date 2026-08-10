# KChat LangChain4j 封装能力改造方案

> 状态：待评审
> 范围：backend/
> 版本：v1.0
> 关联文档：[backend-codebase-map.md](./backend-codebase-map.md)、[multimodal-auto-plan.md](./multimodal-auto-plan.md)

## 1. 背景与目标

### 1.1 背景

KChat 后端基于 LangChain4j 0.35.0 构建，但实际只用了其底层数据结构和接口抽象（`ChatMessage`、`ChatLanguageModel`、`EmbeddingModel`、`MessageWindowChatMemory`），**没用它的高层封装能力**（`OpenAiChatModel`、`AiServices`、`StreamingChatLanguageModel` 实例、结构化输出等）。

这导致项目中存在大量「重复造轮子」代码：

- `OpenAICompatibleClient.java`（960 行）手写 OkHttp + Jackson + SSE 流式解析
- `OllamaClient.java` 中 `streamGenerate` / `embed` / `listModels` 用 `HttpURLConnection` 重写
- `MemoryExtractorImpl` / `MultimodalPlannerService` 用 `indexOf("{")` + Jackson 手动解析 LLM 输出的 JSON
- `TitleGenerationService` / `ContentOptimizationServiceImpl` 手写 prompt 模板和模型路由

### 1.2 目标

- **降低维护成本**：删除约 1000 行重复代码，让框架处理 HTTP/SSE/JSON 序列化等易错环节
- **提升健壮性**：用框架的结构化输出能力替代手写 JSON 解析，避免 LLM 输出格式漂移导致的 bug
- **保持业务能力**：长期记忆系统、pipeline 编排、三级嵌入降级等业务特性保持自实现
- **不破坏对外 API**：所有改造对 Controller 层透明，前端无感知

### 1.3 改造原则

| 类别 | 处理方式 |
|------|---------|
| 纯 HTTP 拼装 / SSE 解析 / JSON 解析 | ✅ 改用 LangChain4j |
| 简单 prompt 模板 + 单次 LLM 调用 | ✅ 改用 `AiServices` |
| 结构化 JSON 输出 | ✅ 改用 `AiServices` + POJO 返回 |
| 带业务字段的记忆系统 | ❌ 保持自实现 |
| Pipeline 编排架构 | ❌ 保持自实现 |
| 三级降级嵌入链 | ❌ 保持自实现 |

## 2. 当前 LangChain4j 使用现状

### 2.1 已使用的 LangChain4j 能力

| 能力 | 模块 | 位置 |
|------|------|------|
| `ChatMessage` / `SystemMessage` / `UserMessage` / `AiMessage` | 全项目消息抽象 | 几乎所有 pipeline/service |
| `MessageWindowChatMemory` | 短期记忆 FIFO 窗口 | [ShortTermMemory.java](../src/main/java/com/example/app/memory/ShortTermMemory.java) |
| `OllamaChatModel` | Ollama 同步生成 | [OllamaConfig.java](../src/main/java/com/example/app/config/OllamaConfig.java) |
| `OllamaStreamingChatModel` Bean 定义 | 流式模型 Bean | [StreamingConfig.java](../src/main/java/com/example/app/config/StreamingConfig.java) |
| `EmbeddingModel` 接口 | 自实现 DJL 嵌入 | [VectorStoreConfig.java](../src/main/java/com/example/app/config/VectorStoreConfig.java) |
| `RedisEmbeddingStore` | 长期记忆向量库 | 通过 `langchain4j-redis` 依赖 |
| `Response<T>` | 模型响应包装 | 嵌入/聊天返回值 |

### 2.2 已定义但未使用的 Bean

`StreamingConfig#streamingChatLanguageModel` 定义了 `OllamaStreamingChatModel` Bean，但 `OllamaClient.streamGenerate` 完全没有用它，而是自己用 `HttpUrlConnection` 重写了流式调用——**两套实现并存**。

### 2.3 未使用的高层能力

- `OpenAiChatModel` / `OpenAiStreamingChatModel`（来自 `langchain4j-open-ai`，未引入依赖）
- `AiServices` + 接口代理 + `@SystemMessage` / `@UserMessage`
- 结构化输出（POJO 返回值，框架自动注入 JSON Schema）
- `OllamaEmbeddingModel`（用于 Ollama 嵌入 API 调用）
- `OpenAiTokenizer`（基于 jtokkit 的精确 token 估算）

## 3. 评估矩阵

| 模块 | 当前实现 | LangChain4j 替代方案 | 建议 | 优先级 |
|------|---------|---------------------|------|--------|
| `OpenAICompatibleClient.chatCompletion` | OkHttp 手写 HTTP | `OpenAiChatModel.generate()` | 改用框架 | **P0** |
| `OpenAICompatibleClient.streamChatCompletion` | OkHttp 手写 SSE 解析 | `OpenAiStreamingChatModel.generate()` | 改用框架 | **P0** |
| `OpenAICompatibleClient.chatCompletionWithImages` | 手写 content 数组 + base64 | `UserMessage.from(text, Image.from(url))` | 改用框架 | **P0** |
| `OllamaClient.streamGenerate` | HttpURLConnection 手写 SSE | 已有 `OllamaStreamingChatModel` Bean | 改用框架 | **P0** |
| `OllamaClient.streamGenerateWithImages` | 手写 base64 + messages JSON | `UserMessage.from(text, Image.from(url))` | 改用框架 | **P0** |
| `OllamaClient.embed` | 手写 `/api/embed` 调用 | `OllamaEmbeddingModel` | 改用框架 | **P2** |
| `OllamaClient.listModels` | 手写 `/api/tags` | LangChain4j 不提供 | 保持 | — |
| `MemoryExtractorImpl` JSON 解析 | `indexOf("{")` + Jackson | `AiServices` + POJO 返回 | 改用框架 | **P1** |
| `MultimodalPlannerService` JSON 解析 | `objectMapper.readValue` | `AiServices` + POJO 返回 | 改用框架 | **P1** |
| `TitleGenerationService` | 手写 prompt + 清洗 | `AiServices` + `@SystemMessage` | 改用框架 | **P1** |
| `ContentOptimizationServiceImpl` | 手写 prompt + 模型路由 | `AiServices` + 工厂层 | 改用框架 | **P1** |
| `SimpleTokenEstimator` | CJK 字符近似 | `OpenAiTokenizer`（仅 GPT 精确） | 保持 | — |
| `LongTermMemoryService` | DB+向量库双写、复合过滤 | LangChain4j RAG 不够灵活 | 保持 | — |
| `ShortTermMemory` | 已用 `MessageWindowChatMemory` | — | 保持 | — |
| `VectorStoreConfig`（DJL 嵌入） | 三级降级链 | 框架无此能力 | 保持 | — |
| `pipeline/stage/*` | 项目特色编排 | 框架的 `RetrievalAugmentor` 是黑盒 | 保持 | — |
| `MultimodalPlannerService.fallbackPlan` | 业务规则降级 | — | 保持 | — |
| `MemoryExtractorImpl.extractFallback` | LLM 不可用规则降级 | — | 保持 | — |

## 4. 具体优化方案

### P0-1：引入 `langchain4j-open-ai` 依赖

**问题**：当前 `pom.xml` 没有 `langchain4j-open-ai` 依赖，导致 `OpenAICompatibleClient` 只能自己实现 OpenAI 协议。

**方案**：

```xml
<!-- backend/pom.xml 在 langchain4j-ollama 后追加 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

**收益**：解锁 `OpenAiChatModel` / `OpenAiStreamingChatModel` / `OpenAiTokenizer` 等组件。

**风险**：无。`langchain4j-open-ai` 是 LangChain4j 官方模块，与现有 0.35.0 版本完全兼容。

---

### P0-2：新建 `OpenAiModelFactory`，按需构建 `ChatLanguageModel` / `StreamingChatLanguageModel`

**问题**：项目支持用户在前端动态配置多个 OpenAI 兼容模型（每个模型有不同的 baseUrl/apiKey/modelId），无法在启动时静态注入。

**方案**：新建工厂类，按 `(baseUrl, apiKey, modelId)` 维度缓存模型实例（参考 `OllamaClient.modelCache` 的做法）。

```java
// backend/src/main/java/com/example/app/client/OpenAiModelFactory.java
package com.example.app.client;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import com.example.app.config.OpenAIClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class OpenAiModelFactory {

    private final OpenAIClientProperties props;
    private final ConcurrentHashMap<String, ChatLanguageModel> chatCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamingChatLanguageModel> streamCache = new ConcurrentHashMap<>();

    /** 构建缓存 key，避免相同配置重复构建 */
    private String key(String baseUrl, String apiKey, String modelId) {
        return baseUrl + "|" + (apiKey == null ? "" : apiKey.hashCode()) + "|" + modelId;
    }

    public ChatLanguageModel chatModel(String baseUrl, String apiKey, String modelId) {
        return chatCache.computeIfAbsent(key(baseUrl, apiKey, modelId), k ->
            OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey != null ? apiKey : "dummy")
                .modelName(modelId)
                .maxTokens(props.getDefaultMaxTokens())
                .temperature(props.getSyncTemperature())
                .timeout(Duration.ofSeconds(props.getChat().getReadSeconds()))
                .build()
        );
    }

    public StreamingChatLanguageModel streamingModel(String baseUrl, String apiKey, String modelId) {
        return streamCache.computeIfAbsent(key(baseUrl, apiKey, modelId), k ->
            OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey != null ? apiKey : "dummy")
                .modelName(modelId)
                .maxTokens(props.getDefaultMaxTokens())
                .temperature(props.getStreamTemperature())
                .timeout(Duration.ofSeconds(props.getStream().getReadSeconds()))
                .build()
        );
    }

    /** 模型配置变更时清理缓存 */
    public void evict(String baseUrl, String apiKey, String modelId) {
        String k = key(baseUrl, apiKey, modelId);
        chatCache.remove(k);
        streamCache.remove(k);
    }
}
```

**收益**：模型实例复用，避免每次请求都构建；缓存策略与 `OllamaClient.modelCache` 对齐。

---

### P0-3：用 `OpenAiModelFactory` 重写 `OpenAICompatibleClient.chatCompletion`

**问题**：[OpenAICompatibleClient.java#L344-L405](../src/main/java/com/example/app/client/OpenAICompatibleClient.java#L344-L405) 60 行手写 OkHttp 调用 + JSON 解析。

**方案**：

```java
// 改造前
public String chatCompletion(String modelId, String baseUrl, String apiKey, List<ChatMessage> messages) {
    // 60 行 OkHttp 代码...
}

// 改造后
public String chatCompletion(String modelId, String baseUrl, String apiKey, List<ChatMessage> messages) {
    ChatLanguageModel model = modelFactory.chatModel(baseUrl, apiKey, modelId);
    Response<AiMessage> response = model.generate(messages);
    return response.content().text();
}
```

**预计减少代码**：约 60 行（同步聊天）+ 150 行（流式聊天，[L549-L695](../src/main/java/com/example/app/client/OpenAICompatibleClient.java#L549-L695)）。

---

### P0-4：用 `OpenAiStreamingChatModel` + `StreamingResponseHandler` 重写流式聊天

**问题**：[streamChatCompletion L549-L695](../src/main/java/com/example/app/client/OpenAICompatibleClient.java#L549-L695) 150 行手写 SSE 解析（`data: ` 分割、`[DONE]` 判断、JSON 提取、HTML 响应检测）。

**方案**：

```java
public void streamChatCompletion(String modelId, String baseUrl, String apiKey,
        List<ChatMessage> messages, List<String> imageUrls,
        SseEmitter emitter, Consumer<String> onChunk, Runnable onComplete) {

    // 多模态：把 imageUrls 附加到最后一条 UserMessage
    List<ChatMessage> finalMessages = attachImagesIfNeeded(messages, imageUrls);

    StreamingChatLanguageModel model = modelFactory.streamingModel(baseUrl, apiKey, modelId);
    model.generate(finalMessages, new StreamingResponseHandler<AiMessage>() {
        @Override
        public void onPartialResponse(String partial) {
            try {
                onChunk.accept(partial);
                emitter.send(SseEmitter.event()
                    .name("message")
                    .data("{\"content\": \"" + JsonUtils.escape(partial) + "\"}"));
            } catch (Exception e) {
                log.error("SSE send failed: {}", e.getMessage());
            }
        }

        @Override
        public void onCompleteResponse(Response<AiMessage> response) {
            onComplete.run();
            emitter.complete();
        }

        @Override
        public void onError(Throwable error) {
            emitter.completeWithError(error);
        }
    });
}
```

**关键收益**：
- 删除手写的 SSE 行解析、`[DONE]` 判断、HTML 响应检测
- 框架自动处理 chunk 边界、JSON 增量解析
- 多模态图片处理交给框架（见 P0-5）

**注意事项**：
- 框架的 `StreamingResponseHandler` 是同步回调，SseEmitter 仍可正常工作
- `emitter.onCompletion(() -> call.cancel())` 的取消逻辑需要包装：通过自定义 handler 持有取消句柄

---

### P0-5：用 `Image.from()` 替代手写 base64 转换

**问题**：[attachImagesAsContent L889-L928](../src/main/java/com/example/app/client/OpenAICompatibleClient.java#L889-L928) 40 行手写 base64 转换和 content 数组拼装；[imageUrlToBase64 in OllamaClient L202-L220](../src/main/java/com/example/app/client/OllamaClient.java#L202-L220) 也有类似代码。

**方案**：LangChain4j 0.35.0 的 `UserMessage` 原生支持 `Image`：

```java
// 替代手写 base64 + content 数组
List<ChatMessage> attachImagesIfNeeded(List<ChatMessage> messages, List<String> imageUrls) {
    if (imageUrls == null || imageUrls.isEmpty()) return messages;

    List<ChatMessage> result = new ArrayList<>(messages);
    // 找到最后一条 UserMessage，把图片附加进去
    int lastUserIdx = -1;
    for (int i = result.size() - 1; i >= 0; i--) {
        if (result.get(i) instanceof UserMessage) {
            lastUserIdx = i;
            break;
        }
    }

    if (lastUserIdx == -1) return messages;

    UserMessage original = (UserMessage) result.get(lastUserIdx);
    List<Image> images = imageUrls.stream()
        .map(this::loadImage)  // 项目已有的本地文件/URL 加载逻辑
        .filter(Objects::nonNull)
        .toList();

    if (images.isEmpty()) return messages;

    // 框架自动处理 base64 编码、content 数组拼装
    UserMessage withImages = UserMessage.from(original.text(), images.toArray(new Image[0]));
    result.set(lastUserIdx, withImages);
    return result;
}
```

**收益**：删除 `attachImagesAsContent`、`imageUrlToBase64`、`fetchImageAsBase64` 中约 80 行手写 base64 逻辑；框架对 Ollama/OpenAI 都统一处理。

**注意事项**：项目对本地 URL、文件路径、远程 URL 有特殊处理（[L807-L883](../src/main/java/com/example/app/client/OpenAICompatibleClient.java#L807-L883)），这部分逻辑封装在 `loadImage()` 内保留即可。

---

### P0-6：用 `OllamaStreamingChatModel` 重写 `OllamaClient.streamGenerate`

**问题**：[OllamaClient.java#L75-L93](../src/main/java/com/example/app/client/OllamaClient.java#L75-L93) 手写 `HttpUrlConnection` SSE 调用，而 [StreamingConfig.java](../src/main/java/com/example/app/config/StreamingConfig.java) 已定义 `OllamaStreamingChatModel` Bean 但未被使用。

**方案**：扩展 `StreamingConfig`，支持按模型名构建实例；`OllamaClient.streamGenerate` 直接使用：

```java
// 扩展 StreamingConfig
@Configuration
public class StreamingConfig {

    private final OllamaConfig ollamaConfig;
    private final ConcurrentHashMap<String, OllamaStreamingChatModel> cache = new ConcurrentHashMap<>();

    public StreamingChatLanguageModel streamingModel(String modelName) {
        return cache.computeIfAbsent(modelName, k ->
            OllamaStreamingChatModel.builder()
                .baseUrl(ollamaConfig.getBaseUrl())
                .modelName(k)
                .timeout(Duration.ofMinutes(ollamaConfig.getTimeoutMinutes()))
                .build()
        );
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return streamingModel(ollamaConfig.getDefaultModel());
    }
}

// OllamaClient.streamGenerate 改造后
public void streamGenerate(List<ChatMessage> messages, Consumer<String> callback, String model) {
    String targetModel = (model != null && !model.isBlank()) ? model : ollamaConfig.getDefaultModel();
    StreamingChatLanguageModel streamingModel = streamingConfig.streamingModel(targetModel);
    streamingModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
        @Override
        public void onPartialResponse(String partial) { callback.accept(partial); }
        @Override
        public void onCompleteResponse(Response<AiMessage> r) {}
        @Override
        public void onError(Throwable error) { throw new RuntimeException(error); }
    });
}
```

**收益**：删除 `OllamaClient` 中 SSE 手写逻辑，统一用框架；`buildMessagesArray` 方法也可删除（框架自动序列化）。

**注意**：`@Retry` / `@CircuitBreaker` 注解可保留在方法上，Resilience4j 不影响。

---

### P1-1：用 `AiServices` + POJO 重写 `MemoryExtractorImpl` 的 LLM 调用与 JSON 解析

**问题**：[MemoryExtractorImpl.java#L291-L369](../src/main/java/com/example/app/service/impl/MemoryExtractorImpl.java#L291-L369) 用 `indexOf("{")` + Jackson 手动解析 LLM 输出，且 LLM 输出格式漂移时容易失败。

**方案**：定义接口 + POJO，让 LangChain4j 自动注入 JSON Schema 并解析：

```java
// backend/src/main/java/com/example/app/service/ai/MemoryExtractionAI.java
package com.example.app.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

public interface MemoryExtractionAI {

    @SystemMessage("""
        你是记忆提取专家。从对话中提取事实性记忆，每条标注 type/importance/confidence。
        类型：PROFILE/PREFERENCE/PROJECT/SKILL/TASK/KNOWLEDGE/RELATION/EVENT
        规则：只提取事实性信息，不保存对话本身；忽略问候闲聊；每条不超过 50 字。
        """)
    MemoryExtractionResult extract(@UserMessage String conversation);

    record MemoryExtractionResult(String summary, List<MemoryItem> memories) {}
    record MemoryItem(String content, String type, int importance, double confidence) {}
}
```

```java
// 使用：通过工厂构建（按用户选择的模型动态切换）
MemoryExtractionAI extractor = AiServices.builder(MemoryExtractionAI.class)
    .chatLanguageModel(modelFactory.chatModel(baseUrl, apiKey, modelId))
    .build();

MemoryExtractionAI.MemoryExtractionResult result = extractor.extract(conversationText);
// 直接拿到 POJO，不用手动解析 JSON
```

**收益**：
- 删除 `parseExtractionResult` / `extractJson` / `parseMemoryItem` 共约 80 行
- 框架自动处理 JSON Schema 注入和容错，LLM 输出格式漂移时由框架重试
- 保留 `extractFallback` 作为降级（业务规则降级仍需要）

**注意事项**：
- LangChain4j 0.35.0 的结构化输出通过 `@SystemMessage` 中注入 schema 实现，对 Ollama 模型也有效（基于 prompt engineering）
- 工厂层负责按 `modelConfigService.getConfigByModelId(model)` 决定用 `OpenAiChatModel` 还是 `OllamaChatModel`

---

### P1-2：用 `AiServices` 重写 `MultimodalPlannerService`

**问题**：[MultimodalPlannerService.java#L77](../src/main/java/com/example/app/service/MultimodalPlannerService.java#L77) 用 `objectMapper.readValue(response, MultimodalPlan.class)` 直接解析 LLM 输出，无 schema 保证。

**方案**：

```java
public interface MultimodalPlannerAI {

    @SystemMessage("""
        你是多模态任务规划器。根据对话历史、用户输入和图片数量，输出一个 JSON 计划。
        type 只能是 vision、image_gen、text 之一。
        步骤数量不超过 {max_steps}。
        """)
    MultimodalPlan plan(
        @V("history") String history,
        @V("message") String message,
        @V("image_count") int imageCount,
        @V("max_steps") int maxSteps
    );
}
```

**收益**：删除 `PLANNER_PROMPT` 拼装、`objectMapper.readValue` 容错；保留 `fallbackPlan` 作为降级。

---

### P1-3：用 `AiServices` 重写 `TitleGenerationService`

**问题**：[TitleGenerationService.java](../src/main/java/com/example/app/service/TitleGenerationService.java) 60 行手写 prompt 拼装和清洗。

**方案**：

```java
public interface TitleGenerator {

    @UserMessage("""
        根据以下对话内容生成 3-15 字标题，直接输出标题，不加引号编号。
        用户：{{user}}
        AI：{{ai}}
        """)
    String generate(@V("user") String userMsg, @V("ai") String aiResponse);
}
```

**收益**：`TitleGenerationService` 从 60 行降到约 20 行（仅保留模型路由 + `cleanTitle` 兜底清洗）。

---

### P1-4：用 `AiServices` 重写 `ContentOptimizationServiceImpl`

**问题**：[ContentOptimizationServiceImpl.java#L184-L213](../src/main/java/com/example/app/service/impl/ContentOptimizationServiceImpl.java#L184-L213) 手写 `buildSystemPrompt` + 模型路由；调用层 60+ 行。

**方案**：

```java
public interface ContentOptimizer {

    @SystemMessage("""
        你是文本优化助手。请根据以下要求优化文本：{instruction}
        保持原意，只输出优化后的文本，不解释，不说明。
        """)
    String optimize(@V("instruction") String instruction, @UserMessage String content);
}
```

业务层根据 `optimizationType` 拼装 `instruction`（"语法纠错" / "语义优化" 等），调用 `optimizer.optimize(instruction, content)`。

**收益**：删除 `buildSystemPrompt` 约 30 行；保留 `fallbackOptimization` 作为降级。

---

### P1-5：抽取 `AiServiceFactory` 统一构建 `AiServices`

**问题**：P1-1 到 P1-4 都需要按模型配置动态构建 `AiServices`，避免重复代码。

**方案**：

```java
// backend/src/main/java/com/example/app/service/ai/AiServiceFactory.java
@Component
@RequiredArgsConstructor
public class AiServiceFactory {

    private final OpenAiModelFactory openAiModelFactory;
    private final OllamaConfig ollamaConfig;
    private final ModelConfigService modelConfigService;
    private final ConcurrentHashMap<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    /** 按模型 ID 获取 ChatLanguageModel（OpenAI 兼容 or Ollama） */
    public ChatLanguageModel chatModel(String modelId) {
        return modelCache.computeIfAbsent(modelId, id -> {
            ModelConfig config = modelConfigService.getConfigByModelId(id);
            if (config != null) {
                String actualId = id.startsWith(config.getName() + ":")
                    ? id.substring(config.getName().length() + 1) : id;
                return openAiModelFactory.chatModel(config.getBaseUrl(), config.getApiKey(), actualId);
            }
            // Ollama 模型
            return OllamaChatModel.builder()
                .baseUrl(ollamaConfig.getBaseUrl())
                .modelName(id)
                .timeout(Duration.ofMinutes(ollamaConfig.getTimeoutMinutes()))
                .build();
        });
    }

    public <T> T create(Class<T> aiServiceClass, String modelId) {
        return AiServices.builder(aiServiceClass)
            .chatLanguageModel(chatModel(modelId))
            .build();
    }
}
```

业务层使用：

```java
MemoryExtractionAI extractor = aiServiceFactory.create(MemoryExtractionAI.class, model);
MemoryExtractionAI.MemoryExtractionResult result = extractor.extract(conversation);
```

---

### P2-1：用 `OllamaEmbeddingModel` 替代 `OllamaClient.embed`

**问题**：[OllamaClient.java#L247-L279](../src/main/java/com/example/app/client/OllamaClient.java#L247-L279) 手写 `/api/embed` + `/api/embeddings` 双端点调用。

**方案**：

```java
// 仅在 VectorStoreConfig 的降级路径中替换
EmbeddingModel ollamaEmbedding = OllamaEmbeddingModel.builder()
    .baseUrl(ollamaConfig.getBaseUrl())
    .modelName(embeddingModel)
    .build();
Response<Embedding> response = ollamaEmbedding.embed(text);
```

**优先级低**：因为 `VectorStoreConfig` 的三级降级链（DJL → Ollama → Hash）本身就是定制逻辑，替换 `embed` 只是减少 30 行代码，业务影响小。

**注意事项**：替换后需要测试降级链：DJL 失败 → OllamaEmbeddingModel → 哈希兜底，确保三级降级正常工作。

## 5. 保持不动的部分及理由

### 5.1 长期记忆系统 `LongTermMemoryService`

**理由**：业务字段丰富（importance/confidence/type/expiresAt/source），复合过滤（用户+重要度+类型），DB+向量库双写事务，无 query 的高优先级召回——这些需求超出 LangChain4j RAG 的 `EmbeddingStoreContentRetriever` 能力，自己实现更灵活。

详见 [LongTermMemoryService.java#L169-L189](../src/main/java/com/example/app/service/LongTermMemoryService.java#L169-L189)。

### 5.2 短期记忆 `ShortTermMemory`

**理由**：已使用 LangChain4j 的 `MessageWindowChatMemory`，无需改造。

### 5.3 Pipeline 架构 `pipeline/stage/*`

**理由**：项目特色架构，需要精细控制召回时机、Token 管理、prompt 拼装顺序。LangChain4j 的 `RetrievalAugmentor` 是黑盒注入，控制粒度不够。

### 5.4 嵌入模型 `VectorStoreConfig`

**理由**：三级降级链（DJL PyTorch → Ollama → Hash 兜底）是项目独有需求，LangChain4j 没有对应封装。仅 P2-1 中替换 Ollama 调用部分。

### 5.5 业务降级逻辑

- `MultimodalPlannerService.fallbackPlan`：LLM 失败时基于关键词的多模态任务规划
- `MemoryExtractorImpl.extractFallback`：LLM 失败时基于规则的中文模式识别
- `ContentOptimizationServiceImpl.fallbackOptimization`：LLM 失败时基于字符串操作的文本清洗

**理由**：业务规则降级，不属于 LangChain4j 范畴，保持现状。

### 5.6 `OllamaClient.listModels`

**理由**：调用 Ollama `/api/tags` 接口列出本地模型，LangChain4j 不提供该能力。

## 6. 实施建议

### 6.1 里程碑划分

| 里程碑 | 内容 | 预计减少代码 | 风险 |
|-------|------|----------|------|
| **M1：基础引入** | P0-1（依赖）、P0-2（工厂）、P0-6（Ollama 流式） | ~80 行 | 低 |
| **M2：OpenAI 客户端重构** | P0-3、P0-4、P0-5 | ~700 行 | 中 |
| **M3：结构化输出** | P1-5（工厂）、P1-1、P1-2 | ~200 行 | 中 |
| **M4：简单服务** | P1-3、P1-4 | ~100 行 | 低 |
| **M5：嵌入降级优化** | P2-1 | ~30 行 | 低 |

### 6.2 测试策略

每个里程碑完成后，需要验证：

1. **单元测试**：现有测试（如 `MultimodalPlannerServiceTest`、`ContentOptimizationServiceTest`）必须全绿
2. **集成测试**：手动测试以下场景：
   - Ollama 同步/流式聊天
   - OpenAI 兼容模型同步/流式聊天
   - 多模态（图片输入 + 图片生成）
   - 记忆提取（自定义模型 + Ollama 模型）
   - 标题生成
   - 内容优化
3. **回归测试**：前端无感知，所有 Controller 接口签名不变

### 6.3 兼容性考虑

- **`OpenAICompatibleClient.generateImage` / `generateImageSdWebui`**：图像生成（DALL-E / SD WebUI）不在 LangChain4j 范畴，保持手写实现
- **`OpenAICompatibleClient` 中的 `extractImageFromToolCall`**：tool_calls 解析在 0.35.0 的 `OpenAiChatModel` 中已有原生支持，但项目有自定义的 `generate_image` 工具识别逻辑，迁移时需要保留
- **`@Retry` / `@CircuitBreaker`**：Resilience4j 注解在方法上，不影响 LangChain4j 调用，可保留
- **`HttpStreamingTemplate`**：项目封装的 SSE 模板，迁移后可能不再需要，但应保留直到所有调用点都迁移完成

### 6.4 不建议一次性重构

按里程碑渐进式推进，每个里程碑独立可发布。**M2（OpenAI 客户端重构）风险最高**，因为涉及多模态、流式、tool_calls 等复杂场景，建议在测试环境充分验证后再合并。

## 7. 风险与回滚

### 7.1 已识别风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| LangChain4j 0.35.0 的 `OpenAiStreamingChatModel` SSE 实现与项目期望不一致 | 流式输出断流或重复 | M2 完成后在测试环境跑全量场景 |
| `AiServices` 结构化输出对 Ollama 模型效果不稳定 | 记忆提取/规划失败率上升 | 保留 `extractFallback` / `fallbackPlan` 作为降级 |
| 模型实例缓存导致内存占用上升 | 长期运行 OOM | `OpenAiModelFactory` / `AiServiceFactory` 增加 LRU 淘汰 |
| `OpenAiChatModel` 对自定义 baseUrl 兼容性（如 Azure OpenAI、第三方网关） | 部分用户配置失效 | M2 前测试所有 `ModelConfig.type` 类型 |

### 7.2 回滚策略

每个里程碑独立提交，若线上出现问题：

- **M1 / M3 / M4 / M5**：可独立回滚，不影响其他模块
- **M2**：保留旧版 `OpenAICompatibleClient` 直到稳定 1-2 周后再删除（通过 `@Primary` + `@Component` 切换）

## 8. 附录

### 8.1 预计代码量变化

| 文件 | 当前行数 | 改造后行数 | 变化 |
|------|---------|----------|------|
| `OpenAICompatibleClient.java` | 960 | ~250 | **-710** |
| `OllamaClient.java` | 360 | ~250 | **-110** |
| `MemoryExtractorImpl.java` | 484 | ~380 | **-100** |
| `MultimodalPlannerService.java` | 194 | ~140 | **-50** |
| `TitleGenerationService.java` | 60 | ~30 | **-30** |
| `ContentOptimizationServiceImpl.java` | 270 | ~200 | **-70** |
| 新增 `OpenAiModelFactory.java` | 0 | +60 | +60 |
| 新增 `AiServiceFactory.java` | 0 | +50 | +50 |
| 新增 4 个 AI 接口 | 0 | +80 | +80 |
| **合计** | — | — | **-870 行** |

### 8.2 依赖变化

```xml
<!-- 新增 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.35.0</version>
</dependency>
```

无需删除现有依赖。`okhttp` / `jackson` 仍由其他模块使用，保留。

### 8.3 参考链接

- [LangChain4j 官方文档 - OpenAI 集成](https://docs.langchain4j.dev/integrations/language-models/open-ai)
- [LangChain4j 官方文档 - AiServices](https://docs.langchain4j.dev/tutorials/ai-services)
- [LangChain4j 官方文档 - 结构化输出](https://docs.langchain4j.dev/tutorials/structured-outputs)
- [LangChain4j 0.35.0 GitHub](https://github.com/langchain4j/langchain4j/releases/tag/0.35.0)
