# 18. 数据流：从用户发送到流式展示的完整链路

> 生成日期：2026-06-27 | 分支：main

---

## 一、总体时序图

```
用户点击发送 ──────────────────────────────────────────────────────────────► 消息展示完成

┌──────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Browser │     │   Vite Dev   │     │  Spring Boot │     │  AI Provider │
│ (React)  │     │  (Proxy)     │     │   (Tomcat)   │     │ (Ollama/API) │
└────┬─────┘     └──────┬───────┘     └──────┬───────┘     └──────┬───────┘
     │                   │                   │                    │
     │ 1. 乐观更新UI      │                   │                    │
     │    + 创建占位消息   │                   │                    │
     │                   │                   │                    │
     │ 2. POST /api/chat/stream              │                    │
     │    Accept: text/event-stream          │                    │
     │───────────────────┼──────────────────►│                    │
     │                   │                   │                    │
     │                   │                   │ 3. 同步前置         │
     │                   │                   │  • 对话管理         │
     │                   │                   │  • 记忆检索         │
     │                   │                   │  • 语言偏好         │
     │                   │                   │                    │
     │                   │                   │ 4. 异步执行         │
     │                   │                   │  executorService    │
     │                   │                   │  .execute(() -> {   │
     │                   │                   │                    │
     │                   │                   │ 5. POST stream req  │
     │                   │                   │───────────────────►│
     │                   │                   │                    │
     │                   │                   │ 6. token 1          │
     │                   │                   │◄───────────────────│
     │                   │                   │                    │
     │                   │                   │ 7. SSE event:msg   │
     │  8. SSE: message  │◄──────────────────│  {"content":"你"}   │
     │  {"content":"你"}  │                   │                    │
     │◄──────────────────│                   │                    │
     │                   │                   │                    │
     │ 9. React setState │                   │                    │
     │    逐字追加渲染     │                   │                    │
     │                   │                   │                    │
     │                   │                   │ 10. token 2         │
     │                   │                   │◄───────────────────│
     │                   │                   │                    │
     │                   │                   │ 11. SSE event:msg  │
     │  12. SSE: message │◄──────────────────│  {"content":"好"}   │
     │  {"content":"好"}  │                   │                    │
     │◄──────────────────│                   │                    │
     │                   │                   │                    │
     │      ... 循环 ...  │                   │    ... 循环 ...    │
     │                   │                   │                    │
     │                   │                   │ 13. stream end     │
     │                   │                   │◄───────────────────│
     │                   │                   │                    │
     │                   │                   │ 14. 后处理         │
     │                   │                   │  • 更新短期记忆     │
     │                   │                   │  • 持久化消息       │
     │                   │                   │  • 异步记忆提取     │
     │                   │                   │  • 标题生成         │
     │                   │                   │                    │
     │                   │                   │ 15. SSE event:done │
     │  16. SSE: done    │◄──────────────────│  {"messageId":".."} │
     │  {"messageId":"."} │                   │                    │
     │◄──────────────────│                   │                    │
     │                   │                   │                    │
     │ 17. 替换临时ID     │                   │                    │
     │     + 标题更新(可选)│                   │                    │
     │     + 标记新回复    │                   │                    │
     ▼                   ▼                   ▼                    ▼
```

---

## 二、前端阶段详解

### 2.1 用户点击发送 (InputArea → ChatContext)

**文件：** `frontend/src/components/chat/InputArea/index.tsx` → `frontend/src/context/ChatContext.tsx`

```
InputArea.onSubmit()
  │
  ├── 读取输入框内容 + 图片列表 + 联网搜索开关
  │
  └── 调用 chatContext.sendMessage(content, imageUrls, webSearch)
```

### 2.2 sendMessage 逻辑 (ChatContext.sendMessage)

```
sendMessage(content, imageUrls, webSearch)
  │
  ├── 乐观对话创建（若无活跃对话）
  │     ├── POST /api/conversations → 获取新 conversationId
  │     └── dispatch ADD_CONVERSATION + SET_ACTIVE_CONVERSATION
  │
  ├── 乐观用户消息
  │     ├── 生成临时 UUID: crypto.randomUUID()
  │     ├── dispatch ADD_MESSAGE { role:'user', content, images }
  │     └── dispatch SCROLL_TO_BOTTOM
  │
  ├── 乐观 AI 占位消息 (空 content)
  │     ├── 生成临时 UUID: crypto.randomUUID()
  │     └── dispatch ADD_MESSAGE { role:'assistant', content:'', id: tempMessageId }
  │
  ├── 构建 ChatRequest
  │     {
  │       conversationId,
  │       message: content || '分析图片',
  │       model: currentModel,
  │       imageUrls,
  │       userId: 'default',
  │       webSearch
  │     }
  │
  ├── 创建 AbortController → 存入 abortControllersRef
  │
  └── 调用 chat.stream(request, onMessage, onComplete, onError, controller, onSearchResults)
```

