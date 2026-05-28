# KChat 长期记忆系统设计文档

---

## 1. 概述

### 1.1 需求背景

当前项目已实现短期记忆（会话级别，24小时过期），但用户希望AI能够跨会话记住重要信息。例如：

- 用户："我主要使用Java开发"
- 一个月后，AI仍能记住此信息

### 1.2 设计目标

| 目标 | 描述 |
|------|------|
| **跨会话记忆** | 记忆在不同会话间持久化 |
| **智能提取** | 自动从对话中提取重要信息 |
| **语义检索** | 支持基于语义的记忆召回 |
| **记忆管理** | 支持记忆的增删改查和过期清理 |
| **低延迟** | 不显著影响响应速度 |

### 1.3 架构定位

```
┌─────────────────────────────────────────────────────────────┐
│                     应用层 (Application)                    │
│  Controller → Service → Prompt → Model                     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     记忆层 (Memory Layer)                   │
│  LongTermMemory ←→ WorkingMemory ←→ ShortTermMemory       │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     存储层 (Storage)                        │
│  MySQL + Redis Vector Store                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 数据模型设计

### 2.1 实体设计

#### 2.1.1 LongTermMemory 实体

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键 |
| userId | VARCHAR(36) | NOT NULL, INDEX | 用户标识 |
| content | TEXT | NOT NULL | 记忆内容 |
| type | VARCHAR(30) | NOT NULL, INDEX | 记忆类型 |
| importance | INT | DEFAULT 5 | 重要性评分(1-10) |
| embedding | TEXT | NULL | 向量嵌入(JSON数组) |
| metadata | JSON | NULL | 扩展元数据 |
| sourceConversationId | VARCHAR(36) | NULL | 来源对话ID |
| sourceMessageId | VARCHAR(36) | NULL | 来源消息ID |
| createdAt | DATETIME | NOT NULL | 创建时间 |
| updatedAt | DATETIME | NOT NULL | 更新时间 |
| expiresAt | DATETIME | NULL | 过期时间 |

#### 2.1.2 记忆类型枚举

```java
public enum MemoryType {
    PROFILE,      // 用户画像
    PREFERENCE,   // 用户偏好
    PROJECT,      // 项目信息
    SKILL,        // 技能专长
    TASK,         // 待办任务
    KNOWLEDGE,    // 领域知识
    RELATION,     // 关系信息
    EVENT         // 事件记录
}
```

### 2.2 DTO 设计

#### 2.2.1 MemoryDTO

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | Long | 记忆ID |
| userId | String | 用户ID |
| content | String | 记忆内容 |
| type | String | 记忆类型 |
| importance | Integer | 重要性评分 |
| createdAt | LocalDateTime | 创建时间 |

#### 2.2.2 MemoryExtractRequest

| 字段名 | 类型 | 说明 |
|--------|------|------|
| conversationId | String | 对话ID |
| messages | List\<ChatMessageDTO\> | 消息列表 |

#### 2.2.3 MemoryRecallRequest

| 字段名 | 类型 | 说明 |
|--------|------|------|
| userId | String | 用户ID |
| query | String | 查询文本 |
| topK | Integer | 返回数量(默认5) |
| types | List\<String\> | 过滤类型(可选) |

---

## 3. 核心组件设计

### 3.1 组件架构

```
┌─────────────────────────────────────────────────────────────┐
│                   LongTermMemoryService                     │
│  ┌──────────────────┐    ┌──────────────────┐              │
│  │ MemoryExtractor  │    │ MemoryRecaller   │              │
│  │ (记忆提取)       │    │ (记忆召回)       │              │
│  └────────┬─────────┘    └────────┬─────────┘              │
│           │                       │                         │
│           ↓                       ↓                         │
│  ┌─────────────────────────────────────────────┐            │
│  │           VectorStoreWrapper                │            │
│  │  (向量存储封装：Redis + MySQL)              │            │
│  └─────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 组件职责

| 组件 | 职责 | 关键方法 |
|------|------|----------|
| **LongTermMemoryService** | 长期记忆主服务 | save, retrieve, delete |
| **MemoryExtractor** | 从对话中提取记忆 | extract |
| **MemoryRecaller** | 根据查询召回记忆 | recall |
| **VectorStoreWrapper** | 向量存储封装 | add, search, delete |

### 3.3 核心类设计

#### 3.3.1 LongTermMemoryService

