# 记忆召回流水线改造方案（Memory Retrieval Pipeline）

> 版本 1.0 · 2026-08-11
> 基于 KChat 后端当前实现的系统性改造设计

---

## 一、问题诊断

### 1.1 现状概述

KChat 当前的长期记忆召回是**单级、单路、无评估**的简单流水线：

```
用户 query → 向量检索(topK) → 格式化 → 注入 System Prompt
```

### 1.2 核心问题

| 编号 | 问题                    | 表现                                            | 根因                           |
| ---- | ----------------------- | ----------------------------------------------- | ------------------------------ |
| P1   | **召回关联度低**        | 问"Java 是什么"召回"KChat 用 Java"而非技术知识  | query 是裸输入，无语义扩展     |
| P2   | **无关记忆污染**        | 独立问题被注入不相关的历史记忆                  | 无意图门控，所有问题都注入记忆 |
| P3   | **记忆语义重复**        | "用户用 Java" 与 "用户技术栈是 Java" 被存为两条 | 去重仅靠精确字符串匹配         |
| P4   | **短记忆裁剪切断指代**  | 多轮对话后 token 裁剪切掉开头指代对象           | 裁剪策略仅从后往前             |
| P5   | **空/UNKNOWN 消息污染** | 日志可见 UNKNOWN 类型空消息进入上下文           | 消息持久化/类型映射缺陷        |
| P6   | **降级提取质量低**      | LLM 提取失败后规则提取产生大量噪声              | 规则提取过于激进，无质量校验   |

### 1.3 日志证据

```
[System Prompt 注入的记忆]
  - [2026-08-06] 用户是一名程序员（置信度 95%）
  - [2026-08-06] 用户正在开发个人项目kchat（置信度 95%）
  - [2026-08-06] 用户正在了解大模型多模态能力（置信度 85%）

[用户问] hello
[AI 答] 你好，Kyle！我是 KChat 智能助手

→ "hello" 不需要注入"用户正在了解大模型多模态能力"
→ 但当前实现无条件注入所有召回的记忆
```

---

## 二、目标架构：Memory Retrieval Pipeline

### 2.1 设计理念

Memory Retrieval Pipeline（MRP）是**多阶段、多策略、可评估**的召回流水线：

1. **理解意图** — 先读懂用户要什么，再决定召回什么
2. **多路并行** — 向量 + 关键词 + 图谱，互补召回
3. **精排筛选** — 粗召回 → 精排，确保质量
4. **动态门控** — 不是所有问题都需要记忆
5. **结构化组装** — 按优先级分级注入

### 2.2 流水线全景

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Memory Retrieval Pipeline                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌── Stage 1: Query Understanding ──────────────────────────────────────┐  │
│  │  输入: "Java 是什么"                                                │  │
│  │  ├── 意图分类: KNOWLEDGE_QUERY                                       │  │
│  │  ├── 指代消解: 无代词                                                │  │
│  │  ├── Query 改写: "Java 编程语言，用户技术栈相关"                      │  │
│  │  └── 需要的记忆类型: [KNOWLEDGE, SKILL, PROJECT]                    │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│         │                                                                   │
│         ▼                                                                   │
│  ┌── Stage 2: Memory Selection ────────────────────────────────────────┐  │
│  │  根据意图筛选可召回的记忆类型                                        │  │
│  │  KNOWLEDGE_QUERY → [KNOWLEDGE, SKILL, PROJECT]                      │  │
│  │  排除: [PREFERENCE, RELATION, EVENT]                                │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│         │                                                                   │
│         ▼                                                                   │
│  ┌── Stage 3: Multi-Strategy Retrieval ────────────────────────────────┐  │
│  │  ┌─ Dense (向量检索) ─┐  ┌─ Sparse (关键词) ─┐  ┌─ Temporal ─┐    │  │
│  │  │  "Java" → 0.91     │  │  "Java" 全匹配   │  │ 最近7天    │    │  │
│  │  │  "技术栈" → 0.82   │  │ 命中3条          │  │ 优先       │    │  │
│  │  └────────────────────┘  └────────────────┘  └────────────┘    │  │
│  │  ↓ 合并 → 去重 → 粗排 (top 20)                                     │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│         │                                                                   │
│         ▼                                                                   │
│  ┌── Stage 4: Relevance Ranking ────────────────────────────────────────┐  │
│  │  Cross-Encoder 精排: query + candidate → 相关性打分                 │  │
│  │  "KChat 用 Java 实现" → 0.95                                        │  │
│  │  "用户技术栈是 Java" → 0.88                                         │  │
│  │  "用户正在了解多模态" → 0.32  ← 被淘汰                             │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│         │                                                                   │
│         ▼                                                                   │
│  ┌── Stage 5: Memory Gating ───────────────────────────────────────────┐  │
│  │  判定: 当前 query 是否需要注入记忆？                                 │  │
│  │  KNOWLEDGE_QUERY → ✅ 需要                                          │  │
│  │  GREETING → ❌ 不需要                                               │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│         │                                                                   │
│         ▼                                                                   │
│  ┌── Stage 6: Context Assembly ────────────────────────────────────────┐  │
│  │  按优先级组装:                                                       │  │
│  │  ┌─ L1: 用户档案 (PROFILE) — 始终注入                               │  │
│  │  ├─ L2: 当前问题相关记忆 — 动态注入                                 │  │
│  │  └─ L3: 用户偏好 (PREFERENCE) — 可选注入                            │  │
│  │  格式: 结构化摘要 + 原文引用                                         │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 各 Stage 详细设计

