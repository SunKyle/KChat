# KChat 后端项目架构总览

> 生成日期：2026-06-27 | 分支：main

---

## 一、技术栈总览

| 层次 | 技术 | 版本/说明 |
|------|------|-----------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.0 |
| 构建 | Maven | `pom.xml` |
| 数据库 | MySQL (HikariCP) | `jdbc:mysql://localhost:3306/kchatdb`，H2 为运行时降级备选 |
| 缓存/向量 | Redis | `localhost:6379`，存储短期记忆 L2 缓存 + 向量嵌入索引 |
| LLM 集成 | langchain4j | 0.35.0（核心 + Ollama + Embeddings + Redis） |
| 外部 HTTP | OkHttp | OpenAI 兼容 API 调用 |
| 弹性 | Resilience4j | 重试 + 熔断器（Ollama 调用保护） |
| 工具 | Lombok | 1.18.30 |
| 验证 | Jakarta Validation | `spring-boot-starter-validation` |

---

## 二、包结构与分层

```
backend/src/main/java/com/example/app/
├── Application.java              # Spring Boot 入口
├── aspect/
│   └── RateLimitAspect.java      # Redis 滑动窗口限流切面
├── auth/                         # （预留，当前为空）
├── client/                       # 外部 AI 服务客户端
│   ├── OllamaClient.java         # 本地 Ollama 模型调用
│   ├── OpenAICompatibleClient.java # OpenAI 兼容 API（34KB，最大文件）
│   └── HttpStreamingTemplate.java # HTTP 流式请求通用模板
├── config/                       # 配置类
│   ├── WebConfig.java            # CORS + 内容协商
│   ├── RedisConfig.java          # RedisTemplate 序列化配置
│   ├── AsyncConfig.java          # 流式响应线程池（核心2/最大10/队列100）
│   ├── OllamaConfig.java         # Ollama 连接 + ChatLanguageModel Bean
│   ├── StreamingConfig.java      # OllamaStreamingChatModel Bean
│   ├── VectorStoreConfig.java    # 向量嵌入维度/相似度阈值 + EmbeddingModel Bean
│   ├── MemoryExtractorConfig.java # 记忆提取触发条件配置
│   └── WebSearchConfig.java      # 网络搜索配置
├── controller/                   # REST API 控制器
│   ├── ChatController.java       # 对话管理 + 消息发送 + 模型列表 + 总结/重新生成
│   ├── MemoryController.java     # 长期记忆 CRUD + 语义召回
│   ├── ModelConfigController.java # 模型配置管理
│   ├── UserController.java       # 用户资料/偏好/隐私/API Key
│   ├── UserSettingController.java # 用户设置
│   ├── ImageController.java      # 图片上传/下载/删除
│   ├── NoteController.java       # 笔记管理
│   ├── TodoController.java       # 待办事项管理
│   ├── ContentOptimizationController.java # 内容优化
│   ├── PromptTemplateController.java # Prompt 模板管理
│   └── PromptMetricsController.java # Prompt 构建指标查询
├── dto/                          # 数据传输对象（30+ 个 DTO）
│   ├── ChatRequest.java / ChatResponse.java
│   ├── MemoryDTO.java / MemoryRecallRequest.java
│   ├── ConversationDTO.java / MessageDTO.java
│   ├── ModelConfigDTO.java
│   ├── UserProfileDTO.java / UserSettingDTO.java
│   ├── SummarizeRequest/Response, RegenerateRequest/Response
│   ├── WebSearchResult.java
│   └── NoteDTO, TodoDTO, APIKeyDTO 等
├── entity/                       # JPA 实体（10个）
│   ├── Conversation.java         # 对话（id/UUID, title, modelId, pinned, tokenUsage）
│   ├── Message.java              # 消息（conversationId, content, role, images, imageUrls, tokenCount）
│   ├── LongTermMemory.java       # 长期记忆（userId, content, type枚举, importance, embedding, metadata）
│   ├── ModelConfig.java          # 模型配置（name, modelId, baseUrl, apiKey, type枚举, category枚举）
│   ├── UserProfile.java          # 用户资料（nickname, avatar, email, theme, language, 通知/隐私设置）
│   ├── UserSetting.java          # 用户设置（theme, memoryEnable, defaultModel, contextSize, autoTitle）
│   ├── Note.java                 # 笔记（title, content, category, tags/JSON, pinned, memoryId）
│   ├── APIKey.java               # API 密钥
│   ├── PromptTemplate.java       # Prompt 模板（name, content, category, version, active）
│   ├── PromptMetrics.java        # Prompt 构建指标（tokenCount, memoryCount, buildDurationMs, truncation*）
│   └── UserDevice.java           # 用户设备
├── exception/
│   ├── GlobalExceptionHandler.java # 全局异常处理（@RestControllerAdvice）
│   └── ErrorResponse.java        # 统一错误响应体
├── memory/                       # 记忆系统核心组件
│   ├── ShortTermMemory.java      # 短期记忆 L1(ConcurrentHashMap) + L2(Redis, 24h过期) 双层缓存
│   └── VectorStoreWrapper.java   # Redis 向量索引（余弦相似度检索，384维）
├── repository/                   # Spring Data JPA 数据访问层（12个仓库接口）
│   ├── ConversationRepository, MessageRepository
│   ├── LongTermMemoryRepository, ModelConfigRepository
│   ├── UserProfileRepository, UserSettingRepository, UserDeviceRepository, APIKeyRepository
│   ├── NoteRepository, TodoRepository
│   └── PromptTemplateRepository, PromptMetricsRepository
├── security/                     # 安全组件
│   ├── InputValidator.java       # 输入长度校验 + Prompt 注入检测（11种危险模式）
│   └── SensitiveFilter.java      # 敏感信息脱敏（手机号/身份证/邮箱/银行卡/车牌/IP/微信/QQ等12种）
├── service/                      # 业务逻辑层
│   ├── ChatService.java          # 同步聊天完整流程编排
│   ├── StreamingService.java     # SSE 流式响应处理
│   ├── ChatWorkflowService.java  # 对话生命周期 + 记忆召回 + 消息组装（门面模式）
│   ├── ConversationService.java  # 对话 CRUD
│   ├── MessagePersistenceService.java # 消息持久化
│   ├── LongTermMemoryService.java # 长期记忆管理（向量索引 + 数据库双写）
│   ├── ShortTermMemoryService.java # 短期记忆代理层
│   ├── ModelConfigService.java   # 模型配置管理 + 模型列表聚合
│   ├── AutoMemoryExtractor.java  # 自动记忆提取（阈值触发 + 定时扫描）
│   ├── TitleGenerationService.java # AI 标题自动生成
│   ├── UserProfileService.java   # 用户资料管理
│   ├── UserSettingService.java   # 用户设置管理
│   ├── ImageService.java         # 图片文件管理
│   ├── NoteService.java          # 笔记 CRUD
│   ├── TodoService.java          # 待办 CRUD
│   ├── PromptTemplateService.java # Prompt 模板 CRUD + 版本管理 + 缓存
│   ├── PromptMetricsService.java # 构建指标记录
│   ├── CacheService.java         # 通用缓存服务
│   ├── MemoryExtractor.java      # 记忆提取接口
│   ├── MemoryRecaller.java       # 记忆召回接口
│   ├── WebSearchService.java     # 网络搜索接口
│   ├── ContentOptimizationService.java # 内容优化
│   └── impl/
│       ├── MemoryExtractorImpl.java   # LLM 驱动记忆提取（含降级规则提取）
│       └── MemoryRecallerImpl.java    # 语义记忆召回实现
└── util/
    ├── PromptAssembler.java      # Prompt 组装核心（安全过滤 + 模板注入 + 智能截断 + 指标记录）
    ├── TokenEstimator.java       # Token 估算接口
    ├── SimpleTokenEstimator.java # 简单 Token 估算实现
    ├── DefaultTokenEstimator.java # 默认 Token 估算实现
    ├── JsonUtils.java            # JSON 工具类
    └── PromptAssemblerTest.java  # Prompt 组装测试
```