```java
public interface LongTermMemoryService {
    // 保存记忆
    MemoryDTO save(MemoryDTO dto);
    
    // 批量保存
    List<MemoryDTO> saveAll(List<MemoryDTO> dtos);
    
    // 根据ID获取
    Optional<MemoryDTO> findById(Long id);
    
    // 根据用户ID查询
    List<MemoryDTO> findByUserId(String userId);
    
    // 根据类型查询
    List<MemoryDTO> findByUserIdAndType(String userId, MemoryType type);
    
    // 语义检索
    List<MemoryDTO> recall(String userId, String query, int topK);
    
    // 语义检索(带类型过滤)
    List<MemoryDTO> recall(String userId, String query, int topK, List<MemoryType> types);
    
    // 删除记忆
    void deleteById(Long id);
    
    // 清理过期记忆
    int cleanExpired();
    
    // 清理用户所有记忆
    void deleteByUserId(String userId);
}
```

#### 3.3.2 MemoryExtractor

```java
public interface MemoryExtractor {
    /**
     * 从对话中提取记忆
     * @param messages 对话消息列表
     * @return 提取的记忆列表
     */
    List<MemoryDTO> extract(List<ChatMessage> messages);
    
    /**
     * 提取并保存
     * @param conversationId 对话ID
     * @param messages 消息列表
     * @param userId 用户ID
     * @return 保存的记忆数量
     */
    int extractAndSave(String conversationId, List<ChatMessage> messages, String userId);
}
```

#### 3.3.3 MemoryRecaller

```java
public interface MemoryRecaller {
    /**
     * 语义召回记忆
     * @param userId 用户ID
     * @param query 查询文本
     * @param topK 返回数量
     * @return 相关记忆列表
     */
    List<MemoryDTO> recall(String userId, String query, int topK);
    
    /**
     * 语义召回(带过滤)
     * @param userId 用户ID
     * @param query 查询文本
     * @param topK 返回数量
     * @param types 类型过滤
     * @return 相关记忆列表
     */
    List<MemoryDTO> recall(String userId, String query, int topK, List<MemoryType> types);
}
```

---

## 4. 关键流程设计

### 4.1 记忆提取流程

```
用户对话完成
       ↓
MemoryExtractor.extract()
       ↓
┌──────────────────────────────┐
│  1. 构建提取Prompt          │
│  2. 调用模型提取信息         │
│  3. 解析JSON结果            │
└──────────────────────────────┘
       ↓
┌──────────────────────────────┐
│  重要性评估                   │
│  - 基础分(类型)              │
│  - 上下文加分                │
│  - 时间衰减                  │
└──────────────────────────────┘
       ↓
┌──────────────────────────────┐
│  去重与合并                  │
│  - 检查重复                  │
│  - 合并冲突                  │
└──────────────────────────────┘
       ↓
VectorStoreWrapper.add()
       ↓
MySQL + Redis 持久化
```

### 4.2 记忆召回流程

```
用户问题输入
       ↓
MemoryRecaller.recall()
       ↓
┌──────────────────────────────┐
│  Query Embedding            │
│  将问题转换为向量            │
└──────────────────────────────┘
       ↓
┌──────────────────────────────┐
│  Redis 向量检索              │
│  余弦相似度 Top-K            │
└──────────────────────────────┘
       ↓
┌──────────────────────────────┐
│  过滤与排序                  │
│  - importance >= 4          │
│  - 相似度排序                │
│  - 去重                     │
└──────────────────────────────┘
       ↓
返回相关记忆列表(最多5条)
```

### 4.3 Prompt 组装流程

```
┌──────────────────────────────┐     ┌─────────────────────┐
│   1. System Prompt          │     │   角色设定         │
└──────────────────────────────┘     └─────────────────────┘
                ↓
┌──────────────────────────────┐     ┌─────────────────────┐
│   2. Long-term Memory       │     │   用户画像/偏好     │
│      (召回的相关记忆)        │     │   项目信息         │
└──────────────────────────────┘     └─────────────────────┘
                ↓
┌──────────────────────────────┐     ┌─────────────────────┐
│   3. Short-term Memory      │     │   最近20条消息     │
└──────────────────────────────┘     └─────────────────────┘
                ↓
┌──────────────────────────────┐     ┌─────────────────────┐
│   4. 当前问题                │     │   用户输入         │
└──────────────────────────────┘     └─────────────────────┘
```

---

## 5. 向量存储设计

### 5.1 存储架构

```
┌─────────────────────────────────────────────────────────────┐
│                    MySQL (关系型存储)                       │
│  - LongTermMemory 实体完整数据                              │
│  - 支持复杂查询(类型/用户/时间)                              │
└─────────────────────────────────────────────────────────────┘
                              ↓ 同步
┌─────────────────────────────────────────────────────────────┐
│                  Redis Vector Store (向量存储)              │
│  - Key: kchat:memory:embedding:{userId}                    │
│  - Value: 向量数据 + 记忆ID                                │
│  - 支持余弦相似度检索                                       │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 向量生成方案

| 方案 | 说明 | 优势 | 劣势 |
|------|------|------|------|
| **LangChain4j EmbeddingModel** | 使用内置嵌入模型 | 轻量级，无需额外部署 | 模型质量一般 |
| **Ollama Embedding** | 调用Ollama的嵌入端点 | 统一模型，质量高 | 额外API调用 |
| **外部API** | OpenAI/text-embedding-3-small | 质量最高 | 成本高 |

**当前推荐方案**：LangChain4j EmbeddingModel（all-MiniLM-L6-v2）

### 5.3 检索算法

```
相似度 = cosine(query_embedding, memory_embedding)
阈值 = 0.5

