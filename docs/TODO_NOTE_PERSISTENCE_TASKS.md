# Todo & Note 持久化方案 - 任务执行清单

## 任务概述

本任务清单基于 [TODO_NOTE_PERSISTENCE_SPEC.md](TODO_NOTE_PERSISTENCE_SPEC.md) 设计文档，旨在完成 Todo 和 Note 功能的后端持久化改造。

## 项目进度概览

| 阶段 | 任务数 | 状态 | 预计耗时 |
|-----|-------|------|---------|
| 第一阶段：数据库与实体层 | 5 | ✅ 完成 | 2天 |
| 第二阶段：Service 层 | 4 | ✅ 完成 | 2天 |
| 第三阶段：Controller 层 | 3 | ✅ 完成 | 1天 |
| 第四阶段：前端集成 | 2 | ✅ 完成 | 2天 |
| 第五阶段：测试与优化 | 3 | ⬜ 未开始 | 3天 |
| **总计** | **17** | **14/17 完成** | **10天** |

---

## 第一阶段：数据库与实体层

### 任务 1.1：创建数据库表

**描述**：在 MySQL 数据库中创建 `notes` 和 `todos` 表

**前置条件**：MySQL 服务已启动，数据库 `kchatdb` 已创建

**执行步骤**：
1. 连接数据库：`mysql -uadmin -p sxk1997sxk kchatdb`
2. 执行 SQL 创建 `notes` 表
3. 执行 SQL 创建 `todos` 表
4. 验证表结构

**输出**：数据库表创建成功

**责任人**：后端开发

**状态**：✅ 已完成 (创建 V3__create_notes_todos_tables.sql 迁移脚本)

---

### 任务 1.2：创建 Note 实体类

**描述**：创建 JPA 实体类 `Note.java`

**文件路径**：`backend/src/main/java/com/example/app/entity/Note.java`

**关键属性**：
- id (String, UUID)
- userId (String)
- title (String)
- content (String)
- category (String)
- tags (List<String>)
- pinned (Boolean)
- memoryId (String)
- createdAt (LocalDateTime)
- updatedAt (LocalDateTime)

**要求**：
- 使用 Lombok 注解
- 添加 `@PrePersist` 和 `@PreUpdate` 自动维护时间戳
- 添加索引注解

**输出**：`Note.java` 实体类

**责任人**：后端开发

**状态**：✅ 已完成 (创建 Note.java 实体类)

---

### 任务 1.3：创建 Todo 实体类

**描述**：创建 JPA 实体类 `Todo.java`

**文件路径**：`backend/src/main/java/com/example/app/entity/Todo.java`

**关键属性**：
- id (String, UUID)
- userId (String)
- title (String)
- description (String)
- status (String)
- priority (String)
- dueDate (LocalDateTime)
- category (String)
- memoryId (String)
- createdAt (LocalDateTime)
- updatedAt (LocalDateTime)
- completedAt (LocalDateTime)

**要求**：
- 使用 Lombok 注解
- 添加 `@PrePersist` 和 `@PreUpdate` 自动维护时间戳
- 添加索引注解

**输出**：`Todo.java` 实体类

**责任人**：后端开发

**状态**：✅ 已完成 (创建 Todo.java 实体类)

---

### 任务 1.4：创建 NoteRepository

**描述**：创建 Note 数据访问层

**文件路径**：`backend/src/main/java/com/example/app/repository/NoteRepository.java`

**方法列表**：
| 方法名 | 功能 |
|-------|------|
| `findByUserIdOrderByPinnedDescUpdatedAtDesc` | 按用户ID查询，置顶优先，时间倒序 |
| `findByUserId` | 分页查询用户笔记 |
| `findByUserIdAndPinnedTrueOrderByUpdatedAtDesc` | 查询置顶笔记 |
| `findByUserIdAndCategory` | 按分类查询 |
| `searchByUserIdAndKeyword` | 关键词搜索 |
| `findByIdAndUserId` | 按ID和用户ID查询 |
| `deleteByIdAndUserId` | 删除指定用户的笔记 |
| `countByUserId` | 统计用户笔记数量 |

**输出**：`NoteRepository.java`

**责任人**：后端开发

**状态**：✅ 已完成 (创建 NoteRepository.java)

---

### 任务 1.5：创建 TodoRepository

**描述**：创建 Todo 数据访问层

**文件路径**：`backend/src/main/java/com/example/app/repository/TodoRepository.java`

