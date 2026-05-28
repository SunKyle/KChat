# KChat 后端架构设计文档

---

## 文档说明

本文档详细描述了 KChat 对话应用的后端架构设计，包括技术选型、目录结构、核心模块、API 接口设计等。

---

## 一、技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Spring Boot | 3.2.0 | 后端服务框架 |
| 语言 | Java | 17 | 开发语言 |
| 构建工具 | Maven | 3.9+ | 项目构建 |
| 数据库 | MySQL | 8.0+ | 关系型数据库 |
| 缓存 | Redis | 7.0+ | 缓存及向量存储 |
| ORM | Spring Data JPA | 3.2.x | 数据访问层 |
| AI 集成 | LangChain4j | 0.35.0 | AI 模型集成 |
| 流式输出 | SSE (Server-Sent Events) | - | 流式响应 |
| 容错 | Resilience4j | 2.1.0 | 重试/熔断 |
| HTTP 客户端 | OkHttp | 4.12.x | OpenAI兼容API调用 |

---

## 二、项目目录结构

```
backend/                              # Maven 后端项目根目录
├── src/
│   └── main/
│       ├── java/com/example/app/
│       │   ├── Application.java      # Spring Boot 主启动类
│       │   ├── controller/           # REST API 控制层
│       │   │   ├── ChatController.java
│       │   │   ├── ImageController.java
│       │   │   ├── ModelConfigController.java
│       │   │   └── UserSettingController.java
│       │   ├── service/              # 业务逻辑层
│       │   │   ├── ChatService.java
│       │   │   ├── StreamingService.java
│       │   │   ├── MemoryService.java
│       │   │   ├── ConversationService.java
│       │   │   ├── MessagePersistenceService.java
│       │   │   ├── ImageService.java
│       │   │   ├── ModelConfigService.java
│       │   │   └── UserSettingService.java
│       │   ├── repository/           # 数据访问层
│       │   │   ├── ConversationRepository.java
│       │   │   ├── MessageRepository.java
│       │   │   ├── ModelConfigRepository.java
│       │   │   ├── UserSettingRepository.java
│       │   │   └── LongTermMemoryRepository.java
│       │   ├── entity/               # 数据库实体
│       │   │   ├── Conversation.java
│       │   │   ├── Message.java
│       │   │   ├── ModelConfig.java
│       │   │   ├── UserSetting.java
│       │   │   └── LongTermMemory.java
│       │   ├── dto/                  # 数据传输对象
│       │   │   ├── ChatRequest.java
│       │   │   ├── ChatResponse.java
│       │   │   ├── ConversationDTO.java
│       │   │   ├── MessageDTO.java
│       │   │   ├── ModelConfigDTO.java
│       │   │   └── UserSettingDTO.java
│       │   ├── config/               # 配置类
│       │   │   ├── OllamaConfig.java
│       │   │   ├── StreamingConfig.java
│       │   │   ├── RedisConfig.java
│       │   │   ├── AsyncConfig.java
│       │   │   └── WebConfig.java
│       │   ├── client/               # 外部服务客户端
│       │   │   ├── OllamaClient.java
│       │   │   └── OpenAICompatibleClient.java
│       │   ├── memory/               # 记忆模块
│       │   │   ├── ShortTermMemory.java
│       │   │   └── LongTermMemoryManager.java
│       │   ├── exception/            # 异常处理
│       │   │   ├── ErrorResponse.java
│       │   │   └── GlobalExceptionHandler.java
│       │   └── util/                 # 工具类
│       │       └── JsonUtils.java
│       └── resources/
│           └── application.yml       # 应用配置
├── src/test/                         # 测试代码
│   └── java/com/example/app/
│       ├── controller/
│       │   └── ChatControllerTest.java
│       ├── service/
│       │   ├── ChatServiceTest.java
│       │   └── MemoryServiceTest.java
│       └── TestConfig.java
└── pom.xml                           # Maven 依赖配置
```

---

## 三、核心模块划分

### 3.1 模块职责说明

| 模块 | 职责 | 状态 |
|------|------|------|
| **Controller** | REST API 入口，处理 HTTP 请求 | ✅ |
| **Service** | 业务逻辑处理，调用 AI 模型 | ✅ |
| **Repository** | 数据访问，与数据库交互 | ✅ |
| **Entity** | 数据库表映射实体 | ✅ |
| **DTO** | 请求/响应数据传输对象 | ✅ |
| **Config** | Spring 配置类 | ✅ |
| **Client** | Ollama/OpenAI 服务客户端封装 | ✅ |
| **Memory** | 短期/长期记忆管理 | ✅ |
| **Exception** | 全局异常处理 | ✅ |