### 2.3 SSE 解析 (requestSSE)

**文件：** `frontend/src/api/client.ts`

```
requestSSE(endpoint, options, onMessage, onComplete, onError, controller, onSearchResults)
  │
  ├── fetch(BASE_URL + '/chat/stream', {
  │     method: 'POST',
  │     headers: {
  │       Accept: 'text/event-stream',
  │       'Cache-Control': 'no-cache',
  │       Connection: 'keep-alive',
  │       Authorization: 'Bearer {token}'   // 如果有
  │     },
  │     body: JSON.stringify(requestData),
  │     signal: abortController.signal,      // 支持中止
  │     credentials: 'same-origin'
  │   })
  │
  ├── 超时保护: setTimeout(60000) → abort
  │
  ├── 获取 ReadableStream reader
  │     └── reader = response.body.getReader()
  │
  ├── 流式解码循环:
  │     while (true) {
  │       { done, value } = await reader.read()
  │       buffer += decoder.decode(value, { stream: true })
  │       
  │       // 按 \n\n 分隔 SSE 事件块
  │       while (buffer 包含完整事件块) {
  │         提取一个事件块 (event:...\ndata:...\n\n)
  │         解析 event: 字段 → eventType
  │         解析 data: 字段 → JSON.parse(data)
  │         
  │         switch (eventType) {
  │           case 'message':
  │             onMessage(parsedData.content)
  │           case 'done':
  │             onComplete(parsedData.messageId, parsedData.title)
  │           case 'search_results':
  │             onSearchResults(parsedData)
  │         }
  │       }
  │     }
  │
  └── 错误/中断处理
        ├── AbortError → 静默（用户主动停止）
        └── 其他错误 → onError(apiError)
```

### 2.4 流式状态更新 (ChatContext reducer)

```
每个 SSE message 事件 → dispatch STREAM_CHUNK
  │
  ├── 追加 streamingState[convId].currentContent
  │
  └── 更新 messagesByConversation[convId] 中 tempMessageId 的 content
        └── React 自动触发 MessageBubble 重新渲染
        └── MarkdownRenderer 增量渲染流内容

SSE done 事件 → dispatch END_STREAMING
  │
  ├── 设置 streamingState[convId].isStreaming = false
  ├── UPDATE_MESSAGE_ID: tempMessageId → backendMessageId
  │     └── 将前端临时 UUID 替换为后端生成的消息 ID
  ├── 若有 title: UPDATE_CONVERSATION_TITLE
  └── 标记 SET_NEW_REPLY (侧边栏新消息绿点)
```

---

## 三、后端阶段详解

### 3.1 请求入口 (ChatController)

```
POST /api/chat/stream
  │
  └── ChatController.streamMessage(@RequestBody ChatRequest)
        └── return streamingService.streamResponse(request)
              (返回 SseEmitter，Spring MVC 自动处理 SSE 协议)
```

### 3.2 流式响应初始化 (StreamingService)

```
streamResponse(request)
  │
  ├── SseEmitter emitter = new SseEmitter(300000L)  // 5分钟超时
  │     ├── onCompletion → log
  │     ├── onTimeout    → emitter.complete()
  │     └── onError      → log
  │
  ├── 同步前置操作:
  │     ├── conversationId = getOrCreateConversationId(request)
  │     ├── updateShortTermMemoryWithUserMessage(conversationId, userMessage)
  │     │     └── ShortTermMemory.get(conversationId).add(UserMessage)
  │     │           └── Write-Through: 自动持久化到 Redis
  │     ├── saveUserMessage(conversationId, userMessage, imageUrls)
  │     │     └── INSERT INTO message (conversationId, content, role='user', images)
  │     ├── recallLongTermMemory(userId, userMessage, 5)
  │     │     └── VectorStoreWrapper.search(userId, userMessage, 5)
  │     │           → LongTermMemoryRepository.findAllById(ids)
  │     │           → 用户隔离 + 重要性过滤
  │     └── getLanguage(userId)
  │           └── UserProfileRepository → language 字段
  │
  └── executorService.execute(() -> { 异步LLM调用 })
```