**方法列表**：
| 方法名 | 功能 |
|-------|------|
| `findByUserIdOrderByStatusAscPriorityDescUpdatedAtDesc` | 按状态、优先级排序查询 |
| `findByUserId` | 分页查询用户待办 |
| `findByUserIdAndStatus` | 按状态查询 |
| `findByUserIdAndPriority` | 按优先级查询 |
| `findByUserIdAndCategory` | 按分类查询 |
| `searchByUserIdAndKeyword` | 关键词搜索 |
| `findOverdueTodos` | 查询过期待办 |
| `findByIdAndUserId` | 按ID和用户ID查询 |
| `deleteByIdAndUserId` | 删除指定用户的待办 |
| `countByUserIdAndStatus` | 统计指定状态待办数量 |

**输出**：`TodoRepository.java`

**责任人**：后端开发

**状态**：✅ 已完成 (创建 TodoRepository.java)

---

## 第二阶段：Service 层

### 任务 2.1：创建 DTO 类

**描述**：创建请求和响应 DTO

**文件清单**：
| 文件路径 | 说明 |
|---------|------|
| `backend/src/main/java/com/example/app/dto/NoteDTO.java` | 笔记响应 DTO |
| `backend/src/main/java/com/example/app/dto/TodoDTO.java` | 待办响应 DTO |
| `backend/src/main/java/com/example/app/dto/CreateNoteRequest.java` | 创建笔记请求 |
| `backend/src/main/java/com/example/app/dto/UpdateNoteRequest.java` | 更新笔记请求 |
| `backend/src/main/java/com/example/app/dto/CreateTodoRequest.java` | 创建待办请求 |
| `backend/src/main/java/com/example/app/dto/UpdateTodoRequest.java` | 更新待办请求 |

**要求**：使用 Lombok 注解，字段与实体对应

**输出**：6 个 DTO 类文件

**责任人**：后端开发

**状态**：✅ 已完成 (创建 6 个 DTO 类文件)

---

### 任务 2.2：创建 CacheService

**描述**：创建 Redis 缓存服务

**文件路径**：`backend/src/main/java/com/example/app/service/CacheService.java`

**方法列表**：
| 方法名 | 功能 |
|-------|------|
| `cacheNotes` | 缓存笔记列表 |
| `getCachedNotes` | 获取缓存的笔记列表 |
| `cacheNote` | 缓存单条笔记 |
| `getCachedNote` | 获取缓存的单条笔记 |
| `invalidateNoteCache` | 失效笔记缓存 |
| `cacheTodos` | 缓存待办列表 |
| `getCachedTodos` | 获取缓存的待办列表 |
| `cacheTodo` | 缓存单条待办 |
| `getCachedTodo` | 获取缓存的单条待办 |
| `invalidateTodoCache` | 失效待办缓存 |

**要求**：使用 StringRedisTemplate 和 ObjectMapper 进行序列化

**输出**：`CacheService.java`

**责任人**：后端开发

**状态**：✅ 已完成 (创建 CacheService.java)

---

### 任务 2.3：创建 NoteService

**描述**：创建笔记业务逻辑服务

**文件路径**：`backend/src/main/java/com/example/app/service/NoteService.java`

**方法列表**：
| 方法名 | 功能 | 缓存策略 |
|-------|------|---------|
| `getAllNotes` | 获取所有笔记 | 先查缓存，未命中查DB |
| `getNoteById` | 获取单条笔记 | 先查缓存，未命中查DB |
| `createNote` | 创建笔记 | 失效列表缓存 |
| `updateNote` | 更新笔记 | 失效相关缓存 |
| `deleteNote` | 删除笔记 | 失效相关缓存 |
| `searchNotes` | 搜索笔记 | 不缓存 |
| `getNotesByCategory` | 按分类查询 | 不缓存 |

**要求**：
- 使用 `@Transactional` 注解
- 集成缓存服务
- 添加日志记录

**输出**：`NoteService.java`

**责任人**：后端开发

**状态**：✅ 已完成 (创建 NoteService.java)

---

### 任务 2.4：创建 TodoService

**描述**：创建待办业务逻辑服务

**文件路径**：`backend/src/main/java/com/example/app/service/TodoService.java`

**方法列表**：
| 方法名 | 功能 | 缓存策略 |
|-------|------|---------|
| `getAllTodos` | 获取所有待办 | 先查缓存，未命中查DB |
| `getTodoById` | 获取单条待办 | 先查缓存，未命中查DB |
| `createTodo` | 创建待办 | 失效列表缓存 |
| `updateTodo` | 更新待办 | 失效相关缓存 |
| `toggleTodoStatus` | 切换待办状态 | 失效相关缓存 |
| `deleteTodo` | 删除待办 | 失效相关缓存 |
| `searchTodos` | 搜索待办 | 不缓存 |
| `getTodosByStatus` | 按状态查询 | 不缓存 |
| `getTodosByPriority` | 按优先级查询 | 不缓存 |
| `getOverdueTodos` | 获取过期待办 | 不缓存 |