### 3.2 类职责设计

| 类名 | 职责 | 所属模块 |
|------|------|----------|
| `ChatController` | 处理聊天相关 HTTP 请求 | controller |
| `ImageController` | 处理图片上传/下载请求 | controller |
| `ModelConfigController` | 处理模型配置管理请求 | controller |
| `UserSettingController` | 处理用户设置管理请求 | controller |
| `ChatService` | 同步消息生成业务逻辑 | service |
| `StreamingService` | 流式消息输出业务逻辑 | service |
| `MemoryService` | 记忆管理协调服务 | service |
| `ConversationService` | 对话管理服务 | service |
| `MessagePersistenceService` | 消息持久化服务 | service |
| `ImageService` | 图片存储服务 | service |
| `ModelConfigService` | 模型配置管理服务 | service |
| `UserSettingService` | 用户设置管理服务 | service |
| `ConversationRepository` | 对话数据访问 | repository |
| `MessageRepository` | 消息数据访问 | repository |
| `ModelConfigRepository` | 模型配置数据访问 | repository |
| `UserSettingRepository` | 用户设置数据访问 | repository |
| `LongTermMemoryRepository` | 长期记忆数据访问 | repository |
| `Conversation` | 对话实体 | entity |
| `Message` | 消息实体 | entity |
| `ModelConfig` | 模型配置实体 | entity |
| `UserSetting` | 用户设置实体 | entity |
| `LongTermMemory` | 长期记忆实体 | entity |
| `ChatRequest` | 聊天请求 DTO | dto |
| `ChatResponse` | 聊天响应 DTO | dto |
| `ModelConfigDTO` | 模型配置请求 DTO | dto |
| `UserSettingDTO` | 用户设置请求 DTO | dto |
| `OllamaConfig` | Ollama 配置 | config |
| `StreamingConfig` | 流式配置 | config |
| `RedisConfig` | Redis 配置 | config |
| `AsyncConfig` | 异步线程池配置 | config |
| `OllamaClient` | Ollama 客户端 | client |
| `OpenAICompatibleClient` | OpenAI 兼容 API 客户端 | client |
| `ShortTermMemory` | 短期记忆实现 | memory |
| `LongTermMemoryManager` | 长期记忆实现 | memory |
| `GlobalExceptionHandler` | 全局异常处理器 | exception |
| `JsonUtils` | JSON 工具类 | util |

---

## 四、数据流

### 4.1 同步消息流程

```
客户端 → POST /api/chat → ChatController → ChatService → OllamaClient → Ollama API → 返回响应
                                                              ↓
                                                     MemoryService 更新短期记忆
                                                              ↓
                                                     MessagePersistenceService 保存消息
```

### 4.2 流式消息流程

```
客户端 → POST /api/chat/stream → ChatController → StreamingService → SseEmitter
                                                                       ↓
                                                          检查自定义模型配置
                                                                       ↓
                                                    ┌─────────────────┴─────────────────┐
                                                    ▼                                 ▼
                                             自定义模型配置存在                使用默认 Ollama
                                                    ↓                                 ↓
                                          OpenAICompatibleClient        OllamaClient.streamGenerate()
                                                    ↓                                 ↓
                                          支持图片生成/多模态                     SSE 事件流推送
```

### 4.3 会话管理流程

```
GET /api/conversations → ChatController → ConversationRepository → 返回会话列表
POST /api/conversations → ChatController → ConversationRepository → 创建会话
GET /api/conversations/{id} → ChatController → ConversationRepository + MessageRepository → 返回对话详情
PUT /api/conversations/{id} → ChatController → ConversationRepository → 更新会话标题
DELETE /api/conversations/{id} → ChatController → ConversationRepository + MemoryService → 删除会话
```

### 4.4 图片处理流程

```
客户端上传 → POST /api/images/upload → ImageController → ImageService → 本地存储 → 返回图片URL
客户端请求 → GET /api/images/{filename} → ImageController → ImageService → 读取文件 → 返回图片数据
```

---

## 五、API 接口设计

### 5.1 接口列表