### 3.3 LLM 调用分支

```
executorService.execute(() -> {
  │
  ├── 联网搜索（若开启）
  │     └── WebSearchService.search(userMessage)
  │           ├── Bing API (若有 key) → 解析 webPages.value[]
  │           └── HTML Scraping (降级) → 正则解析 b_algo 结果
  │     └── 发送 search_results SSE event
  │
  ├── 模型路由: modelConfigService.getConfigByModelId(model)
  │     │
  │     ├── 找到自定义配置 (非 null)
  │     │     │
  │     │     ├── 图像模型? → generateImage / generateImageSdWebui
  │     │     │     └── SSE message: "![Generated Image](url)"
  │     │     │
  │     │     └── 文本模型? → streamChatCompletion
  │     │           └── POST {baseUrl}/v1/chat/completions (stream=true)
  │     │
  │     └── 未找到 → Ollama 本地模型
  │           │
  │           ├── 获取短期记忆: getShortTermMemory(conversationId)
  │           ├── 组装 Prompt: assembleMessages(shortTerm, longTerm, userMsg, lang, search)
  │           └── 流式生成: streamGenerate / streamGenerateWithImages
  │                 └── POST {ollamaUrl}/api/generate (stream=true)
  │
  └── 流式回调循环:
        每次收到 token:
          ├── fullResponse.append(chunk)
          └── emitter.send(SseEmitter.event()
                .name("message")
                .data("{\"content\": \"" + escapeJson(chunk) + "\"}"))
```

### 3.4 响应完成后处理 (finalizeResponse)

```
finalizeResponse(conversationId, content, aiMessageId, userId, model, userMessage, emitter)
  │
  ├── 更新短期记忆
  │     └── ShortTermMemory.updateMemoryWithAiMessage(conversationId, content)
  │           └── Write-Through → Redis
  │
  ├── 持久化 AI 消息
  │     └── MessagePersistenceService.saveAiMessage(conversationId, aiMessageId, content)
  │           └── INSERT INTO message (id, conversationId, content, role='assistant')
  │
  ├── 异步记忆提取
  │     └── executorService.execute(() -> autoMemoryExtractor.tryExtract(conversationId, userId))
  │           └── 计数器 +1 → 达到阈值 (5) → LLM 提取 → 过滤 → 保存
  │
  ├── 标题生成 (仅当标题为 "新对话")
  │     └── TitleGenerationService.generateTitle(userMessage, aiResponse, model)
  │           └── 调用 LLM 生成 3-15 字标题 → 更新 Conversation.title
  │
  └── 发送 done 事件
        └── emitter.send(SseEmitter.event()
              .name("done")
              .data("{\"messageId\": \"...\", \"title\": \"...\"}"))
        └── emitter.complete()
```

---

## 四、关键数据结构流

### 4.1 请求体

```json
// 前端 → 后端
POST /api/chat/stream
{
  "conversationId": "uuid-xxx",
  "message": "你好，请帮我...",
  "model": "llama3",
  "imageUrls": ["http://localhost:8080/api/images/photo.png"],
  "userId": "default",
  "webSearch": false
}
```

### 4.2 SSE 事件流

```
// event: message — 每收到一个 token
event: message
data: {"content": "你"}

event: message
data: {"content": "好"}

event: message
data: {"content": "，"}

// event: search_results — 联网搜索（仅当开启，在 message 之前）
event: search_results
data: {"query":"...", "snippets":[...], "timestamp":..., "status":"success"}

// event: done — 流结束
event: done
data: {"messageId": "uuid-xxx", "title": "AI生成的对话标题"}
```

### 4.3 前端状态变更