**要求**：
- 使用 `@Transactional` 注解
- 集成缓存服务
- 添加日志记录

**输出**：`TodoService.java`

**责任人**：后端开发

**状态**：⬜ 未开始

---

## 第三阶段：Controller 层

### 任务 3.1：创建 NoteController

**描述**：创建笔记 API 控制器

**文件路径**：`backend/src/main/java/com/example/app/controller/NoteController.java`

**API 端点**：
| 路径 | 方法 | 功能 |
|-----|------|------|
| `/api/notes` | GET | 获取笔记列表 |
| `/api/notes/{noteId}` | GET | 获取单条笔记 |
| `/api/notes` | POST | 创建笔记 |
| `/api/notes/{noteId}` | PUT | 更新笔记 |
| `/api/notes/{noteId}` | DELETE | 删除笔记 |

**要求**：
- 使用 `@CrossOrigin` 支持跨域
- 所有接口必须接收 `userId` 参数
- 统一异常处理

**输出**：`NoteController.java`

**责任人**：后端开发

**状态**：✅ 已完成 (创建 NoteController.java)

---

### 任务 3.2：创建 TodoController

**描述**：创建待办 API 控制器

**文件路径**：`backend/src/main/java/com/example/app/controller/TodoController.java`

**API 端点**：
| 路径 | 方法 | 功能 |
|-----|------|------|
| `/api/todos` | GET | 获取待办列表 |
| `/api/todos/overdue` | GET | 获取过期待办 |
| `/api/todos/{todoId}` | GET | 获取单条待办 |
| `/api/todos` | POST | 创建待办 |
| `/api/todos/{todoId}` | PUT | 更新待办 |
| `/api/todos/{todoId}/toggle` | PATCH | 切换状态 |
| `/api/todos/{todoId}` | DELETE | 删除待办 |

**要求**：
- 使用 `@CrossOrigin` 支持跨域
- 所有接口必须接收 `userId` 参数
- 统一异常处理

**输出**：`TodoController.java`

**责任人**：后端开发

**状态**：✅ 已完成 (创建 TodoController.java)

---

### 任务 3.3：配置跨域与异常处理

**描述**：确保全局跨域配置和异常处理正确

**检查项**：
1. WebConfig 中配置了正确的跨域规则
2. GlobalExceptionHandler 能够处理业务异常
3. 返回统一的错误响应格式

**输出**：配置验证通过

**责任人**：后端开发

**状态**：✅ 已完成 (更新 WebConfig 支持 PATCH，增强 GlobalExceptionHandler)

---

## 第四阶段：前端集成

### 任务 4.1：创建前端 API 封装

**描述**：创建前端 API 调用封装

**文件路径**：`frontend/src/api/note-todo.ts`

**API 方法列表**：

**noteApi**:
| 方法名 | 功能 |
|-------|------|
| `getAll` | 获取所有笔记 |
| `getById` | 获取单条笔记 |
| `create` | 创建笔记 |
| `update` | 更新笔记 |
| `delete` | 删除笔记 |
| `search` | 搜索笔记 |
| `getByCategory` | 按分类查询 |

**todoApi**:
| 方法名 | 功能 |
|-------|------|
| `getAll` | 获取所有待办 |
| `getById` | 获取单条待办 |
| `create` | 创建待办 |
| `update` | 更新待办 |
| `toggle` | 切换状态 |
| `delete` | 删除待办 |
| `search` | 搜索待办 |
| `getByStatus` | 按状态查询 |
| `getByPriority` | 按优先级查询 |
| `getOverdue` | 获取过期待办 |

**输出**：`frontend/src/api/note-todo.ts`

**责任人**：前端开发

**状态**：✅ 已完成 (创建 frontend/src/api/note-todo.ts)

---

### 任务 4.2：修改 NoteTodoPanel 组件

**描述**：修改前端组件使用新的后端 API

**文件路径**：`frontend/src/components/note-todo/NoteTodoPanel.tsx`

**修改内容**：
1. 导入新的 API 模块
2. 替换 localStorage 为 API 调用
3. 添加数据加载状态
4. 添加错误处理
5. 修改初始化逻辑（从 API 加载数据）

**关键修改点**：
- `useEffect` 初始化数据加载
- `handleCreateNote` 调用 API
- `handleUpdateNote` 调用 API
- `handleDeleteNote` 调用 API
- `handleCreateTodo` 调用 API
- `handleUpdateTodo` 调用 API
- `handleDeleteTodo` 调用 API
- `handleToggleTodo` 调用 API

**输出**：修改后的 `NoteTodoPanel.tsx`

**责任人**：前端开发

**状态**：✅ 已完成 (将 localStorage 替换为 API 调用，添加 loading 状态和错误处理)

---

## 第五阶段：测试与优化