---

## 三、核心架构设计

### 3.1 消息处理流程

```
用户消息 → ChatController
              ├── POST /api/chat (同步)      → ChatService.generateResponse()
              └── POST /api/chat/stream (SSE) → StreamingService.streamResponse()
```

**同步流程 (`ChatService.generateResponse`)：**
```
1. 获取/创建对话ID          → ConversationService
2. 检索短期记忆（对话历史）   → ShortTermMemoryService → ShortTermMemory (L1 → L2 Redis)
3. 召回长期记忆（语义检索）   → LongTermMemoryService.recall() → VectorStoreWrapper.search()
4. 网络搜索（可选）          → WebSearchService
5. 查询语言偏好              → UserProfileService
6. 组装 Prompt              → PromptAssembler.assemble()
7. 调用 LLM                 → OllamaClient.generate() 或 OpenAICompatibleClient.chatCompletion()
8. 更新短期记忆              → ShortTermMemoryService.updateMemory()
9. 持久化消息                → MessagePersistenceService.saveMessages()
10. 异步提取新记忆           → AutoMemoryExtractor.tryExtract()
11. 尝试生成标题             → TitleGenerationService
```

**流式流程 (`StreamingService.streamResponse`)：**
```
1. 创建 SseEmitter (5分钟超时)
2. 获取/创建对话ID
3. 更新短期记忆 + 保存用户消息（同步前置）
4. 召回长期记忆（同步前置）
5. 异步线程执行：
   a. 网络搜索（可选）
   b. 判断模型类型：自定义模型 → 判断是否为图像模型
      - 文本模型：OpenAICompatibleClient.streamChatCompletion()
      - 图像模型：OpenAICompatibleClient.generateImage() / generateImageSdWebui()
      - 本地 Ollama：OllamaClient.streamGenerate()
   c. 流式推送 SSE message 事件
   d. 完成后发送 SSE done 事件
   e. 异步提取记忆 + 尝试生成标题
```

