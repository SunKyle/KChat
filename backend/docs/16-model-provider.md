# 16. 模型提供商适配层设计

> 生成日期：2026-06-27 | 分支：main

---

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      StreamingService / ChatService              │
│                         (业务调度层)                               │
├─────────────────────────────────────────────────────────────────┤
│                       ModelConfigService                         │
│                     (模型路由: 名称解析 → 配置查表)                  │
├──────────────────────┬──────────────────────────────────────────┤
│    OllamaClient       │        OpenAICompatibleClient            │
│   (本地模型适配器)     │         (云端模型适配器)                    │
│                       │                                          │
│  依赖: langchain4j    │  依赖: OkHttp 4.x                        │
│        OllamaChatModel│        Jackson ObjectMapper              │
│        HttpURLConn.   │        SseEmitter (Spring MVC)          │
│                       │                                          │
│  协议: /api/generate   │  协议: /v1/chat/completions (OpenAI格式)  │
│        /api/tags      │        /v1/images/generations (+SD WebUI) │
├──────────────────────┼──────────────────────────────────────────┤
│ 弹性: Resilience4j    │ 弹性: 无（直接 OkHttp 调用）                │
│ 安全: InputValidator  │ 安全: 无内建过滤                           │
│       SensitiveFilter │                                          │
└──────────────────────┴──────────────────────────────────────────┘
```

### 1.1 模型路由逻辑

```
用户选择模型 (如 "my-openai:gpt-4o")
    │
    ▼
ModelConfigService.getConfigByModelId("my-openai:gpt-4o")
    │
    ├── 遍历所有 enabled 的 ModelConfig
    ├── 检查 modelId 是否以 "{config.name}:" 开头
    │
    ├── 找到匹配 → 返回 ModelConfig {name, modelId, baseUrl, apiKey, type, category}
    │               解析 actualModelId = "gpt-4o" (去掉 "my-openai:" 前缀)
    │               路由到 OpenAICompatibleClient
    │
    └── 未找到 → 返回 null
                 路由到 OllamaClient (本地模型)
```

### 1.2 模型列表聚合

```
ModelConfigService.listModels(category?)
    │
    ├── [TEXT 或 null] OllamaClient.listModels()
    │       └── GET /api/tags → 解析 JSON → 提取 "models[].name"
    │
    ├── 查询所有 enabled 的 ModelConfig (按 category 过滤)
    │
    └── 合并: ollamaModels + configs 格式化为 "{name}:{modelId}"
```

---

## 二、OllamaClient — 本地模型适配器

### 2.1 职责

调用本地 Ollama 服务，支持同步生成、流式生成、多模态（图片输入）三种模式。

### 2.2 依赖

| 组件 | 用途 |
|------|------|
| `langchain4j OllamaChatModel` | 同步对话（带模型缓存 `ConcurrentHashMap`） |
| `HttpStreamingTemplate` | 流式 HTTP 请求模板（NDJSON 逐行解析） |
| `ObjectMapper` | JSON 请求体构建 / 响应解析 |

### 2.3 同步生成 (`generate`)

```
generate(messages, model)
  │
  ├── 模型实例缓存: modelCache.computeIfAbsent(model, ...)
  │       └── OllamaChatModel.builder()
  │           .baseUrl(ollamaConfig.getBaseUrl())    // http://localhost:11434
  │           .modelName(model)
  │           .timeout(Duration.ofMinutes(2))
  │           .build()
  │
  ├── 调用: modelInstance.generate(messages)
  │       └── 返回: Response<AiMessage>
  │
  ├── 成功: response.content().text()
  └── 失败: modelCache.remove(model) + throw