---

#### Stage 1: Query Understanding — 查询理解

**目标**：在召回前先"读懂"用户 query，产出结构化的召回计划。

**输入**：用户原始 query（如 `"Java 是什么"`）

**输出**：
```java
public record QueryAnalysisResult(
    IntentType intentType,           // KNOWLEDGE_QUERY / PROFILE_QUERY / TASK_EXECUTION / ...
    Map<String, Double> keywords,    // {"Java": 1.0, "编程语言": 0.8}
    String rewrittenQuery,          // "Java 编程语言，用户技术栈相关"
    Set<MemoryType> requiredTypes,  // {KNOWLEDGE, SKILL, PROJECT}
    Set<MemoryType> excludedTypes,  // {PREFERENCE, RELATION, EVENT}
    boolean requiresMemory          // 是否需要记忆
) {}
```

**实现方式**：

| 方案         | 描述                                                                             | 成本                  | 精度 |
| ------------ | -------------------------------------------------------------------------------- | --------------------- | ---- |
| **规则匹配** | 关键词 + 正则：检测代词/指代词 → PROFILE_QUERY；检测技术关键词 → KNOWLEDGE_QUERY | 零 LLM 调用           | 中等 |
| **轻量 LLM** | 用小模型（如 Groq flash）一次性输出 JSON 结构                                    | 1 次调用 ≈ 200 tokens | 高   |
| **混合方案** | 规则先判，不确定时调 LLM                                                         | 平均 0.3 次调用       | 高   |

**推荐**：混合方案。规则覆盖 80% 场景，20% 疑难场景调 LLM。

**规则映射表**：

| 触发模式                    | 意图类型          | 需要的记忆类型   |
| --------------------------- | ----------------- | ---------------- |
| 代词（这个/那个/刚才/之前） | CONTEXT_DEPENDENT | 全部             |
| "你叫什么"/"我叫"           | PROFILE_QUERY     | PROFILE          |
| "用什么技术"/"怎么实现"     | KNOWLEDGE_QUERY   | KNOWLEDGE, SKILL |
| "总结"/"翻译"/"处理"        | TASK_EXECUTION    | EVENT, FACT      |
| 问候/闲聊                   | CHAT_SMALLTALK    | 仅 PROFILE(昵称) |
| 其他                        | GENERAL           | 动态判断         |

---

#### Stage 2: Memory Selection — 记忆选择

**目标**：根据意图类型，只召回相关类别的记忆。

**记忆类型与意图映射**：

```
记忆类型: PROFILE | PREFERENCE | PROJECT | SKILL | TASK | KNOWLEDGE | RELATION | EVENT

KNOWLEDGE_QUERY:  ✓SKILL  ✓KNOWLEDGE  ✓PROJECT  ✗PROFILE  ✗PREFERENCE  ✗RELATION  ✗EVENT
PROFILE_QUERY:    ✓PROFILE  ✓PREFERENCE  ✗其他全部
TASK_EXECUTION:   ✓TASK  ✓PROJECT  ✓EVENT  ✗PROFILE  ✗PREFERENCE  ✗KNOWLEDGE
CHAT_SMALLTALK:   ✓PROFILE(仅昵称)  ✗其他全部
CONTEXT_DEPENDENT: 全部类型（但优先 PROFILE + EVENT）
```