### 3.2 LLM 客户端双模式

| 特性 | OllamaClient | OpenAICompatibleClient |
|------|-------------|----------------------|
| 协议 | langchain4j `OllamaChatModel` + 底层 HTTP 流式 | OkHttp + OpenAI 兼容 API |
| 模型 | 本地 Ollama 服务 (`/api/generate`, `/api/tags`) | 自定义 URL + API Key |
| 流式 | `streamGenerate()` / `streamGenerateWithImages()` | `streamChatCompletion()` |
| 图像 | 无直接图像生成，仅图片输入（base64） | `generateImage()` (DALL-E 兼容) + `generateImageSdWebui()` (Stable Diffusion) |
| 弹性 | `@Retry`(3次/2s间隔) + `@CircuitBreaker`(50%阈值/10s半开) | 无（直接 OkHttp） |
| 安全 | `InputValidator` + `SensitiveFilter` | 无内建过滤 |

### 3.3 记忆系统双通道

```
┌──────────────────────────────────────────────────────┐
│                    记忆系统架构                        │
├──────────────────┬───────────────────────────────────┤
│  短期记忆 (L1+L2) │  长期记忆 (向量检索)               │
├──────────────────┼───────────────────────────────────┤
│ L1: ConcurrentHashMap │ 存储: MySQL (long_term_memory表)  │
│     (进程内存)        │ 索引: Redis (向量嵌入 + Set索引)  │
│ L2: Redis             │ 检索: 余弦相似度 (384维向量)       │
│     (24h过期, JSON)   │ 过滤: 用户隔离 + 重要性阈值         │
│ 策略: Write-Through   │ 类型: PROFILE/PREFERENCE/PROJECT/  │
│       自动持久化       │       SKILL/TASK/KNOWLEDGE/       │
│                       │       RELATION/EVENT/RULE/FACT/    │
│                       │       EXPERIENCE (11种)            │
└──────────────────┴───────────────────────────────────┘
```