```
初始状态:
  messagesByConversation["conv-1"] = [
    { id: "msg-1", role: "user", content: "你好" },
    { id: "msg-2", role: "assistant", content: "你好！有什么可以帮你的？" }
  ]

用户发送后 (乐观更新):
  messagesByConversation["conv-1"] = [
    ...existing,
    { id: "temp-uuid-u1", role: "user", content: "介绍一下Java" },
    { id: "temp-uuid-a1", role: "assistant", content: "" }      ← 空占位
  ]
  streamingStates["conv-1"] = { isStreaming: true, currentContent: "", messageId: null }

流式更新中:
  messagesByConversation["conv-1"][最后].content = "Java是一"    ← 逐 token 更新
  streamingStates["conv-1"].currentContent = "Java是一"

流式完成:
  messagesByConversation["conv-1"][最后] = 
    { id: "backend-uuid", role: "assistant", content: "Java是一种..." }
  streamingStates["conv-1"] = { isStreaming: false, currentContent: "", messageId: "backend-uuid" }
  newReplies["conv-1"] = true                                     ← 侧边栏绿点
```

---

## 五、中止/停止流程

```
用户点击停止按钮
  │
  └── ChatContext.stopStreaming(conversationId)
        ├── abortControllersRef[conversationId].abort()
        │     └── AbortController.signal → fetch() 被中断
        │     └── requestSSE 捕获 AbortError → 静默处理
        │
        └── dispatch END_STREAMING { messageId: 'stopped' }
              └── streamingState.isStreaming = false
              └── 消息 content 保留当前已接收的部分
```

---

## 六、网络代理

```
Browser :5173  →  Vite Proxy  →  Spring Boot :8080

Vite 配置 (vite.config.ts):
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }

前端请求:
  fetch('/api/chat/stream', ...)
  → Vite 代理转发到 http://localhost:8080/api/chat/stream

环境变量 (.env):
  VITE_API_URL=http://localhost:8080/api   (生产环境直连)
  
API 层 (client.ts):
  const BASE_URL = import.meta.env.VITE_API_URL || '/api'
```

---

## 七、错误处理链路

```
场景 A: 网络断开
  fetch() → NetworkError
  → requestSSE catch → onError(apiError)
  → ChatContext: dispatch SET_ERROR + END_STREAMING
  → 前端显示 toast 错误提示

场景 B: LLM 服务不可用
  OllamaClient → Connection refused
  → @Retry(3次) → 仍失败 → RuntimeException
  → StreamingService catch → emitter.completeWithError(e)
  → SSE 连接断开 → requestSSE 捕获 → onError

场景 C: 用户手动停止
  abortController.abort()
  → fetch signal → AbortError
  → requestSSE catch → error.name === 'AbortError' → 静默

场景 D: SSE 超时 (5分钟)
  Spring: SseEmitter(300000L).onTimeout → emitter.complete()
  → 流正常结束但可能内容不完整

场景 E: 客户端超时 (60秒)
  requestSSE: setTimeout(60000) → abortController.abort()
  → 强制中止
```

---

## 八、对话总结数据流（特殊路径）

```
用户选中 AI 消息 → 点击"总结为笔记"
  │
  ├── 前端: startSummarizing(conversationId, messageId)
  │
  └── 调用 chat.summarize(content, model)
        │
        ├── POST /api/chat/summarize (同步，timeout=120s, retries=0)
        │
        ├── 后端: ChatController.summarize()
        │     ├── 构建总结专用 System Prompt (含语言指令)
        │     ├── 模型路由 (同对话流)
        │     └── 返回 { title, summary }
        │
        └── 前端处理:
              ├── 打开 NoteTodoPanel
              ├── 预填 NoteForm: title + summary (Markdown)
              └── endSummarizing(conversationId)
```

## 九、重新生成数据流（特殊路径）

```
用户选中旧 AI 消息 → 点击"重新生成"
  │
  ├── 前端: START_REGENERATING
  │     ├── 保存原始 content (用于失败回滚)
  │     └── 清空消息 content (视觉反馈)
  │
  └── 调用 chat.regenerate(conversationId, messageId, userId, model)
        │
        ├── POST /api/chat/regenerate (同步)
        │
        ├── 后端: ChatService.regenerateResponse()
        │     ├── 查找前置用户消息
        │     ├── 事务: 删除该 AI 消息之后的所有消息
        │     ├── 清除短期记忆缓存
        │     ├── 重新执行完整对话流程 (记忆→Prompt→LLM)
        │     ├── 覆盖原消息 content + 更新时间戳
        │     └── 返回新 content
        │
        └── 前端:
              ├── 成功: UPDATE_MESSAGE { 新 content }
              └── 失败: UPDATE_MESSAGE { 原始 content } + toast 错误
```
