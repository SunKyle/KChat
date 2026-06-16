# Todo & Note 持久化方案设计文档

## 1. 需求分析

### 1.1 业务背景

当前项目中的 Todo（待办事项）和 Note（笔记）功能仅使用前端 localStorage 临时存储，存在以下问题：

| 问题点 | 影响 |
|-------|------|
| 数据易丢失 | 清除浏览器缓存或更换设备导致数据丢失 |
| 无用户隔离 | 所有用户共享同一份数据 |
| 不支持多端同步 | 无法在不同设备间同步数据 |
| 无法支持 AI 操作 | 无后端 API 供 AI 系统调用 |

### 1.2 功能需求

| 需求编号 | 需求描述 | 来源 |
|---------|---------|------|
| REQ-001 | 支持永久存储，数据持久化到数据库 | 用户要求 |
| REQ-002 | 支持用户隔离，不同用户数据完全隔离 | 用户要求 |
| REQ-003 | 支持未来 AI 自动操作，提供 RESTful API | 用户要求 |
| REQ-004 | 支持后续扩展 Memory 系统，预留关联能力 | 用户要求 |

### 1.3 非功能需求

| 需求编号 | 需求描述 | 目标值 |
|---------|---------|------|
| NFR-001 | 列表查询响应时间 | < 100ms（缓存命中） |
| NFR-002 | 单条数据查询响应时间 | < 50ms |
| NFR-003 | 创建/更新操作响应时间 | < 200ms |
| NFR-004 | 数据持久化可靠性 | 99.99% |

---

## 2. 技术方案

### 2.1 技术选型

| 层级 | 技术 | 版本 | 选型理由 |
|-----|------|------|---------|
| 数据库 | MySQL | 8.0+ | 关系型数据库，支持事务、ACID，适合结构化数据存储，与现有架构一致 |
| 缓存层 | Redis | 7.0+ | 支持快速读写、TTL过期、分布式锁，提升查询性能 |
| ORM | Spring Data JPA | 3.2+ | 简化数据访问层开发，与现有架构保持一致 |
| API风格 | RESTful | - | 标准化接口，便于前端集成和未来AI调用 |

### 2.2 架构设计

#### 2.2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端应用                                  │
│  NoteTodoPanel ── NoteList ── NoteForm ── TodoList ── TodoForm  │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP/REST
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                       API 网关层                                 │
│              NoteController / TodoController                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  NoteService  │    │  TodoService  │    │ CacheService  │
│  (业务逻辑)    │    │  (业务逻辑)    │    │  (缓存管理)    │
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│NoteRepository │    │TodoRepository │    │    Redis      │
│   (JPA)       │    │   (JPA)       │    │   (缓存)      │
└───────┬───────┘    └───────┬───────┘    └───────────────┘
        │                    │
        └────────────────────┼────────────────────┘
                             ▼
                    ┌───────────────┐
                    │   MySQL       │
                    │ (持久化存储)   │
                    └───────────────┘