| 接口 | HTTP 方法 | 路径 | 说明 |
|------|----------|------|------|
| 创建对话 | POST | `/api/conversations` | 创建新对话 |
| 获取对话列表 | GET | `/api/conversations` | 获取所有对话 |
| 获取对话详情 | GET | `/api/conversations/{id}` | 获取单个对话及消息 |
| 更新对话 | PUT | `/api/conversations/{id}` | 更新对话标题 |
| 删除对话 | DELETE | `/api/conversations/{id}` | 删除对话 |
| 发送消息（同步） | POST | `/api/chat` | 发送消息并等待响应 |
| 发送消息（流式） | POST | `/api/chat/stream` | SSE 流式响应 |
| 获取模型列表 | GET | `/api/models` | 获取可用模型列表 |
| 上传图片 | POST | `/api/images/upload` | 上传图片文件 |
| 获取图片 | GET | `/api/images/{filename}` | 获取图片资源 |
| 删除图片 | DELETE | `/api/images/{filename}` | 删除图片 |
| 获取模型配置列表 | GET | `/api/model-configs` | 获取所有模型配置 |
| 获取启用的配置 | GET | `/api/model-configs/enabled` | 获取启用的模型配置 |
| 获取模型配置 | GET | `/api/model-configs/{id}` | 获取单个配置 |
| 创建模型配置 | POST | `/api/model-configs` | 创建新配置 |
| 更新模型配置 | PUT | `/api/model-configs/{id}` | 更新配置 |
| 删除模型配置 | DELETE | `/api/model-configs/{id}` | 删除配置 |
| 获取用户设置 | GET | `/api/settings/{userId}` | 获取用户设置 |
| 更新用户设置 | PUT | `/api/settings/{userId}` | 更新用户设置 |
| 删除用户设置 | DELETE | `/api/settings/{userId}` | 删除用户设置 |

### 5.2 请求/响应示例

#### 创建对话
```http
POST /api/conversations
Content-Type: application/json

{"title": "新对话"}
```
**响应**:
```json
{"id": "uuid", "title": "新对话", "createdAt": "2024-01-01 12:00:00", "updatedAt": "2024-01-01 12:00:00"}
```

#### 发送消息（同步）
```http
POST /api/chat
Content-Type: application/json

{"message": "你好", "conversationId": "uuid", "model": "llama3"}
```
**响应**:
```json
{"messageId": "uuid", "content": "您好！", "role": "assistant", "conversationId": "uuid"}
```

#### 发送消息（流式）
```http
POST /api/chat/stream
Content-Type: application/json
Accept: text/event-stream

{"message": "你好", "model": "llama3"}
```
**响应**（SSE）:
```
event: message
data: {"content": "你"}

event: message  
data: {"content": "好"}

event: done
data: {"messageId": "uuid"}
```

#### 上传图片
```http
POST /api/images/upload
Content-Type: multipart/form-data

image: <file>
```
**响应**:
```json
{"url": "http://localhost:8080/api/images/2c05f1aa-5df3-4258-8d09-aa8a7cba2a7f.jpg"}
```

#### 创建模型配置
```http
POST /api/model-configs
Content-Type: application/json

{
  "name": "OpenAI",
  "modelType": "OPENAI",
  "baseUrl": "https://api.openai.com/v1",
  "apiKey": "sk-xxx",
  "enabled": true,
  "defaultModel": "gpt-4"
}
```
**响应**:
```json
{
  "id": 1,
  "name": "OpenAI",
  "modelType": "OPENAI",
  "baseUrl": "https://api.openai.com/v1",
  "apiKey": "sk-xxx",
  "enabled": true,
  "defaultModel": "gpt-4"
}
```

---

## 六、数据库设计

### 6.1 表结构

#### conversation 表
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | VARCHAR(36) | 主键，UUID |
| user_id | VARCHAR(36) | 用户ID，默认 "default" |
| title | VARCHAR(255) | 对话标题 |
| model_id | VARCHAR(255) | 使用的模型ID |
| token_usage | INT | Token 使用量 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

#### message 表
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | VARCHAR(36) | 主键，UUID |
| conversation_id | VARCHAR(36) | 关联对话 ID |
| content | TEXT | 消息内容 |
| role | VARCHAR(20) | 角色（user/assistant） |
| timestamp | DATETIME | 时间戳 |
| images | TEXT | 图片数据（已废弃） |
| image_urls | TEXT | 图片URL列表（JSON数组） |
| token_count | INT | Token 数量 |

#### model_config 表
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键，自增 |
| name | VARCHAR(100) | 配置名称 |
| model_type | VARCHAR(20) | 模型类型（OLLAMA/OPENAI/CUSTOM） |
| base_url | VARCHAR(500) | API基础URL |
| api_key | VARCHAR(500) | API密钥 |
| enabled | BOOLEAN | 是否启用 |
| default_model | VARCHAR(255) | 默认模型 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