### 任务 5.1：单元测试

**描述**：编写单元测试验证各层功能

**测试文件**：
| 文件路径 | 测试内容 |
|---------|---------|
| `backend/src/test/java/com/example/app/service/NoteServiceTest.java` | NoteService 测试 |
| `backend/src/test/java/com/example/app/service/TodoServiceTest.java` | TodoService 测试 |
| `backend/src/test/java/com/example/app/service/CacheServiceTest.java` | CacheService 测试 |

**测试覆盖**：
- CRUD 操作
- 用户隔离验证
- 缓存机制验证
- 异常处理

**输出**：测试通过报告

**责任人**：后端开发

**状态**：⬜ 未开始

---

### 任务 5.2：集成测试

**描述**：端到端集成测试

**测试场景**：
| 场景 | 描述 |
|-----|------|
| 笔记 CRUD | 创建、读取、更新、删除笔记 |
| 待办 CRUD | 创建、读取、更新、删除待办 |
| 用户隔离 | 不同用户数据隔离 |
| 状态切换 | 待办状态切换 |
| 置顶功能 | 笔记置顶/取消置顶 |
| 搜索功能 | 关键词搜索 |
| 缓存机制 | 更新后缓存失效 |

**输出**：集成测试通过报告

**责任人**：测试人员

**状态**：⬜ 未开始

---

### 任务 5.3：性能测试与优化

**描述**：性能测试并优化

**测试指标**：
| 指标 | 目标 |
|-----|------|
| 列表查询（缓存命中） | < 100ms |
| 单条查询 | < 50ms |
| 创建/更新操作 | < 200ms |
| 并发 100 用户 | 无错误 |

**优化项**：
- 索引优化
- 缓存策略调整
- 连接池配置优化

**输出**：性能测试报告和优化建议

**责任人**：后端开发

**状态**：⬜ 未开始

---

## 验收检查清单

### 功能验收 ✅ ⬜

| 功能 | 验收标准 | 状态 |
|-----|---------|------|
| Note CRUD | 支持创建、读取、更新、删除笔记 | ⬜ |
| Todo CRUD | 支持创建、读取、更新、删除待办 | ⬜ |
| 用户隔离 | 不同用户数据完全隔离 | ⬜ |
| 搜索功能 | 支持按关键词搜索笔记和待办 | ⬜ |
| 状态切换 | Todo 状态切换正常 | ⬜ |
| 置顶功能 | Note 置顶/取消置顶正常 | ⬜ |
| 缓存机制 | 更新后缓存正确失效 | ⬜ |

### 性能验收 ✅ ⬜

| 指标 | 标准 | 状态 |
|-----|------|------|
| 列表查询（缓存命中） | < 100ms | ⬜ |
| 单条查询 | < 50ms | ⬜ |
| 创建/更新操作 | < 200ms | ⬜ |
| 1000条数据分页 | < 150ms | ⬜ |

### 数据完整性 ✅ ⬜

| 场景 | 验证 | 状态 |
|-----|------|------|
| 服务重启 | 数据持久化，重启后数据不丢失 | ⬜ |
| 网络异常 | 前端显示错误提示 | ⬜ |
| 并发操作 | 数据一致性保证 | ⬜ |

---

## 依赖关系图

```
任务依赖关系：

第一阶段
├── 1.1 创建数据库表
│   └── 1.2 创建 Note 实体类
│   └── 1.3 创建 Todo 实体类
│       └── 1.4 创建 NoteRepository
│       └── 1.5 创建 TodoRepository

第二阶段
├── 2.1 创建 DTO 类
│   └── 2.2 创建 CacheService
│       └── 2.3 创建 NoteService
│       └── 2.4 创建 TodoService

第三阶段
├── 3.1 创建 NoteController
├── 3.2 创建 TodoController
└── 3.3 配置跨域与异常处理

第四阶段
├── 4.1 创建前端 API 封装
│   └── 4.2 修改 NoteTodoPanel 组件

第五阶段
├── 5.1 单元测试
├── 5.2 集成测试
└── 5.3 性能测试与优化
```

---

## 里程碑

| 里程碑 | 完成条件 | 预期日期 |
|-------|---------|---------|
| M1: 数据库与实体层完成 | 任务 1.1-1.5 完成 | D+2 |
| M2: Service 层完成 | 任务 2.1-2.4 完成 | D+4 |
| M3: Controller 层完成 | 任务 3.1-3.3 完成 | D+5 |
| M4: 前端集成完成 | 任务 4.1-4.2 完成 | D+7 |
| M5: 测试与优化完成 | 任务 5.1-5.3 完成 | D+10 |

---

**文档版本**: v1.0  
**创建日期**: 2026-06-16  
**最后更新**: 2026-06-16  
**作者**: 技术团队