```

#### 2.2.2 模块职责

| 模块 | 职责 |
|-----|------|
| **Controller** | 处理HTTP请求，参数校验，调用Service层 |
| **Service** | 业务逻辑处理，缓存管理，事务控制 |
| **Repository** | 数据访问层，基于JPA的数据库操作 |
| **CacheService** | Redis缓存管理，缓存读写与失效 |

### 2.3 数据模型设计

#### 2.3.1 Note 实体

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | UUID主键 |
| `user_id` | VARCHAR(36) | NOT NULL, INDEX | 用户标识（支持多租户） |
| `title` | VARCHAR(255) | NOT NULL | 笔记标题 |
| `content` | TEXT | | 笔记内容（支持Markdown） |
| `category` | VARCHAR(50) | DEFAULT '默认' | 分类（工作/学习/生活/默认） |
| `tags` | JSON | | 标签数组 |
| `pinned` | BOOLEAN | DEFAULT FALSE | 是否置顶 |
| `memory_id` | VARCHAR(36) | | 关联记忆ID（预留扩展） |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

#### 2.3.2 Todo 实体

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| `id` | VARCHAR(36) | PRIMARY KEY | UUID主键 |
| `user_id` | VARCHAR(36) | NOT NULL, INDEX | 用户标识 |
| `title` | VARCHAR(255) | NOT NULL | 待办标题 |
| `description` | TEXT | | 待办描述 |
| `status` | VARCHAR(20) | NOT NULL | 状态（pending/completed） |
| `priority` | VARCHAR(20) | NOT NULL | 优先级（high/medium/low） |
| `due_date` | DATETIME | | 截止日期 |
| `category` | VARCHAR(50) | DEFAULT '默认' | 分类 |
| `memory_id` | VARCHAR(36) | | 关联记忆ID（预留扩展） |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |
| `completed_at` | DATETIME | | 完成时间 |

#### 2.3.3 索引设计

**notes 表索引**:
- `idx_notes_user_id(user_id)` - 用户隔离查询
- `idx_notes_user_pinned(user_id, pinned)` - 置顶排序
- `idx_notes_user_updated(user_id, updated_at DESC)` - 时间排序

**todos 表索引**:
- `idx_todos_user_id(user_id)` - 用户隔离查询
- `idx_todos_user_status(user_id, status)` - 状态筛选
- `idx_todos_user_priority(user_id, priority)` - 优先级筛选
- `idx_todos_due_date(due_date)` - 截止日期查询

### 2.4 API 接口设计

#### 2.4.1 Note API

| API路径 | HTTP方法 | 功能描述 | Controller文件 |
|---------|---------|---------|---------------|
| `/api/notes?userId={userId}` | GET | 获取用户所有笔记 | NoteController.java |
| `/api/notes?userId={userId}&category={category}` | GET | 按分类获取笔记 | NoteController.java |
| `/api/notes?userId={userId}&keyword={keyword}` | GET | 搜索笔记 | NoteController.java |
| `/api/notes/{noteId}?userId={userId}` | GET | 获取单条笔记 | NoteController.java |
| `/api/notes?userId={userId}` | POST | 创建笔记 | NoteController.java |
| `/api/notes/{noteId}?userId={userId}` | PUT | 更新笔记 | NoteController.java |
| `/api/notes/{noteId}?userId={userId}` | DELETE | 删除笔记 | NoteController.java |

**创建笔记请求体**:
```json
{
  "title": "string (可选)",
  "content": "string (可选)",
  "category": "string (可选)",
  "tags": ["string"],
  "pinned": "boolean (可选)"
}
```

**笔记响应体**:
```json
{
  "id": "string",
  "userId": "string",
  "title": "string",
  "content": "string",
  "category": "string",
  "tags": ["string"],
  "pinned": "boolean",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

#### 2.4.2 Todo API

| API路径 | HTTP方法 | 功能描述 | Controller文件 |
|---------|---------|---------|---------------|
| `/api/todos?userId={userId}` | GET | 获取用户所有待办 | TodoController.java |
| `/api/todos?userId={userId}&status={status}` | GET | 按状态获取待办 | TodoController.java |
| `/api/todos?userId={userId}&priority={priority}` | GET | 按优先级获取待办 | TodoController.java |
| `/api/todos?userId={userId}&keyword={keyword}` | GET | 搜索待办 | TodoController.java |
| `/api/todos/overdue?userId={userId}` | GET | 获取过期待办 | TodoController.java |
| `/api/todos/{todoId}?userId={userId}` | GET | 获取单条待办 | TodoController.java |
| `/api/todos?userId={userId}` | POST | 创建待办 | TodoController.java |
| `/api/todos/{todoId}?userId={userId}` | PUT | 更新待办 | TodoController.java |
| `/api/todos/{todoId}/toggle?userId={userId}` | PATCH | 切换待办状态 | TodoController.java |
| `/api/todos/{todoId}?userId={userId}` | DELETE | 删除待办 | TodoController.java |

**创建待办请求体**:
```json
{
  "title": "string (可选)",
  "description": "string (可选)",
  "priority": "string (可选)",
  "dueDate": "datetime (可选)",
  "category": "string (可选)"
}
```

**待办响应体**:
```json
{
  "id": "string",
  "userId": "string",
  "title": "string",
  "description": "string",
  "status": "string",
  "priority": "string",
  "dueDate": "datetime",
  "category": "string",
  "createdAt": "datetime",
  "updatedAt": "datetime",
  "completedAt": "datetime"
}
```

### 2.5 缓存策略

| 缓存类型 | 缓存键格式 | TTL | 更新策略 |
|---------|-----------|-----|---------|
| 用户笔记列表 | `notes:{userId}` | 5分钟 | 创建/更新/删除时失效 |
| 用户待办列表 | `todos:{userId}` | 5分钟 | 创建/更新/删除时失效 |
| 单条笔记 | `note:{userId}:{noteId}` | 10分钟 | 更新/删除时失效 |
| 单条待办 | `todo:{userId}:{todoId}` | 10分钟 | 更新/删除时失效 |

### 2.6 数据同步机制

```
1. 前端发起请求
2. Controller 接收请求，校验 userId
3. Service 先从 Redis 获取缓存
   - 缓存命中 → 直接返回
   - 缓存未命中 → 从数据库查询 → 更新缓存 → 返回
4. 创建/更新/删除操作
   - 执行数据库操作
   - 失效相关缓存
   - 返回结果
```

---

## 3. 部署与集成方案

### 3.1 依赖与环境

| 依赖 | GroupId | ArtifactId | Version |
|-----|---------|-----------|---------|
| Spring Boot Starter Web | org.springframework.boot | spring-boot-starter-web | 3.2.x |
| Spring Boot Starter Data JPA | org.springframework.boot | spring-boot-starter-data-jpa | 3.2.x |
| Spring Boot Starter Data Redis | org.springframework.boot | spring-boot-starter-data-redis | 3.2.x |
| MySQL Connector | com.mysql | mysql-connector-j | 8.0.x |
| Lombok | org.projectlombok | lombok | 1.18.x |

### 3.2 配置说明

**application.yml 关键配置**:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/kchatdb?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf8
    driverClassName: com.mysql.cj.jdbc.Driver
    username: admin
    password: sxk1997sxk
  jpa:
    database-platform: org.hibernate.dialect.MySQLDialect
    hibernate:
      ddl-auto: update
    show-sql: true
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 60000ms
```

---

## 4. 代码安全性

### 4.1 用户隔离机制

| 层级 | 实现方式 |
|-----|---------|
| Controller | 强制要求 userId 参数 |
| Service | 验证数据所属用户 |
| Repository | 所有查询必须包含 userId 条件 |

### 4.2 输入验证

| 验证项 | 实现方式 |
|-----|---------|
| 参数校验 | 使用 `@Valid` 注解 |
| SQL注入防护 | 使用 JPA 参数化查询 |
| XSS防护 | 前端转义，后端过滤 |

### 4.3 错误处理

| 错误类型 | 处理方式 | HTTP状态码 |
|---------|---------|-----------|
| 资源不存在 | 抛出自定义异常 | 404 |
| 权限不足 | 抛出权限异常 | 403 |
| 参数错误 | 验证失败返回 | 400 |
| 服务器错误 | 全局异常处理 | 500 |

---

## 5. 扩展能力

### 5.1 AI 自动操作支持

预留 AI 操作接口：
- AI 创建笔记/待办：调用 POST `/api/notes` 或 `/api/todos`
- AI 分析待办优先级：扩展分析接口
- AI 生成待办建议：扩展建议接口

### 5.2 Memory 系统集成

预留与 LongTermMemory 的关联字段 `memory_id`，支持：
- 笔记/待办与记忆的关联
- 基于记忆的智能推荐
- 自动提取笔记内容到记忆系统

---

## 6. 验收标准

### 6.1 功能验收

| 功能 | 验收标准 |
|-----|---------|
| Note CRUD | 支持创建、读取、更新、删除笔记 |
| Todo CRUD | 支持创建、读取、更新、删除待办 |
| 用户隔离 | 不同用户数据完全隔离，无法访问他人数据 |
| 搜索功能 | 支持按关键词搜索笔记和待办 |
| 状态切换 | Todo 状态切换正常，完成时间正确记录 |
| 置顶功能 | Note 置顶/取消置顶正常 |
| 缓存机制 | 更新后缓存正确失效，下次查询重新加载 |

### 6.2 性能验收

| 指标 | 标准 |
|-----|------|
| 列表查询（缓存命中） | < 100ms |
| 单条查询 | < 50ms |
| 创建/更新操作 | < 200ms |
| 1000条数据分页 | < 150ms |

### 6.3 数据完整性

| 场景 | 验证 |
|-----|------|
| 服务重启 | 数据持久化，重启后数据不丢失 |
| 网络异常 | 前端显示错误提示，不丢失已输入数据 |
| 并发操作 | 数据一致性保证，无数据覆盖 |

---

## 7. 附录

### 7.1 数据库表 SQL

```sql
-- notes 表
CREATE TABLE notes (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    category VARCHAR(50) DEFAULT '默认',
    tags JSON,
    pinned BOOLEAN DEFAULT FALSE,
    memory_id VARCHAR(36),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_notes_user_id (user_id),
    INDEX idx_notes_user_pinned (user_id, pinned),
    INDEX idx_notes_user_updated (user_id, updated_at DESC)
);

-- todos 表
CREATE TABLE todos (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'medium',
    due_date DATETIME,
    category VARCHAR(50) DEFAULT '默认',
    memory_id VARCHAR(36),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    completed_at DATETIME,
    INDEX idx_todos_user_id (user_id),
    INDEX idx_todos_user_status (user_id, status),
    INDEX idx_todos_user_priority (user_id, priority),
    INDEX idx_todos_due_date (due_date)
);
```

### 7.2 文件清单

| 文件路径 | 说明 |
|---------|------|
| `backend/src/main/java/com/example/app/entity/Note.java` | Note 实体类 |
| `backend/src/main/java/com/example/app/entity/Todo.java` | Todo 实体类 |
| `backend/src/main/java/com/example/app/repository/NoteRepository.java` | Note 数据访问层 |
| `backend/src/main/java/com/example/app/repository/TodoRepository.java` | Todo 数据访问层 |
| `backend/src/main/java/com/example/app/service/NoteService.java` | Note 业务逻辑层 |
| `backend/src/main/java/com/example/app/service/TodoService.java` | Todo 业务逻辑层 |
| `backend/src/main/java/com/example/app/service/CacheService.java` | 缓存服务 |
| `backend/src/main/java/com/example/app/controller/NoteController.java` | Note API 控制层 |
| `backend/src/main/java/com/example/app/controller/TodoController.java` | Todo API 控制层 |
| `backend/src/main/java/com/example/app/dto/CreateNoteRequest.java` | 创建笔记请求 DTO |
| `backend/src/main/java/com/example/app/dto/UpdateNoteRequest.java` | 更新笔记请求 DTO |
| `backend/src/main/java/com/example/app/dto/CreateTodoRequest.java` | 创建待办请求 DTO |
| `backend/src/main/java/com/example/app/dto/UpdateTodoRequest.java` | 更新待办请求 DTO |
| `backend/src/main/java/com/example/app/dto/NoteDTO.java` | 笔记响应 DTO |
| `backend/src/main/java/com/example/app/dto/TodoDTO.java` | 待办响应 DTO |
| `frontend/src/api/note-todo.ts` | 前端 API 封装 |
| `frontend/src/components/note-todo/NoteTodoPanel.tsx` | 前端主组件（需修改） |