```

**参数映射（langchain4j 内部处理）：**
- `ChatMessage` 列表 → Ollama `/api/chat` 格式（`messages` 数组）
- 同步模式，等完整响应

### 2.4 流式生成 (`streamGenerate`)

```
streamGenerate(messages, callback, model)
  │
  ├── 安全过滤: sanitizeMessages(messages)
  │       └── SensitiveFilter.sanitize() 逐条脱敏
  │
  ├── Prompt 构建: buildPrompt(messages)
  │       └── SystemMessage → 前缀 "System: ..."
  │       └── UserMessage    → 前缀 "User: ..."
  │       └── AiMessage      → 前缀 "Assistant: ..."
  │       └── 末尾追加 "Assistant: " 引导生成
  │
  ├── 构建请求体 (ObjectNode):
  │       { "model": "llama3", "prompt": "...", "stream": true }
  │
  ├── 发送: httpStreamingTemplate.streamJsonResponse(...)
  │       └── POST {baseUrl}/api/generate
  │       └── NDJSON 逐行读取 → 提取 "response" 字段 → callback.accept(chunk)
  │
  └── 异常: 包装为 RuntimeException (消息: "AI model connection timeout or service unavailable")
```

**流式生成（多模态）(`streamGenerateWithImages`)：**
- 与普通流式相同，但额外处理图片：
  - 遍历 `imageUrls` → `imageUrlToBase64(url)` → 转为 Base64 字符串
  - 请求体增加 `"images": ["base64...", ...]` 字段
  - 注意：`imageUrlToBase64` 依赖 `java.net.URL` + `HttpURLConnection`，仅支持 HTTP(S) URL，不支持本地文件路径

### 2.5 弹性保护

```java
@Retry(name = "ollamaRetry")        // max-attempts=3, wait-duration=2s
@CircuitBreaker(name = "ollamaCB")   // sliding-window=10, failure-rate=50%, wait-in-open=10s
```

| 参数 | 值 | 说明 |
|------|-----|------|
| 重试次数 | 3 | 最多尝试 3 次 |
| 重试间隔 | 2s | 固定间隔 |
| 重试异常 | IOException, SocketTimeoutException, RuntimeException | |
| 熔断窗口 | 10 次调用 | 滑动窗口 |
| 熔断阈值 | 50% 失败率 | |
| 半开状态 | 10s 后尝试 3 次 | |
| 模型缓存清理 | 失败后 remove | 防止缓存损坏实例 |

### 2.6 模型列表查询

```java
listModels()
  └── GET {baseUrl}/api/tags
       └── 解析 JSON: root.get("models")[i].get("name")
       └── 返回 List<String>
```

---

## 三、OpenAICompatibleClient — 云端模型适配器

### 3.1 职责

通过 OpenAI 兼容协议调用任意云端模型（OpenAI、Anthropic、Google、Azure、自定义服务），支持：
- 同步聊天补全
- 流式聊天补全（SSE）
- 图像生成（DALL-E 风格 + Stable Diffusion WebUI）

### 3.2 依赖

| 组件 | 用途 |
|------|------|
| OkHttp 4.x | 所有 HTTP 调用（同步 + 异步回调） |
| Jackson ObjectMapper | JSON 请求构建 / 响应解析 |
| Spring SseEmitter | 流式响应推送 |
| Java Base64 / Files | 图片 base64 编码 |

### 3.3 同步聊天补全 (`chatCompletion`)

```
chatCompletion(modelId, baseUrl, apiKey, systemPrompt, userContent)
  │
  ├── 构建请求体:
  │     {
  │       "model": "gpt-4o",
  │       "messages": [
  │         {"role": "system", "content": "You are a helpful assistant..."},
  │         {"role": "user", "content": "..."}
  │       ],
  │       "stream": false,
  │       "max_tokens": 4096,
  │       "temperature": 0.3
  │     }
  │
  ├── POST {baseUrl}/v1/chat/completions
  │       (如果 baseUrl 已含完整路径则直接使用)
  │
  ├── Headers:
  │       Authorization: Bearer {apiKey}
  │       Content-Type: application/json
  │
  ├── 超时: connect=30s, read=2min, write=30s
  │
  └── 解析: node.get("choices")[0].get("message").get("content")