**实现位置**：`LongTermMemoryStage` 中，根据 `QueryAnalysisResult.requiredTypes` 构建带类型过滤的召回。

**代码示意**：
```java
// 改造前
List<MemoryDTO> memories = longTermMemoryService.recall(userId, query, 5, minScore);

// 改造后
List<MemoryDTO> memories = longTermMemoryService.recall(
    userId, rewrittenQuery, 10, minScore, requiredTypes);
//                                                          ^^^^^^^^^^^^^  新增类型过滤
```

---

#### Stage 3: Multi-Strategy Retrieval — 多路召回

**目标**：多路并行召回，取并集后精排，提升召回率。

**三条召回路径**：

**路径 1: Dense Retrieval（向量检索）**
- 用 rewrittenQuery 做向量检索
- 优势：语义相似（"用 Java" ≈ "技术栈是 Java"）
- 已有实现：`VectorStoreWrapper.searchWithScore`

**路径 2: Sparse Retrieval（关键词检索）**
- 用 keywords 做 Redis 关键词匹配
- 优势：精确匹配（"KChat" 就是 KChat）
- 实现方式：
  ```java
  // Redis 倒排索引
  // memory:keyword:KChat → Set<memoryId>
  // memory:keyword:Java → Set<memoryId>
  Set<Long> results = new HashSet<>();
  for (String keyword : keywords) {
      Set<Long> ids = redisTemplate.opsForSet()
          .members("memory:keyword:" + keyword);
      if (ids != null) results.addAll(ids);
  }
  ```

**路径 3: Temporal Retrieval（时间衰减）**
- 优先召回最近 7 天的记忆
- 优势：时效性高的记忆更相关
- 实现方式：按 `updatedAt` 排序，近 7 天的记忆加权

**合并策略**：
```
dense_results (top 10, 带分)
  + sparse_results (关键词匹配，无分)
  + temporal_results (近7天，加权)
  → Set 去重
  → 粗排（按 dense 分 + temporal 权重）
  → top 20 输出给 Stage 4
```

---

#### Stage 4: Relevance Ranking — 相关性精排

**目标**：用 Cross-Encoder 对粗召回结果做精排，提升精度。

**Cross-Encoder 原理**：

```
Bi-Encoder（当前方案）:
  query → [embedding] → 384维
  candidate → [embedding] → 384维
  similarity = cosine(query_vec, candidate_vec)
  问题：两个文本独立编码，无法捕捉精确匹配关系

Cross-Encoder（精排方案）:
  [query + candidate] → [Transformer] → score
  优势：联合编码，能捕捉 "Java" vs "Java" 的精确匹配
```

**实现方式**：

| 方案                | 描述                      | 适用场景           |
| ------------------- | ------------------------- | ------------------ |
| **BGE-Reranker**    | 用开源重排模型            | 精度高，需要 GPU   |
| **LLM-as-Reranker** | 用小模型 API 打分         | 无需 GPU，精度略低 |
| **规则精排**        | 关键词匹配 + 时间衰减加权 | 零成本，精度最低   |

**推荐**：先实现规则精排，后续引入 BGE-Reranker。

**规则精排示意**：
```
score = 0.4 * dense_similarity     // 向量相似度
      + 0.3 * keyword_match_score   // 关键词匹配数 / 总关键词数
      + 0.2 * temporal_decay        // exp(-days / 30)
      + 0.1 * importance_normalized // importance / 10
```

---

#### Stage 5: Memory Gating — 记忆门控

**目标**：判定当前 query 是否真的需要注入记忆。

**门控决策表**：

| 意图类型          | 需要记忆 | 注入策略            |
| ----------------- | -------- | ------------------- |
| KNOWLEDGE_QUERY   | ✅ 是     | 注入相关类型        |
| PROFILE_QUERY     | ✅ 是     | 强制注入 PROFILE    |
| TASK_EXECUTION    | ✅ 是     | 注入 TASK + PROJECT |
| CONTEXT_DEPENDENT | ✅ 是     | 注入全部类型        |
| CHAT_SMALLTALK    | ❌ 否     | 跳过                |
| GREETING          | ❌ 否     | 跳过                |
| MATH_CALCULATION  | ❌ 否     | 跳过                |

**实现位置**：`LongTermMemoryStage` 入口处，根据 `QueryAnalysisResult.requiresMemory` 决定是否跳过。

**代码示意**：
```java
if (!analysisResult.requiresMemory()) {
    log.info("[Memory Gating] Skipping memory injection for intent: {}", 
             analysisResult.intentType());
    ctx.setLongTermMemory(new ArrayList<>());
    return;
}
```