#### user_setting 表
| 字段名 | 类型 | 说明 |
|--------|------|------|
| user_id | VARCHAR(36) | 用户ID，主键 |
| theme | VARCHAR(50) | 主题（light/dark） |
| memory_enable | BOOLEAN | 是否启用记忆 |
| default_model | VARCHAR(255) | 默认模型 |
| context_size | INT | 上下文大小 |
| auto_title | BOOLEAN | 是否自动生成标题 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

#### long_term_memory 表
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键，自增 |
| user_id | VARCHAR(36) | 用户ID |
| content | TEXT | 记忆内容 |
| embedding | TEXT | 向量嵌入（JSON数组） |
| created_at | DATETIME | 创建时间 |

### 6.2 ER 图

```
┌─────────────────┐       ┌─────────────────┐
│  conversation   │  1:N  │    message      │
├─────────────────┤───────►├─────────────────┤
│ id (PK)         │       │ id (PK)         │
│ user_id         │       │ conversation_id │
│ title           │       │ content         │
│ model_id        │       │ role            │
│ token_usage     │       │ timestamp       │
│ created_at      │       │ image_urls      │
│ updated_at      │       │ token_count     │
└─────────────────┘       └─────────────────┘

┌─────────────────┐       ┌─────────────────┐
│   model_config  │       │  user_setting   │
├─────────────────┤       ├─────────────────┤
│ id (PK)         │       │ user_id (PK)    │
│ name            │       │ theme           │
│ model_type      │       │ memory_enable   │
│ base_url        │       │ default_model   │
│ api_key         │       │ context_size    │
│ enabled         │       │ auto_title      │
│ default_model   │       │ created_at      │
│ created_at      │       │ updated_at      │
│ updated_at      │       └─────────────────┘
└─────────────────┘

┌─────────────────┐
│long_term_memory │
├─────────────────┤
│ id (PK)         │
│ user_id         │
│ content         │
│ embedding       │
│ created_at      │
└─────────────────┘
```

---

## 七、记忆模块设计

### 7.1 短期记忆

- **实现**：基于 `MessageWindowChatMemory`
- **存储**：`ConcurrentHashMap<String, ChatMemory>`
- **特点**：会话级别隔离，最多保留 20 条消息
- **生命周期**：服务运行期间存在，重启后丢失

### 7.2 长期记忆

- **实现**：基于 Redis 向量存储（LangChain4j Redis）
- **存储**：Redis + MySQL 持久化
- **特点**：用户级别隔离，支持语义检索
- **功能**：
  - `store(userId, content)`: 存储记忆片段
  - `retrieve(userId)`: 获取用户相关记忆
  - `clear(userId)`: 清除用户记忆

### 7.3 记忆服务 API

`MemoryService` 作为记忆管理协调层，整合短期和长期记忆：

| 方法 | 说明 |
|------|------|
| `getMemoryContext(conversationId)` | 获取对话短期记忆上下文 |
| `getLongTermMemoryContext(userId)` | 获取用户长期记忆上下文 |
| `updateMemory(conversationId, userMsg, aiMsg)` | 更新短期记忆 |
| `updateMemoryWithUserMessage(conversationId, content)` | 更新用户消息到记忆 |
| `updateMemoryWithAiMessage(conversationId, content)` | 更新AI响应到记忆 |
| `storeLongTermMemory(userId, content)` | 存储长期记忆 |
| `clearMemory(conversationId)` | 清除对话短期记忆 |
| `clearAllMemory()` | 清除所有短期记忆 |
| `clearAllMemory(userId)` | 清除用户所有记忆 |

---

## 八、多模型支持

### 8.1 模型类型

| 类型 | 说明 | 客户端 |
|------|------|--------|
| OLLAMA | 本地 Ollama 模型 | OllamaClient |
| OPENAI | OpenAI API 兼容模型 | OpenAICompatibleClient |
| CUSTOM | 自定义 API 模型 | OpenAICompatibleClient |

### 8.2 模型调用流程

```
用户请求 → StreamingService → ModelConfigService
                                      ↓
                          查询自定义模型配置
                                      ↓
                    ┌─────────────────┴─────────────────┐
                    ▼                                 ▼
              配置存在                              配置不存在
                    ↓                                 ↓
         OpenAICompatibleClient               OllamaClient
                    ↓                                 ↓
         解析模型ID格式:                              直接调用
         {configName}_{modelId}                默认模型或指定模型
                    ↓
         判断是否为图片生成模型
                    ↓
          ┌────────┴────────┐
          ▼                 ▼
     图片生成            文本生成
```

### 8.3 模型 ID 格式

自定义模型使用格式：`{configName}_{modelId}`