**短期记忆 (`ShortTermMemory`)：**
- 装饰器模式：`RedisBackedChatMemory` 包装 `MessageWindowChatMemory`（max 20条）
- Redis 不可用时自动降级为纯内存存储
- 对话 ID 作为隔离键

**长期记忆 (`LongTermMemoryService`)：**
- 保存时：数据库插入 + 向量索引写入（同一事务）
- 召回时：向量相似度检索 → 过滤非本用户 → 应用重要性阈值
- 注意：当前 `VectorStoreConfig` 的 `EmbeddingModel` 是**确定性哈希伪嵌入**（非真实语义嵌入），适合开发测试

### 3.4 自动记忆提取

```
AutoMemoryExtractor
├── 触发条件: 消息计数 ≥ messageThreshold (默认5条)
├── 计数管理: ConversationMessageCounter (内存计数器)
├── LLM 提取: MemoryExtractorImpl.extract()
│   ├── 主路径: LLM 结构化提取 (JSON → {summary, memories[]})
│   └── 降级路径: 规则匹配提取 (关键词模式识别)
├── 质量过滤: 置信度阈值 + 重要性阈值 + 去重
├── 保存: LongTermMemoryService.saveAll() (批量)
└── 定时扫描: @Scheduled(60s) 扫描空闲对话 (待实现)
```

### 3.5 Prompt 组装器

`PromptAssembler` 是消息组装的中心节点，7 步流程：

```
1. 安全过滤    → InputValidator (长度检查 + 注入检测)
2. 语言指令    → 根据用户语言偏好生成 "请使用中文回复。" 等指令
3. 长期记忆文本 → 按重要性降序格式化
4. 系统提示词  → 动态从 DB 加载模板 (PromptTemplateService)
               或使用硬编码降级模板 (FALLBACK_SYSTEM_PROMPT_TEMPLATE)
5. 对话历史    → 附加短期记忆消息
6. 用户输入    → 附加当前消息
7. 指标记录    → PromptMetricsService.recordMetrics()
```

特性：
- 支持占位符 `{language_clause}`, `{long_term_memory}` 替换
- Token 感知智能截断（优先保留 SystemMessage + 最近对话）
- 网络搜索结果注入（带当前时间戳）
- 降级组装 (fallback)：异常时回退到简单 SystemMessage + UserMessage

---

## 四、API 端点清单

### ChatController (`/api`)
| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/conversations` | 创建新对话 |
| GET | `/api/conversations` | 获取对话列表 |
| GET | `/api/conversations/{id}` | 获取对话详情（含消息） |
| PUT | `/api/conversations/{id}` | 更新对话（标题/置顶） |
| DELETE | `/api/conversations/{id}` | 删除对话（级联删除消息+短期记忆） |
| POST | `/api/chat` | 发送消息（同步，返回完整响应） |
| POST | `/api/chat/stream` | 发送消息（SSE 流式） |
| POST | `/api/chat/summarize` | 总结 AI 回复为 Markdown 笔记 |
| POST | `/api/chat/regenerate` | 重新生成指定消息的响应 |
| GET | `/api/models` | 获取模型列表（支持 `?category=` 过滤） |

### MemoryController (`/api/memories`)
| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/memories?userId=` | 获取用户所有记忆 |
| GET | `/api/memories/{id}` | 获取单个记忆 |
| GET | `/api/memories/type/{type}?userId=` | 按类型过滤 |
| GET | `/api/memories/types` | 获取所有记忆类型枚举 |
| POST | `/api/memories` | 创建记忆 |
| POST | `/api/memories/batch` | 批量创建 |
| POST | `/api/memories/recall` | 语义召回 |
| PUT | `/api/memories/{id}` | 更新记忆 |
| DELETE | `/api/memories/{id}` | 删除记忆 |
| DELETE | `/api/memories/user/{userId}` | 删除用户所有记忆 |
| DELETE | `/api/memories/cleanup` | 清理过期记忆 |