---

#### Stage 6: Context Assembly — 上下文组装

**目标**：按优先级结构化组装记忆，而非简单拼接。

**三层注入结构**：

```
System Prompt 组装:
┌─ L1: 用户档案 (PROFILE) ← 始终注入，最高优先级
│   "用户：Kyle，语言偏好：zh-CN，身份：程序员"
│
├─ L2: 当前问题相关记忆 ← 动态注入
│   "KChat 用 Java + Spring Boot 实现"
│   "用户正在了解大模型多模态能力" ← 精排后保留的
│
└─ L3: 用户偏好 (PREFERENCE) ← 可选注入，低优先级
    "用户偏好简洁风格"
```

**格式改进**：
```
当前格式: "- [2026-08-06] 用户正在开发kchat（置信度 95%）"
改进格式: 
  "📌 项目相关: KChat 用 Java + Spring Boot 实现"
  "💡 能力相关: 用户正在了解大模型多模态"
```

---

## 三、改造实施计划

### Phase 1：基础加固（已完成 ✅）

| 编号 | 改动                   | 文件                                            | 状态   |
| ---- | ---------------------- | ----------------------------------------------- | ------ |
| 1.1  | 长期记忆相似度阈值过滤 | `VectorStoreWrapper` + `LongTermMemoryService`  | ✅ 完成 |
| 1.2  | 空/UNKNOWN 消息过滤    | `MessageAssemblyStage`                          | ✅ 完成 |
| 1.3  | 语义去重               | `MemoryExtractorImpl` + `LongTermMemoryService` | ✅ 完成 |

### Phase 2：Query Understanding + Memory Selection（核心改造）

| 编号 | 改动                                                      | 文件                                                | 优先级 |
| ---- | --------------------------------------------------------- | --------------------------------------------------- | ------ |
| 2.1  | 新增 `QueryAnalysisResult` 数据类                         | `dto/QueryAnalysisResult.java`                      | P0     |
| 2.2  | 新增 `QueryAnalyzerStage`（规则 + LLM 混合）              | `pipeline/stage/preprocess/QueryAnalyzerStage.java` | P0     |
| 2.3  | `LongTermMemoryService.recall` 新增类型过滤参数           | `LongTermMemoryService.java`                        | P0     |
| 2.4  | `LongTermMemoryStage` 使用 QueryAnalysisResult            | `LongTermMemoryStage.java`                          | P0     |
| 2.5  | `MemoryExtractorConfig` 新增 `intent-gating-enabled` 配置 | `MemoryExtractorConfig.java`                        | P1     |

**预估收益**：召回关联度 ↑ 30%，无关注入 ↓ 50%

### Phase 3：Multi-Strategy Retrieval + Relevance Ranking

| 编号 | 改动                                        | 文件                           | 优先级 |
| ---- | ------------------------------------------- | ------------------------------ | ------ |
| 3.1  | 新增 `KeywordRetriever`（Redis 倒排索引）   | `memory/KeywordRetriever.java` | P1     |
| 3.2  | `VectorStoreWrapper` 支持关键词倒排索引构建 | `VectorStoreWrapper.java`      | P1     |
| 3.3  | 新增 `MemoryReranker`（规则精排）           | `memory/MemoryReranker.java`   | P1     |
| 3.4  | `LongTermMemoryStage` 接入多路召回 + 精排   | `LongTermMemoryStage.java`     | P1     |
| 3.5  | （可选）引入 BGE-Reranker 重排模型          | `config/RerankerConfig.java`   | P2     |

**预估收益**：召回精度 ↑ 40%

### Phase 4：Memory Gating + Context Assembly

| 编号 | 改动                                           | 文件                           | 优先级 |
| ---- | ---------------------------------------------- | ------------------------------ | ------ |
| 4.1  | `LongTermMemoryStage` 入口门控检查             | `LongTermMemoryStage.java`     | P0     |
| 4.2  | `MemoryFormatStage` 分层格式化输出             | `MemoryFormatStage.java`       | P1     |
| 4.3  | `DefaultSystemPrompt` 支持分层记忆占位符       | `DefaultSystemPrompt.java`     | P1     |
| 4.4  | `ContextPipelineExecutor` Agent 循环按需重检索 | `ContextPipelineExecutor.java` | P2     |

**预估收益**：无效注入 ↓ 80%，上下文利用率 ↑ 20%