```

**使用场景：**
- `ChatService.regenerateResponse()` — 重新生成时优先使用自定义模型
- `ChatController.summarize()` — 对话总结
- `TitleGenerationService.generateTitle()` — 标题生成

**关键参数映射：**

| 业务参数 | API 字段 | 默认值 |
|---------|---------|--------|
| modelId | `model` | —（必传） |
| systemPrompt | `messages[0]` (role=system) | — |
| userContent | `messages[1]` (role=user) | — |
| — | `stream` | `false`（同步） |
| — | `max_tokens` | `4096` |
| — | `temperature` | `0.3`（总结/标题场景偏保守） |

### 3.4 流式聊天补全 (`streamChatCompletion`)

```
streamChatCompletion(modelId, baseUrl, apiKey, prompt, imageUrls, emitter, onChunk, onComplete)
  │
  ├── 构建请求体:
  │     {
  │       "model": "gpt-4o",
  │       "messages": [{
  │         "role": "user",
  │         "content": [{"type": "text", "text": "..."}]
  │       }],
  │       "stream": true,           // ← 关键差异
  │       "max_tokens": 4096,
  │       "temperature": 0.7        // ← 对话场景偏高
  │     }
  │
  ├── Headers (额外):
  │       Accept: text/event-stream
  │       Cache-Control: no-cache
  │       Connection: keep-alive
  │
  ├── 异步执行: call.enqueue(Callback)
  │
  ├── 流式解析:
  │     byte[] buffer[8192] → 按行读取
  │     "data: " 开头 → 提取 JSON
  │     "[DONE]" → 触发 onComplete + emitter.complete()
  │     非 "data:" 行 → 日志记录（跳过）
  │
  ├── 内容提取: extractContent(jsonLine)
  │     └── node.get("choices")[0].get("delta").get("content")
  │
  ├── 每次提取: onChunk.accept(content) + emitter.send("message", json)
  │
  └── HTML 检测: 如果返回 HTML → emitter.completeWithError("请检查baseUrl配置")
```

**流式响应事件：**

| SSE 事件名 | data 格式 | 触发时机 |
|-----------|-----------|---------|
| `message` | `{"content": "..."}` | 每接收到一个 token |
| `done` | `{"messageId": "...", "title": "..."}` | 流结束 |

**超时配置：**
- connect: 30s
- read: 5min（覆盖长文本生成）
- write: 30s

### 3.5 图像模型检测

```java
isImageModel(modelId)
  → modelId 包含 "dall-e" / "image" / "sdxl" / "stable-diffusion"

isStableDiffusionModel(modelId)
  → modelId 包含 "stable-diffusion" / "sdxl" / "sd-"
```

### 3.6 图像生成 (DALL-E 风格)

```
generateImage(modelId, baseUrl, apiKey, prompt, imageUrls, emitter, onComplete)
  │
  ├── 请求体:
  │     {
  │       "model": "dall-e-3",
  │       "prompt": "...",
  │       "n": 1,
  │       "response_format": "url",
  │       "image": "base64..."  // 仅当 imageUrls 非空 (img2img)
  │     }
  │
  ├── POST {baseUrl}/v1/images/generations
  │
  ├── 异步回调:
  │     成功: 解析 node.get("data")[0].get("url" 或 "b64_json")
  │            → Markdown 格式: "![Generated Image](url)"
  │            → emitter.send("message", json)
  │     失败: emitter.completeWithError(...)
  │
  └── 超时: connect=30s, read=60s, write=30s
```

### 3.7 图像生成 (Stable Diffusion WebUI)

```
generateImageSdWebui(modelId, baseUrl, apiKey, prompt, imageUrls, emitter, onComplete)
  │
  ├── 请求体:
  │     {
  │       "prompt": "...",
  │       "negative_prompt": "",
  │       "steps": 20,
  │       "cfg_scale": 7,
  │       "init_images": ["base64..."]  // 仅 img2img
  │       "denoising_strength": 0.75
  │     }
  │
  ├── 路由:
  │     hasReferenceImage → POST /sdapi/v1/img2img
  │     无参考图          → POST /sdapi/v1/txt2img
  │
  ├── 解析: node.get("images")[0] → "data:image/png;base64,..."
  │
  └── 超时: connect=30s, read=120s, write=30s