召回条件：
1. 相似度 >= 阈值
2. importance >= 4
3. 未过期

返回：按相似度降序排列，最多返回5条
```

---

## 6. API 接口设计

### 6.1 接口列表

| 接口 | HTTP方法 | 路径 | 说明 |
|------|----------|------|------|
| 创建记忆 | POST | /api/memories | 创建单条记忆 |
| 批量创建 | POST | /api/memories/batch | 批量创建记忆 |
| 获取记忆列表 | GET | /api/memories | 获取用户记忆列表 |
| 获取单条记忆 | GET | /api/memories/{id} | 获取单条记忆 |
| 更新记忆 | PUT | /api/memories/{id} | 更新记忆 |
| 删除记忆 | DELETE | /api/memories/{id} | 删除记忆 |
| 语义检索 | POST | /api/memories/recall | 语义检索记忆 |
| 清理过期 | DELETE | /api/memories/cleanup | 清理过期记忆 |

### 6.2 接口详细设计

#### 6.2.1 创建记忆

```http
POST /api/memories
Content-Type: application/json

{
  "userId": "user-123",
  "content": "用户使用Java开发",
  "type": "PROFILE",
  "importance": 8
}
```

**响应**：
```json
{
  "id": 1,
  "userId": "user-123",
  "content": "用户使用Java开发",
  "type": "PROFILE",
  "importance": 8,
  "createdAt": "2024-01-01T12:00:00"
}
```

#### 6.2.2 语义检索

```http
POST /api/memories/recall
Content-Type: application/json

{
  "userId": "user-123",
  "query": "我上次提到的项目进展如何",
  "topK": 5,
  "types": ["PROJECT", "TASK"]
}
```

**响应**：
```json
{
  "memories": [
    {
      "id": 2,
      "userId": "user-123",
      "content": "正在开发电商平台",
      "type": "PROJECT",
      "importance": 7,
      "score": 0.85
    }
  ]
}
```

---

## 7. 配置与部署

### 7.1 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| memory.long-term.enabled | true | 是否启用长期记忆 |
| memory.long-term.max-recall | 5 | 最大召回数量 |
| memory.long-term.min-importance | 4 | 最小重要性阈值 |
| memory.long-term.similarity-threshold | 0.5 | 相似度阈值 |
| memory.long-term.extract-interval | 10 | 提取间隔(消息数) |

### 7.2 依赖服务

| 服务 | 版本 | 说明 |
|------|------|------|
| Redis | 7.0+ | 向量存储 |
| MySQL | 8.0+ | 关系型存储 |
| LangChain4j | 0.35.0 | 向量嵌入 |

---

## 8. 安全性考虑

### 8.1 用户隔离

- 所有操作必须携带 userId
- Repository 查询必须包含 userId 条件
- 禁止跨用户访问

### 8.2 数据清理

- 提供用户级别的清理接口
- 定期清理过期记忆(定时任务)
- 支持按类型清理

### 8.3 敏感信息

- 记忆内容可能包含敏感信息
- 建议添加内容审核机制
- 支持敏感信息脱敏存储

---

## 9. 监控与日志

### 9.1 监控指标

| 指标 | 说明 |
|------|------|
| memory_extract_count | 记忆提取次数 |
| memory_save_count | 记忆保存次数 |
| memory_recall_count | 记忆召回次数 |
| memory_recall_hit_rate | 召回命中率 |
| memory_avg_importance | 平均重要性评分 |
| memory_storage_size | 存储大小 |

### 9.2 日志记录

| 场景 | 日志级别 | 内容 |
|------|----------|------|
| 记忆提取成功 | INFO | 提取数量、耗时 |
| 记忆提取失败 | WARN | 失败原因 |
| 记忆召回 | DEBUG | 召回数量、相似度 |
| 记忆清理 | INFO | 清理数量 |

---

## 附录：设计检查清单

- [ ] 实体与DTO设计完成
- [ ] 核心服务接口设计完成
- [ ] 数据库表设计完成
- [ ] API接口设计完成
- [ ] 向量存储方案设计完成
- [ ] 安全性考虑完成
- [ ] 监控指标设计完成

---

*文档版本：v1.0*  
*设计日期：2026-05-28*  
*适用项目：KChat Backend*