### Phase 5：可选优化

| 编号 | 改动                 | 说明                                   |
| ---- | -------------------- | -------------------------------------- |
| 5.1  | Agent 循环上下文刷新 | 工具结果改变语义时重新组装上下文       |
| 5.2  | 记忆压缩定时任务     | 每周合并相似记忆                       |
| 5.3  | embedding 模型微调   | 用 KChat 对话数据微调 all-MiniLM-L6-v2 |
| 5.4  | 记忆冷存储           | 超过 30 天的记忆降级归档               |

---

## 四、新增文件清单

### Phase 2 新增

| 文件路径                                                                                  | 职责                         |
| ----------------------------------------------------------------------------------------- | ---------------------------- |
| `backend/src/main/java/com/example/app/dto/QueryAnalysisResult.java`                      | Query 分析结果 DTO           |
| `backend/src/main/java/com/example/app/pipeline/stage/preprocess/QueryAnalyzerStage.java` | Query 分析 Stage             |
| `backend/src/main/java/com/example/app/service/QueryAnalyzer.java`                        | Query 分析服务（规则 + LLM） |

### Phase 3 新增

| 文件路径                                                             | 职责         |
| -------------------------------------------------------------------- | ------------ |
| `backend/src/main/java/com/example/app/memory/KeywordRetriever.java` | 关键词检索器 |
| `backend/src/main/java/com/example/app/memory/MemoryReranker.java`   | 记忆精排器   |

### Phase 4 新增

| 文件路径                   | 职责 |
| -------------------------- | ---- |
| （无需新增，改造已有文件） |      |

---

## 五、配置项汇总

### 新增配置（application.yml）

```yaml
# 记忆召回流水线配置
memory:
  retrieval:
    # Query 分析
    query-analysis:
      enabled: true                    # 是否启用 Query 分析
      use-llm: true                    # 不确定时是否调用 LLM 做深度分析
      llm-threshold-confidence: 0.6     # 规则判定置信度低于此值时调 LLM
    
    # 多路召回
    multi-strategy:
      dense-enabled: true              # 向量检索
      sparse-enabled: true             # 关键词检索
      temporal-enabled: true           # 时间衰减加权
      max-candidates: 20               # 粗召回候选数
    
    # 精排
    reranking:
      enabled: true                    # 是否启用精排
      method: "rule"                   # rule / cross-encoder / llm
      top-k: 5                         # 精排后保留数
    
    # 门控
    gating:
      enabled: true                    # 是否启用门控
      skip-intents:                    # 跳过的意图类型
        - GREETING
        - CHAT_SMALLTALK
        - MATH_CALCULATION
    
    # 分层组装
    assembly:
      profile-always-inject: true      # PROFILE 类型始终注入
      max-context-memories: 5           # 最多注入的动态记忆数
```

---

## 六、验证方案

### 6.1 离线评估

| 测试用例     | 输入               | 期望召回                   | 期望不召回           |
| ------------ | ------------------ | -------------------------- | -------------------- |
| 独立知识查询 | "Java 是什么"      | 无或少量（用户技术栈）     | 用户偏好、关系       |
| 上下文依赖   | "这个文件总结一下" | PROFILE(昵称) + 最近 EVENT | 其他无关             |
| 用户档案查询 | "我叫什么名字"     | PROFILE(昵称)              | KNOWLEDGE, SKILL     |
| 闲聊         | "你好"             | PROFILE(昵称)              | 所有其他             |
| 技术问题     | "KChat 用什么框架" | SKILL + PROJECT            | PREFERENCE, RELATION |

### 6.2 在线指标

| 指标           | 计算方式                             | 目标  |
| -------------- | ------------------------------------ | ----- |
| **召回相关率** | 人工评估召回记忆与 query 的相关度    | ≥ 80% |
| **无关注入率** | 被判定为无关的注入次数 / 总注入次数  | ≤ 10% |
| **记忆利用率** | 被模型实际引用的记忆数 / 注入数      | ≥ 30% |
| **GPT 引用率** | 模型自己生成答案（未引用记忆）的比例 | ≤ 30% |

### 6.3 A/B 测试

- 灰度 50% 流量开启新 Pipeline
- 对比两组的上述指标
- 观察 1 周后全量

---

## 七、风险与缓解