### ModelConfigController (`/api/model-configs` 和 `/api/models/configs`)
| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `.../` | 获取所有配置 |
| GET | `.../enabled` | 获取启用的配置 |
| GET | `.../types` | 获取所有模型类型 |
| GET | `.../categories` | 获取所有模型分类 |
| GET | `.../by-type/{type}` | 按类型过滤 |
| GET | `.../by-category/{category}` | 按分类过滤 |
| GET | `.../{id}` | 获取单个配置 |
| POST | `.../` | 创建配置 |
| PUT | `.../{id}` | 更新配置 |
| DELETE | `.../{id}` | 删除配置 |

### UserController (`/api/user`)
| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/user/profile?userId=` | 获取用户资料 |
| PUT | `/api/user/profile?userId=` | 更新资料 |
| PUT | `/api/user/preferences?userId=` | 更新偏好 |
| PUT | `/api/user/privacy?userId=` | 更新隐私设置 |
| GET/POST/DELETE | `/api/user/api-keys` | API Key 管理 |
| DELETE | `/api/user/devices/{deviceId}` | 删除设备 |

### ImageController (`/api/images`)
| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/images/upload` | 上传图片（MultipartFile） |
| GET | `/api/images/{filename}` | 获取图片 |
| DELETE | `/api/images/{filename}` | 删除图片 |

### 其他控制器
| 控制器 | 路径前缀 | 功能 |
|--------|---------|------|
| NoteController | `/api/notes` | 笔记 CRUD |
| TodoController | `/api/todos` | 待办事项 CRUD |
| UserSettingController | `/api/user-settings` | 用户设置 |
| PromptTemplateController | `/api/prompt-templates` | Prompt 模板管理 |
| PromptMetricsController | `/api/prompt-metrics` | 构建指标查询 |
| ContentOptimizationController | `/api/content/optimize` | 内容优化 + 限流 |

---

## 五、配置体系

### application.yml 关键配置段

| 配置前缀 | 用途 | 关键默认值 |
|---------|------|-----------|
| `spring.datasource` | MySQL 连接 | HikariCP max=10, min=5 |
| `spring.data.redis` | Redis 连接 | `localhost:6379`, timeout=60s |
| `ollama` | 本地模型服务 | `localhost:11434`, default-model=llama3 |
| `resilience4j.retry.ollamaRetry` | 重试策略 | 3次, 间隔2s |
| `resilience4j.circuitbreaker.ollamaCB` | 熔断器 | 50%阈值, 10s半开, 滑动窗口10 |
| `memory.long-term` | 长期记忆参数 | max-recall=5, min-importance=3, similarity-threshold=0.3, vector-dimension=384 |
| `memory.extractor` | 记忆提取条件 | message-threshold=5, min-confidence=50, min-importance=4, idle-timeout=5min |
| `prompt.security` | 输入安全 | max-input-length=4096, enable-sanitize=true |
| `prompt.token` | Token 限制 | max-tokens=8192, encoding-type=cl100k_base |
| `prompt.metrics` | 指标收集 | enabled=true, interval=60s |
| `websearch` | 网络搜索 | engine=bing, timeout=8s, max-results=5 |
| `rate-limit` | API 限流 | enabled=true, requests-per-minute=10 |
| `optimization` | 内容优化 | model=llama3 |

---

## 六、安全设计

### 6.1 输入安全 (`InputValidator`)
- 长度校验：1 ~ 4096 字符
- 注入检测：11 种危险模式（模板注入 `{{}}`、`{% %}`、XSS `<script>`、SQL 注入 `UNION SELECT`、`DROP TABLE` 等）
- 危险字符过滤