示例：
- `OpenAI_gpt-4` - 使用 OpenAI 配置调用 gpt-4 模型
- `CustomAPI_llama3` - 使用自定义 API 配置调用 llama3

---

## 九、容错设计

### 9.1 重试机制（Resilience4j Retry）

| 配置项 | 值 | 说明 |
|--------|-----|------|
| max-attempts | 3 | 最大重试次数 |
| wait-duration | 2s | 重试间隔 |
| retry-exceptions | IOException, SocketTimeoutException, RuntimeException | 触发重试的异常类型 |

### 9.2 熔断机制（Resilience4j CircuitBreaker）

| 配置项 | 值 | 说明 |
|--------|-----|------|
| sliding-window-size | 10 | 滑动窗口大小 |
| minimum-number-of-calls | 5 | 最小调用次数 |
| failure-rate-threshold | 50 | 失败率阈值（%） |
| wait-duration-in-open-state | 10s | 熔断开启状态等待时间 |
| permitted-number-of-calls-in-half-open-state | 3 | 半开状态允许调用次数 |

---

## 十、配置说明

### 10.1 application.yml 关键配置

```yaml
server:
  port: 8080

spring:
  application:
    name: kchat-backend
  servlet:
    multipart:
      enabled: true
      max-file-size: 50MB
      max-request-size: 50MB
  datasource:
    url: jdbc:mysql://localhost:3306/kchatdb?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf8
    driverClassName: com.mysql.cj.jdbc.Driver
    username: admin
    password: sxk1997sxk
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    database-platform: org.hibernate.dialect.MySQLDialect
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 60000ms
      enabled: true

ollama:
  base-url: http://localhost:11434
  default-model: llama3

resilience4j:
  retry:
    instances:
      ollamaRetry:
        max-attempts: 3
        wait-duration: 2s
        retry-exceptions:
          - java.io.IOException
          - java.net.SocketTimeoutException
          - java.lang.RuntimeException
  circuitbreaker:
    instances:
      ollamaCB:
        register-health-indicator: true
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
```

### 10.2 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8080 | 服务端口 |
| spring.datasource.url | jdbc:mysql://localhost:3306/kchatdb | MySQL 数据库连接 |
| spring.datasource.username | admin | 数据库用户名 |
| spring.datasource.password | sxk1997sxk | 数据库密码 |
| spring.data.redis.host | localhost | Redis 主机 |
| spring.data.redis.port | 6379 | Redis 端口 |
| ollama.base-url | http://localhost:11434 | Ollama 服务地址 |
| ollama.default-model | llama3 | 默认使用的 AI 模型 |
| spring.servlet.multipart.max-file-size | 50MB | 最大上传文件大小 |

---

## 十一、安全与性能

### 11.1 安全措施

- **跨域配置**：配置允许的前端域名（`http://localhost:5173`）
- **文件上传限制**：限制文件大小和类型
- **异常处理**：全局异常处理器统一处理异常
- **日志脱敏**：敏感信息（如 API Key）在日志中脱敏显示

### 11.2 性能优化

- **数据库连接池**：使用 HikariCP，配置合理的连接池大小
- **异步处理**：流式响应使用独立线程池
- **模型缓存**：OllamaClient 使用 ConcurrentHashMap 缓存模型实例
- **Redis 缓存**：利用 Redis 进行向量存储和缓存

---

## 十二、启动方式

### 12.1 开发环境

```bash
cd backend
mvn spring-boot:run
```

### 12.2 打包部署

```bash
cd backend
mvn clean package
java -jar target/kchat-backend-1.0.0.jar
```

### 12.3 测试运行

```bash
cd backend
mvn test
```

### 12.4 依赖服务启动

确保以下服务已启动：
- MySQL 数据库（端口：3306）
- Redis（端口：6379）
- Ollama（端口：11434）- 可选，用于本地模型

---

## 十三、扩展点

### 13.1 功能扩展

- [ ] 用户认证授权（JWT/OAuth2）
- [ ] 消息编辑/删除
- [ ] 对话导出（JSON/Markdown）
- [ ] 多用户支持
- [ ] 消息搜索功能
- [ ] API 访问日志

### 13.2 技术扩展

- [ ] 消息队列异步处理（RabbitMQ/Kafka）
- [ ] 分布式部署支持
- [ ] 监控指标集成（Prometheus/Grafana）
- [ ] API 限流（Rate Limiting）
- [ ] 灰度发布支持

---

*文档版本: 2.0*  
*创建日期: 2026-05-28*  
*适用项目: KChat 对话应用后端*
