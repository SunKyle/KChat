# KChat 后端项目结构文档

---

## 一、项目概述

KChat 是一个基于 Spring Boot 的 AI 聊天应用后端服务，提供对话管理、记忆系统、模型配置等核心功能。

**技术栈**：
- Java 17
- Spring Boot 3.2.x
- Spring Data JPA
- Redis（缓存、向量存储）
- LangChain4j（LLM 集成）
- Ollama（本地模型支持）

---

## 二、目录结构

```
backend/
├── src/main/java/com/example/app/
│   ├── Application.java              # 启动类
│   ├── client/                       # 外部API客户端
│   ├── config/                       # 配置类
│   ├── controller/                   # REST API控制器
│   ├── dto/                          # 数据传输对象
│   ├── entity/                       # 数据库实体
│   ├── exception/                    # 异常处理
│   ├── memory/                       # 记忆管理组件
│   ├── repository/                   # 数据访问层
│   ├── service/                      # 业务逻辑层
│   │   └── impl/                     # Service实现类
│   └── util/                         # 工具类
└── src/test/                         # 测试代码
```

---

## 三、模块职责说明

### 3.1 启动类

| 文件 | 作用 | 说明 |
|------|------|------|
| `Application.java` | Spring Boot 应用启动入口 | 包含 `@SpringBootApplication` 注解 |

---

### 3.2 client（外部API客户端）

| 文件 | 作用 | 核心功能 |
|------|------|----------|
| `OllamaClient.java` | Ollama 模型客户端 | 同步/流式生成、模型列表查询、图像支持 |
| `OpenAICompatibleClient.java` | OpenAI 兼容 API 客户端 | 支持自定义模型配置、图像生成 |
| `HttpStreamingTemplate.java` | HTTP 流式请求模板 | 封装 SSE 流式响应处理 |

---

### 3.3 config（配置类）

| 文件 | 作用 | 配置内容 |
|------|------|----------|
| `AsyncConfig.java` | 异步任务配置 | 线程池配置 |
| `MemoryExtractorConfig.java` | 记忆提取配置 | 置信度阈值、重要性阈值 |
| `OllamaConfig.java` | Ollama 配置 | BaseURL、默认模型 |
| `RedisConfig.java` | Redis 配置 | 连接池、序列化 |
| `StreamingConfig.java` | 流式配置 | SSE 超时时间 |
| `VectorStoreConfig.java` | 向量存储配置 | 相似度阈值、最小重要性 |
| `WebConfig.java` | Web 配置 | CORS、跨域设置 |

---

### 3.4 controller（REST API控制器）

| 文件 | 作用 | API 端点 |
|------|------|----------|
| `ChatController.java` | 对话控制器 | `/api/chat`, `/api/conversations`, `/api/models` |
| `ImageController.java` | 图像控制器 | `/api/images` |
| `MemoryController.java` | 记忆控制器 | `/api/memories` |
| `ModelConfigController.java` | 模型配置控制器 | `/api/model-configs` |
| `UserSettingController.java` | 用户设置控制器 | `/api/user-settings` |

---

### 3.5 dto（数据传输对象）

| 文件 | 作用 | 用途 |
|------|------|------|
| `ChatRequest.java` | 聊天请求 DTO | 包含消息、模型、用户ID等 |
| `ChatResponse.java` | 聊天响应 DTO | 返回消息内容、消息ID |
| `ConversationDTO.java` | 对话 DTO | 对话信息及消息列表 |
| `MemoryDTO.java` | 记忆 DTO | 长期记忆数据 |
| `MemoryRecallRequest.java` | 记忆召回请求 | 查询文本、topK、类型过滤 |
| `MessageDTO.java` | 消息 DTO | 单条消息数据 |
| `ModelConfigDTO.java` | 模型配置 DTO | 自定义模型配置 |
| `UserSettingDTO.java` | 用户设置 DTO | 用户偏好设置 |

---

### 3.6 entity（数据库实体）

| 文件 | 对应表 | 说明 |
|------|--------|------|
| `Conversation.java` | `conversation` | 对话实体，存储对话元信息 |
| `LongTermMemory.java` | `long_term_memory` | 长期记忆实体，支持多种记忆类型 |
| `Message.java` | `message` | 消息实体，存储对话消息 |
| `ModelConfig.java` | `model_config` | 模型配置实体 |
| `UserSetting.java` | `user_setting` | 用户设置实体 |

---

### 3.7 exception（异常处理）

| 文件 | 作用 |
|------|------|
| `ErrorResponse.java` | 统一错误响应结构 |
| `GlobalExceptionHandler.java` | 全局异常处理器 |

---

### 3.8 memory（记忆管理组件）

| 文件 | 作用 | 核心职责 |
|------|------|----------|
| `ShortTermMemory.java` | 短期记忆管理 | L1缓存（内存）+ L2缓存（Redis）的双层缓存设计 |
| `VectorStoreWrapper.java` | 向量存储封装 | 基于Redis的向量索引管理、相似度计算 |

---

### 3.9 repository（数据访问层）

| 文件 | 对应实体 | 说明 |
|------|----------|------|
| `ConversationRepository.java` | `Conversation` | 对话数据访问 |
| `LongTermMemoryRepository.java` | `LongTermMemory` | 长期记忆数据访问 |
| `MessageRepository.java` | `Message` | 消息数据访问 |
| `ModelConfigRepository.java` | `ModelConfig` | 模型配置数据访问 |
| `UserSettingRepository.java` | `UserSetting` | 用户设置数据访问 |