### 6.2 敏感信息脱敏 (`SensitiveFilter`)
- 12 种敏感信息模式：手机号、身份证、邮箱、银行卡、护照、车牌、IPv4/IPv6、URL、微信、QQ、邮编
- 统一替换为 `***`
- 同时用于用户输入脱敏和日志脱敏

### 6.3 限流 (`RateLimitAspect`)
- Redis 有序集合滑动窗口
- 默认 10 次/分钟
- 返回 429 Too Many Requests + retry-after 秒数

### 6.4 全局异常处理
- `GlobalExceptionHandler`：统一捕获 `MethodArgumentNotValidException`、`RuntimeException`、`Exception`
- 统一响应格式：`{code, message, timestamp}`

---

## 七、数据模型关系

```
UserProfile (userId) ──1:1── UserSetting (userId)
     │
     ├──1:N── APIKey (userId)
     ├──1:N── UserDevice (userId)
     ├──1:N── LongTermMemory (userId, sourceConversationId)
     │           └── 向量索引: Redis (key=memory:embedding:{userId}:{id})
     │
     └──1:N── Conversation (userId)
                 │
                 └──1:N── Message (conversationId)
                              └── 短期记忆: Redis L2 (key=kchat:memory:{conversationId})

ModelConfig (userId, modelId, baseUrl, apiKey, type枚举, category枚举)
     └── 模型ID格式: {config.name}:{config.modelId}

Note (userId, memoryId → LongTermMemory.id)
Todo (独立，无外键)

PromptTemplate (name, version) ── 版本链: 更新时创建新版本并禁用旧版本
PromptMetrics (conversationId) ── 每次 Prompt 构建记录一条
```

---

## 八、关键设计模式

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| 编排器 (Orchestrator) | `ChatService`, `ChatWorkflowService` | 协调多个服务完成完整聊天流程 |
| 门面 (Facade) | `ChatWorkflowService` | 为记忆检索/消息组装提供统一入口 |
| 装饰器 (Decorator) | `ShortTermMemory.RedisBackedChatMemory` | 透明地为内存添加 Redis 持久化 |
| 代理 (Proxy) | `ShortTermMemoryService` | 委托给底层 ShortTermMemory 组件 |
| 策略 (Strategy) | `TokenEstimator` 接口 + 多个实现 | 可切换 Token 估算策略 |
| 模板方法 | `PromptAssembler` 的 `assemble()` | 固定7步流程，各步骤可替换 |
| 切面 (AOP) | `RateLimitAspect` | 非侵入式限流 |

---

## 九、已知技术债务

1. **伪嵌入向量**：`VectorStoreConfig` 的 `EmbeddingModel` 使用确定性哈希生成嵌入向量，非真实语义嵌入，语义召回效果有限。建议替换为真实 Embedding API（如 Ollama embedding）。
2. **过期记忆清理不完整**：`LongTermMemoryService.cleanExpired()` 仅清理数据库，向量索引中的过期数据未同步清理。
3. **空闲对话扫描未实现**：`AutoMemoryExtractor.checkIdleConversations()` 为占位实现。
4. **Redis `keys()` 命令**：`ShortTermMemory.clearAll()` 使用 `keys()` 可能在生产环境造成性能问题。
5. **无身份认证**：API 通过 `userId` 查询参数标识用户，无 JWT/Token 认证中间件。
6. **限流仅限内容优化接口**：`RateLimited` 注解当前仅用于内容优化控制器，未覆盖聊天等高频接口。
7. **`OpenAICompatibleClient` 中敏感信息未过滤**：自定义模型调用路径未经过 `InputValidator`/`SensitiveFilter`。
8. **线程池拒绝策略为 `CallerRunsPolicy`**：可能阻塞 HTTP 线程池。
