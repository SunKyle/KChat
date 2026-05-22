# ChatGPT 风格对话应用 - 后端架构设计文档

---

## 文档说明

本文档详细描述了 ChatGPT 风格对话应用的后端架构设计，包括技术选型、目录结构、核心模块、API 接口设计等。

---

## 一、技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Spring Boot | 3.2.0 | 后端服务框架 |
| 语言 | Java | 17 | 开发语言 |
| 构建工具 | Maven | 3.9+ | 项目构建 |
| 数据库 | H2 | 2.2.x | 内存数据库 |
| ORM | Spring Data JPA | 3.2.x | 数据访问层 |
| AI 集成 | LangChain4j | 0.35.0 | AI 模型集成 |
| 流式输出 | SSE (Server-Sent Events) | - | 流式响应 |

---

## 二、项目目录结构

```
backend/                              # Maven 后端项目根目录
├── src/
│   └── main/
│       ├── java/com/example/app/
│       │   ├── Application.java      # Spring Boot 主启动类
│       │   ├── controller/           # REST API 控制层
│       │   │   └── ChatController.java
│       │   ├── service/              # 业务逻辑层
│       │   │   ├── ChatService.java
│       │   │   ├── StreamingService.java
│       │   │   └── MemoryService.java
│       │   ├── repository/           # 数据访问层
│       │   │   ├── ConversationRepository.java
│       │   │   └── MessageRepository.java
│       │   ├── entity/                # 数据库实体
│       │   │   ├── Conversation.java
│       │   │   └── Message.java
│       │   ├── dto/                   # 数据传输对象
│       │   │   ├── ChatRequest.java
│       │   │   ├── ChatResponse.java
│       │   │   ├── ConversationDTO.java
│       │   │   └── MessageDTO.java
│       │   ├── config/                # 配置类
│       │   │   ├── OllamaConfig.java
│       │   │   └── StreamingConfig.java
│       │   ├── client/                # 外部服务客户端
│       │   │   └── OllamaClient.java
│       │   └── memory/                # 记忆模块
│       │       ├── ShortTermMemory.java
│       │       └── LongTermMemory.java
│       └── resources/
│           ├── application.yml        # 应用配置
│           └── schema.sql             # 数据库初始化脚本
├── src/test/                          # 测试代码
└── pom.xml                            # Maven 依赖配置
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
| **Client** | Ollama 服务客户端封装 | ✅ |
| **Memory** | 短期/长期记忆管理 | ✅ |

### 3.2 类职责设计

| 类名 | 职责 | 所属模块 |
|------|------|----------|
| `ChatController` | 处理聊天相关 HTTP 请求 | controller |
| `ChatService` | 同步消息生成业务逻辑 | service |
| `StreamingService` | 流式消息输出业务逻辑 | service |
| `MemoryService` | 记忆管理协调服务 | service |
| `ConversationRepository` | 对话数据访问 | repository |
| `MessageRepository` | 消息数据访问 | repository |
| `Conversation` | 对话实体 | entity |
| `Message` | 消息实体 | entity |
| `ChatRequest` | 聊天请求 DTO | dto |
| `ChatResponse` | 聊天响应 DTO | dto |
| `OllamaConfig` | Ollama 配置 | config |
| `StreamingConfig` | 流式配置 | config |
| `OllamaClient` | Ollama 客户端 | client |
| `ShortTermMemory` | 短期记忆实现 | memory |
| `LongTermMemory` | 长期记忆预留 | memory |

---

## 四、数据流

### 4.1 同步消息流程

```
客户端 → POST /api/chat → ChatController → ChatService → OllamaClient → Ollama API → 返回响应
                                                              ↓
                                                     MemoryService 更新记忆
                                                              ↓
                                                     Repository 保存消息
```

### 4.2 流式消息流程

```
客户端 → POST /api/chat/stream → ChatController → StreamingService → SseEmitter
                                                                       ↓
                                                          OllamaClient.streamGenerate()
                                                                       ↓
                                                          SSE 事件流推送