---

### 3.10 service（业务逻辑层）

#### 3.10.1 接口

| 文件 | 作用 | 核心方法 |
|------|------|----------|
| `MemoryExtractor.java` | 记忆提取接口 | `extract()`, `extractAndSave()` |
| `MemoryRecaller.java` | 记忆召回接口 | `recall()` |

#### 3.10.2 实现类

| 文件 | 作用 | 说明 |
|------|------|------|
| `MemoryExtractorImpl.java` | 记忆提取实现 | 基于LLM的智能记忆提取 |
| `MemoryRecallerImpl.java` | 记忆召回实现 | 语义相似度召回 |

#### 3.10.3 服务类

| 文件 | 作用 | 核心职责 |
|------|------|----------|
| `AutoMemoryExtractor.java` | 自动记忆提取 | 对话完成后自动提取记忆 |
| `ChatService.java` | 聊天服务 | 同步消息处理、完整响应 |
| `ChatWorkflowService.java` | 聊天流程编排 | 短期记忆、长期记忆、消息组装 |
| `ConversationMessageCounter.java` | 对话消息计数器 | 消息计数管理 |
| `ConversationService.java` | 对话服务 | 对话CRUD、消息管理 |
| `ImageService.java` | 图像服务 | 图像生成、处理 |
| `LongTermMemoryService.java` | 长期记忆服务 | 记忆CRUD、向量索引维护、语义召回 |
| `MessagePersistenceService.java` | 消息持久化服务 | 消息保存到数据库 |
| `ModelConfigService.java` | 模型配置服务 | 模型配置管理 |
| `ShortTermMemoryService.java` | 短期记忆服务 | 对话上下文管理 |
| `StreamingService.java` | 流式服务 | SSE流式响应处理 |
| `UserSettingService.java` | 用户设置服务 | 用户偏好管理 |

---

### 3.11 util（工具类）

| 文件 | 作用 | 功能 |
|------|------|------|
| `JsonUtils.java` | JSON工具类 | JSON转义、序列化 |
| `PromptAssembler.java` | 提示词组装器 | 将记忆和消息组装为LLM提示词 |

---

## 四、核心业务流程

### 4.1 同步聊天流程

```
ChatController → ChatService → ChatWorkflowService → LongTermMemoryService
                          ↓
                    OllamaClient
                          ↓
                MessagePersistenceService → ConversationService
                          ↓
                    AutoMemoryExtractor
```

### 4.2 流式聊天流程

```
ChatController → StreamingService → ChatWorkflowService → LongTermMemoryService
                                         ↓
                                   OllamaClient / OpenAICompatibleClient
                                         ↓
                              MessagePersistenceService
                                         ↓
                                   AutoMemoryExtractor
```

### 4.3 记忆召回流程

```
ChatWorkflowService → LongTermMemoryService → VectorStoreWrapper → Redis
                                               ↓
                                    LongTermMemoryRepository → Database
```

---

## 五、依赖关系图

```
┌─────────────────────────────────────────────────────────────────┐
│                        Controller层                             │
│  ChatController  ImageController  MemoryController  ...        │
└───────────────────┬───────────────────┬─────────────────────────┘
                    │                   │
                    ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Service层                              │
│  ChatService  StreamingService  ChatWorkflowService            │
│       │              │                  │                      │
│       ├──────────────┼──────────────────┤                      │
│       ▼              ▼                  ▼                      │
│  OllamaClient  ShortTermMemoryService  LongTermMemoryService   │
│       │              │                      │                  │
│       │              ▼                      ▼                  │
│       │       ShortTermMemory       VectorStoreWrapper         │
│       │              │                      │                  │
│       │              ▼                      ▼                  │
│       │          Redis                 Redis                   │
│       │                                                      │
│       ▼                                                      ▼
│  HttpStreamingTemplate                           LongTermMemoryRepository
│                                                          │
│                                                          ▼
│                                                     Database
└─────────────────────────────────────────────────────────────────┘
```

---

## 六、关键设计决策

### 6.1 记忆系统设计

| 特性 | 设计 | 原因 |
|------|------|------|
| 短期记忆 | L1（内存）+ L2（Redis）双层缓存 | 兼顾性能和持久化 |
| 长期记忆 | 数据库 + 向量索引 | 支持语义召回 |
| 记忆类型 | PROFILE/PREFERENCE/PROJECT/SKILL/TASK/KNOWLEDGE/RELATION/EVENT | 便于分类管理和过滤 |

### 6.2 向量存储设计

- **存储介质**: Redis（字符串存储向量数组）
- **索引结构**: Set 存储用户记忆ID列表
- **相似度计算**: 余弦相似度

### 6.3 容错设计

- Redis不可用时自动降级为纯内存存储
- 向量索引与数据库事务一致性保证
- 异常时记录日志但不中断核心流程

---

## 七、代码质量指标

| 指标 | 当前状态 |
|------|----------|
| 编译状态 | ✅ 通过 |
| 测试覆盖率 | 核心模块已覆盖 |
| 代码重复率 | 重构后显著降低 |
| 依赖层级 | 最多3层 |

---

**生成时间**: 2026-05-29  
**项目版本**: 1.0.0