| 风险                             | 影响                | 缓解措施                             |
| -------------------------------- | ------------------- | ------------------------------------ |
| Query Analyzer 增加 LLM 调用延迟 | 每次对话 +100-500ms | 规则优先，仅不确定时调 LLM           |
| 多路召回增加计算开销             | 每轮多 2-3 次检索   | 候选数限制在 20 以内                 |
| 精排模型增加 GPU 开销            | 部署成本            | 先用规则精排，后续按需引入           |
| 门控可能误杀必要记忆             | 回答缺少背景        | 初期门控宽松，仅跳过明确不需要的意图 |
| 改造期间兼容性                   | 线上风险            | Phase 1-4 逐步实施，每阶段可独立回滚 |

---

## 八、实施路线图

```
2026-08-11  ──── Phase 1 ✅ 已完成
             │
2026-08-12  ──── Phase 2: Query Understanding + Memory Selection
             │   预计 1-2 天
             │
2026-08-14  ──── Phase 3: Multi-Strategy Retrieval + Relevance Ranking
             │   预计 2-3 天
             │
2026-08-17  ──── Phase 4: Memory Gating + Context Assembly
             │   预计 1-2 天
             │
2026-08-19  ──── Phase 5: 可选优化（按需推进）
                 │
2026-08-21  ──── 全量上线 + A/B 验证
```

---

## 附录 A：与当前代码的映射

| 当前文件                    | 改造后角色                                                    |
| --------------------------- | ------------------------------------------------------------- |
| `VectorStoreWrapper`        | Dense Retrieval 核心 + 关键词倒排索引构建                     |
| `LongTermMemoryService`     | 带类型过滤的召回 + 语义查重                                   |
| `LongTermMemoryStage`       | Pipeline 调度：QueryAnalysis → Selection → Retrieval → Gating |
| `MemoryFormatStage`         | 分层格式化输出                                                |
| `MessageAssemblyStage`      | 保留：空消息过滤                                              |
| `MemoryExtractorImpl`       | 保留：语义去重                                                |
| `QueryAnalyzerStage` (新增) | Query 理解                                                    |
| `KeywordRetriever` (新增)   | 关键词检索                                                    |
| `MemoryReranker` (新增)     | 精排                                                          |

## 附录 B：记忆类型与意图映射表

| 意图类型          | PROFILE | PREFERENCE | PROJECT | SKILL | TASK | KNOWLEDGE | RELATION | EVENT |
| ----------------- | ------- | ---------- | ------- | ----- | ---- | --------- | -------- | ----- |
| KNOWLEDGE_QUERY   | ✗       | ✗          | ✓       | ✓     | ✗    | ✓         | ✗        | ✗     |
| PROFILE_QUERY     | ✓       | ✓          | ✗       | ✗     | ✗    | ✗         | ✗        | ✗     |
| TASK_EXECUTION    | ✗       | ✗          | ✓       | ✗     | ✓    | ✗         | ✗        | ✓     |
| CHAT_SMALLTALK    | ✓(昵称) | ✗          | ✗       | ✗     | ✗    | ✗         | ✗        | ✗     |
| CONTEXT_DEPENDENT | ✓       | ✓          | ✓       | ✓     | ✓    | ✓         | ✓        | ✓     |
| GENERAL           | ✓       | ✓          | ✓       | ✓     | ✓    | ✓         | ✓        | ✓     |

## 附录 C：Query 分析规则映射表

| 触发模式 | 正则/关键词                                          | 意图类型          | 需要记忆 |
| -------- | ---------------------------------------------------- | ----------------- | -------- |
| 代词指代 | `(这个\|那个\|刚才\|之前\|上文\|上文的)`             | CONTEXT_DEPENDENT | 是       |
| 身份询问 | `(你叫\|我叫\|我是\|我的名字)`                       | PROFILE_QUERY     | 是       |
| 技术询问 | `(技术栈\|用什么框架\|怎么实现\|如何做\|代码\|开发)` | KNOWLEDGE_QUERY   | 是       |
| 任务请求 | `(总结\|翻译\|处理\|生成\|创建\|删除)`               | TASK_EXECUTION    | 是       |
| 问候     | `^(你好\|hi\|hello\|早上好\|晚安)$`                  | CHAT_SMALLTALK    | 否       |
| 闲聊     | `(最近怎么样\|在吗\|聊聊天)`                         | CHAT_SMALLTALK    | 否       |
| 数学计算 | `^\d+[\+\-\*\/]\d+`                                  | MATH_CALCULATION  | 否       |
| 其他     | 无法规则匹配                                         | 调 LLM 分析       | 动态     |