```

### 4.3 会话管理流程

```
GET /api/conversations → ChatController → ConversationRepository → 返回会话列表
POST /api/conversations → ChatController → ConversationRepository → 创建会话
GET /api/conversations/{id} → ChatController → ConversationRepository + MessageRepository → 返回对话详情
DELETE /api/conversations/{id} → ChatController → ConversationRepository + MemoryService → 删除会话
```

---

## 五、API 接口设计

### 5.1 接口列表

| 接口 | HTTP 方法 | 路径 | 说明 |
|------|----------|------|------|
| 创建对话 | POST | `/api/conversations` | 创建新对话 |
| 获取对话列表 | GET | `/api/conversations` | 获取所有对话 |
| 获取对话详情 | GET | `/api/conversations/{id}` | 获取单个对话及消息 |
| 删除对话 | DELETE | `/api/conversations/{id}` | 删除对话 |
| 发送消息（同步） | POST | `/api/chat` | 发送消息并等待响应 |
| 发送消息（流式） | POST | `/api/chat/stream` | SSE 流式响应 |

### 5.2 请求/响应示例

#### 创建对话
```http
POST /api/conversations
Content-Type: application/json

{"title": "新对话"}
```
**响应**:
```json
{"id": "uuid", "title": "新对话", "createdAt": "2024-01-01 12:00:00"}
```

#### 发送消息
```http
POST /api/chat
Content-Type: application/json

{"message": "你好", "conversationId": "uuid"}
```
**响应**:
```json
{"messageId": "uuid", "content": "您好！", "role": "assistant", "conversationId": "uuid"}
```

#### 流式消息
```http
POST /api/chat/stream
Content-Type: application/json
Accept: text/event-stream

{"message": "你好"}
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

---

## 六、数据库设计

### 6.1 表结构

#### conversation 表
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | VARCHAR(36) | 主键，UUID |
| title | VARCHAR(255) | 对话标题 |
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

### 6.2 ER 图

```
┌─────────────────┐       ┌─────────────────┐
│  conversation   │  1:N  │    message      │
├─────────────────┤───────►├─────────────────┤
│ id (PK)         │       │ id (PK)         │
│ title           │       │ conversation_id │
│ created_at      │       │ content         │
│ updated_at      │       │ role            │
└─────────────────┘       │ timestamp       │
                          └─────────────────┘
```

---

## 七、记忆模块设计

### 7.1 短期记忆

- 实现：基于 `MessageWindowChatMemory`
- 存储：`ConcurrentHashMap<String, ChatMemory>`
- 特点：会话级别隔离，最多保留 20 条消息

### 7.2 长期记忆（预留）

- 预留接口，支持后续扩展
- 可扩展为向量数据库存储

### 7.3 记忆服务

`MemoryService` 作为记忆管理协调层，整合短期和长期记忆：

| 方法 | 说明 |
|------|------|
| `getMemoryContext(conversationId)` | 获取对话上下文 |
| `updateMemory(conversationId, userMsg, aiMsg)` | 更新记忆 |
| `clearMemory(conversationId)` | 清除对话记忆 |

---

## 八、配置说明

### 8.1 application.yml 关键配置

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:mem:kchatdb
    username: sa
    password:

ollama:
  base-url: http://localhost:11434
  default-model: llama3
```

### 8.2 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8080 | 服务端口 |
| ollama.base-url | http://localhost:11434 | Ollama 服务地址 |
| ollama.default-model | llama3 | 默认使用的 AI 模型 |

---

## 九、后续扩展点

### 9.1 功能扩展

- [ ] 长期记忆实现（向量数据库）
- [ ] 多模型支持
- [ ] 文件上传处理
- [ ] 用户认证授权
- [ ] 消息编辑/删除
- [ ] 对话导出

### 9.2 技术扩展

- [ ] Redis 缓存优化
- [ ] 消息队列异步处理
- [ ] 分布式部署支持
- [ ] 监控指标集成

---

## 十、启动方式

### 10.1 开发环境

```bash
cd backend
mvn spring-boot:run
```

### 10.2 打包部署

```bash
cd backend
mvn clean package
java -jar target/kchat-backend-1.0.0.jar
```

### 10.3 测试运行

```bash
cd backend
mvn test
```

---

*文档版本: 1.0*  
*创建日期: 2026-05-22*  
*适用项目: ChatGPT 风格对话应用后端*