```

### 3.8 图片 Base64 获取

`fetchImageAsBase64(imageUrl)` 的优先级链：

```
1. data: URI  → 直接返回
2. 本地文件   → Paths.get("uploads/images/{filename}") → 读取 → Base64
3. 本地 URL   → localhost/127.0.0.1 → HttpURLConnection → Base64
4. 远程 URL   → HttpURLConnection → Base64
5. 全部失败   → return null
```

---

## 四、HttpStreamingTemplate — 流式传输基础组件

```java
streamJsonResponse(url, requestBody, callback)
  // 用于 Ollama 流式调用 (NDJSON)
  └── POST → BufferedReader 逐行读取
       └── 每行尝试 JSON 解析 → 提取 "response" 字段 → callback

streamSseResponse(url, requestBody, authHeader, callback)
  // 通用 SSE 流式读取
  └── POST → BufferedReader 逐行读取
       └── "data: " 行 → 提取 data → callback
       └── "data: [DONE]" → 结束
```

---

## 五、流式处理的线程模型

```
HTTP 请求线程
  │
  ├── 同步前置处理（获取对话/记忆/语言偏好）
  │
  └── executorService.execute(() -> {    ← 切换到异步线程池
        ├── 调用 LLM (阻塞等待流式响应)
        ├── 逐 token 推送 SseEmitter.send()
        ├── 完成后 emitter.complete()
        └── 异步触发记忆提取
      })
  
  异步线程池配置 (AsyncConfig):
    - 核心线程: 2
    - 最大线程: 10
    - 队列容量: 100 (LinkedBlockingQueue)
    - 拒绝策略: CallerRunsPolicy
    - 线程命名: "streaming-N"
    - 守护线程: true
```

---

## 六、错误处理策略对比

| 场景 | OllamaClient | OpenAICompatibleClient |
|------|-------------|----------------------|
| 连接超时 | Retry(3次) → RuntimeException | OkHttp 超时 → emitter.completeWithError |
| 服务不可用 | CircuitBreaker 熔断 | 直接抛异常 |
| 流式中断 | emitter.completeWithError | emitter.completeWithError |
| JSON 解析失败 | 跳过该行继续 | 跳过该行继续 |
| HTML 响应 | — | 检测并报错 "请检查 baseUrl" |
| 模型缓存损坏 | remove + 重新创建 | — |
| 安全违规 | 抛出 IllegalArgumentException | —（调用方负责前置过滤） |

---

## 七、URL 构建策略

`OpenAICompatibleClient.buildFullUrl(baseUrl, endpoint)`:

```
规则 1: baseUrl 已含 "/v1/chat/completions" 或 "/v1/images/generations"
       → 直接返回 baseUrl（不拼接）

规则 2: baseUrl 以 "/" 结尾 → 去掉末尾 "/"，然后 + endpoint
规则 3: 其他 → 直接 + endpoint

示例:
  "https://api.openai.com" + "/v1/chat/completions"
  → "https://api.openai.com/v1/chat/completions"

  "http://localhost:8080/v1/chat/completions"
  → "http://localhost:8080/v1/chat/completions" (不拼接)
```

---

## 八、扩展新模型提供商

基于当前架构，添加新模型提供商需要：

1. **定义类型枚举**：在 `ModelConfig.ModelType` 中添加新枚举值
2. **创建客户端适配器**：实现与目标 API 的 HTTP 通信（参考 `OpenAICompatibleClient`）
3. **注册路由逻辑**：在 `StreamingService` 和 `ChatService` 的模型判断分支中添加新路由
4. **（可选）添加弹性保护**：配置 Resilience4j 重试/熔断
5. **前端适配**：在 `types/index.ts` 的 `PROVIDERS` 数组中添加提供商信息

当前已知限制：
- 所有非 Ollama 模型目前**统一通过 OpenAI 兼容协议**调用（即假设所有云端 API 都遵循 `/v1/chat/completions` 格式）。Anthropic、Google、Azure 的实际 API 协议差异未做适配。
- 非 OpenAI 模型的图像生成路径未实现（仅 DALL-E 和 SD WebUI）。
