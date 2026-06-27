# 17. Prompt 架构设计

> 生成日期：2026-06-27 | 分支：main

---

## 一、Prompt 组装全景

```
                              PromptAssembler.assemble()
                                      │
        ┌─────────────────────────────┼─────────────────────────────┐
        │                             │                             │
        ▼                             ▼                             ▼
  ┌──────────┐               ┌──────────────┐              ┌──────────────┐
  │ System   │               │  History     │              │  User        │
  │ Prompt   │               │  (短期记忆)   │              │  Message     │
  └────┬─────┘               └──────┬───────┘              └──────┬───────┘
       │                            │                             │
       │ 组成:                       │ 来源:                       │ 组成:
       │ • 角色设定                   │ • ShortTermMemory           │ • 原始用户输入
       │ • 语言指令                   │   (L1内存 + L2 Redis)       │ • 经安全过滤
       │ • 长期记忆                   │ • 最多 20 条消息            │ • 经敏感脱敏
       │ • 网络搜索上下文              │ • User/AI 交替              │
       │ • 当前时间戳                 │                            │
       └─────────────────────────────┼─────────────────────────────┘
                                     │
                                     ▼
                          ┌──────────────────┐
                          │  Token 感知截断   │
                          │  (assembleWith   │
                          │   Truncation)    │
                          └──────────────────┘
                                     │
                                     ▼
                          ┌──────────────────┐
                          │  指标记录         │
                          │  (PromptMetrics)  │
                          └──────────────────┘
```

## 二、System Prompt 构成

### 2.1 模板加载优先级

```
buildSystemPrompt(languageClause, longTermMemoryText, searchContext)
  │
  ├── [优先] PromptTemplateService.renderTemplate("default-system-prompt", params)
  │          └── 从数据库 prompt_templates 表加载最新启用版本
  │          └── 占位符替换: {language_clause} → 语言指令
  │                          {long_term_memory} → 记忆文本
  │          └── 若数据库无模板 → 抛出 IllegalArgumentException
  │
  └── [降级] FALLBACK_SYSTEM_PROMPT_TEMPLATE (硬编码)
             └── String.replace("{language_clause}", ...)
             └── String.replace("{long_term_memory}", ...)
```

### 2.2 默认 System Prompt 模板

```
角色：你是 KChat 智能助手，一个专业、友好的AI助手。

核心指令：
1. 始终使用 {language_clause}
2. 基于提供的用户背景信息回答问题
3. 回答要简洁明了，避免冗长
4. 对于不确定的问题，诚实告知

用户背景：
{long_term_memory}

开始回答：
```

### 2.3 语言指令注入

```
语言映射表 (LANGUAGE_NAMES):
  zh-CN → "中文（简体）"
  zh-TW → "中文（繁體）"
  en/en-US/en-GB → "English"
  ja → "日本語"
  ko → "한국어"
  fr → "Français"
  de → "Deutsch"
  es → "Español"
  ru → "Русский"

生成规则:
  languageClause = language != null && !isBlank
    ? "请使用 {语言名} 回复。"
    : ""
```

### 2.4 长期记忆注入格式

```
formatLongTermMemory(memories)
  │
  ├── 按重要性降序排序
  │
  └── 格式: 每行一条
      "- [类型] [重要性:N/10] 内容"
      
      示例:
      "- [SKILL] [重要性:8] 用户使用Java开发后端服务"
      "- [PROFILE] [重要性:9] 用户是一名全栈工程师"
      "- [PREFERENCE] [重要性:6] 用户偏好简洁的代码风格"
      
      若无记忆 → "无"
```

### 2.5 网络搜索结果注入

当用户开启联网搜索时，在 System Prompt 末尾追加：

```
当前时间：2026年06月27日 15:30:00 星期六

网络搜索结果：
- [搜索结果标题1](URL1): 摘要内容...
- [搜索结果标题2](URL2): 摘要内容...

请基于以上网络搜索结果回答用户问题。如果搜索结果不足以回答问题，请结合你的知识进行补充。
```

### 2.6 对话总结专用 Prompt

```
你是一个专业的笔记整理助手。请将用户提供的 AI 对话回复内容整理为一份结构清晰的 Markdown 笔记。
{语言指令}

要求：
1. 首先输出笔记标题（不超过50字，纯文本，不加 # 号、不加 ** 加粗或任何 Markdown 格式，单独一行）
2. 然后用 --- 分隔线隔开
3. 接着输出整理后的 Markdown 笔记正文

笔记正文格式规范：
- 使用合适的 Markdown 标题层级（##、###）组织内容结构
- 保留关键信息、代码示例、要点列表
- 对重要概念使用**加粗**标记
- 如有代码，使用 ```语言 代码块格式
- 去除对话式的寒暄和冗余表述，只保留核心知识内容
```

### 2.7 标题生成专用 Prompt

```
根据以下对话内容，生成一个简短的标题（3-15个字）。直接输出标题，不要加引号、编号或其他修饰。

用户：{truncatedUserMessage（≤200字符）}
AI：{truncatedAiResponse（≤200字符）}
```

### 2.8 记忆提取专用 Prompt

```
你是一个专业的记忆提取与总结专家。请从以下对话中：

1. 提取值得长期记忆的重要事实信息
2. 对相关信息进行总结归纳
3. 识别用户的身份、技能、偏好、项目、任务、知识、关系、事件等

提取规则：
- 只提取事实性信息，不要保存对话内容本身
- 忽略问候语、闲聊、一次性问题
- 每条记忆保持简洁（不超过50字）
- 对相关信息进行合并总结
- 为每条记忆标注类型：PROFILE/PREFERENCE/PROJECT/SKILL/TASK/KNOWLEDGE/RELATION/EVENT
- 为每条记忆评估重要性（1-10分，越高越重要）
- 为每条记忆评估置信度（0.0-1.0）

对话：{conversation}

请输出JSON格式：
{
  "summary": "对对话内容的简要总结（不超过100字）",
  "memories": [
    {
      "content": "用户使用Java开发",
      "type": "SKILL",
      "importance": 8,
      "confidence": 0.95
    }
  ]
}
```

---

## 三、上下文管理

### 3.1 短期记忆（对话历史）

```
ShortTermMemory
├── L1: ConcurrentHashMap<conversationId, ChatMemory>
│        MaxMessages = 20 (MessageWindowChatMemory)
│        进程内存，应用重启丢失
│
├── L2: Redis (kchat:memory:{conversationId})
│        24h TTL, JSON 序列化
│        Redis 不可用时自动降级为纯 L1
│
└── 策略: Write-Through (每次 add 自动持久化到 Redis)
          加载: L1 → L2 → 新建空记忆
```

**消息格式（langchain4j）：**
- `SystemMessage` — 系统指令
- `UserMessage` — 用户输入
- `AiMessage` — AI 回复

### 3.2 长期记忆（语义召回）

```
memories.recall(userId, query, topK=5)
│
├── 向量检索: VectorStoreWrapper.search(userId, query, topK)
│     └── 余弦相似度 ≥ 0.3
│     └── 按相似度降序，取 topK
│
├── 数据库过滤:
│     └── 用户隔离: userId 匹配
│     └── 重要性过滤: importance ≥ 3
│
└── 返回: List<MemoryDTO> (按重要性降序)
```

### 3.3 上下文窗口

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 短期记忆窗口 | 20 条 | `MessageWindowChatMemory.withMaxMessages(20)` |
| Token 上限 | 8192 | `prompt.token.max-tokens` |
| 长期记忆召回数 | 5 条 | `memory.long-term.max-recall` |
| 记忆提取上下文窗口 | 20 条 | `memory.extractor.context-window-size` |

### 3.4 Token 估算

```
DefaultTokenEstimator
├── [精确] tiktoken (com.knuddels.jtokkit) — 若 classpath 可用
│     └── encodingType: cl100k_base (GPT-3.5/GPT-4)
│     └── 通过反射调用，避免编译时依赖
│
└── [降级] SimpleTokenEstimator — 若 tiktoken 不可用
      └── tokens = ceil(text.length() / 4)
      └── 适用于中英文混合文本
```

---

## 四、Prompt 组装流程（7 步）

```
assemble(shortTermMemory, longTermMemory, userMessage, language, conversationId, searchContext)

步骤 1 ─ 安全过滤
  └── InputValidator.validateAndSanitize(userMessage)
       ├── 长度检查: 1 ~ 4096 字符
       ├── 注入检测: 11 种危险模式（{{}}、<script>、SQL 注入等）
       └── 危险字符过滤: 移除 {{}}、{% %}、<script>、javascript: 等

步骤 2 ─ 语言指令
  └── buildLanguageClause(language)
       └── "请使用 {语言名} 回复。" 或 ""

步骤 3 ─ 长期记忆格式化
  └── formatLongTermMemory(longTermMemory)
       └── "- [类型] [重要性:N] 内容" × N 条

步骤 4 ─ 系统提示词
  └── buildSystemPrompt(languageClause, longTermMemoryText, searchContext)
       ├── 加载 DB 模板 (PromptTemplateService.renderTemplate)
       ├── 替换占位符 {language_clause} {long_term_memory}
       ├── 追加网络搜索上下文 + 当前时间（若有）
       └── 降级到硬编码模板（若 DB 无模板）

步骤 5 ─ 对话历史
  └── messages.addAll(shortTermMemory)
       最多 20 条历史消息

步骤 6 ─ 用户输入
  └── messages.add(UserMessage.from(sanitizedUserMessage))

步骤 7 ─ 指标记录
  └── PromptMetricsService.recordMetrics()
       ├── tokenCount, memoryCount, buildDurationMs
       ├── conversationId, modelName, userId
       └── 若发生截断: tokensBefore/After, truncationOccurred
```

### 4.1 最终消息结构示例

```
[
  SystemMessage(
    "角色：你是 KChat 智能助手...
     请使用 中文（简体） 回复。
     
     用户背景：
     - [SKILL] [重要性:8] 用户使用java开发后端服务
     - [PROFILE] [重要性:9] 用户是一名全栈工程师
     - [PREFERENCE] [重要性:6] 用户偏好简洁的代码风格
     
     当前时间：2026年06月27日 15:30:00 星期六

     网络搜索结果：
     - [Spring Boot 3.2 Released](https://example.com): Spring Boot 3.2 正式发布...
     
     请基于以上网络搜索结果回答用户问题。"
  ),
  UserMessage("之前的用户问题..."),
  AiMessage("之前的AI回答..."),
  UserMessage("现在的用户问题..."),   ← 最多 20 条历史
  UserMessage("当前用户输入（已过滤）") ← 步骤 6
]
```

---

## 五、Token 感知截断

### 5.1 截断策略

```
truncateToTokenLimit(messages, maxTokens=8192)

分类消息:
  ├── systemMessages[]   → 始终保留
  ├── historyMessages[]  → 可裁剪
  └── lastUserMessage    → 始终保留

Token 预算分配:
  1. mandatoryTokens = systemTokens + lastUserTokens
     ├── 若 mandatoryTokens > maxTokens
     │     → 极端模式: 仅保留第 1 条 SystemMessage + 用户消息（如果放得下）
     │
  2. availableTokens = maxTokens - mandatoryTokens
     └── 从后往前遍历 historyMessages
          ├── 每条消息: 若 tokenEstimate(msg) ≤ 剩余可用 → 加入
          └── 否则: 跳过（更早的消息也不保留）

最终结构:
  [SystemMessages...] + [保留的最近历史...] + [lastUserMessage]
```

### 5.2 优先级规则

```
保留优先级（从高到低）：
  1. SystemMessage（系统指令是不可裁剪的核心）
  2. 当前 UserMessage（用户最新输入不可丢失）
  3. 最近的 AiMessage + UserMessage 对话对
  4. 更早的对话历史
```

---

## 六、Ollama 客户端的 Prompt 构建

Ollama 使用 `/api/generate` 端点时需要**文本拼接**而非结构化 messages。

```java
buildPrompt(messages)
  遍历 messages:
    SystemMessage     → "System: {text}\n\n"
    UserMessage       → "User: {text}\n"
    AiMessage         → "Assistant: {text}\n"
    其他              → "{text}\n"
  
  末尾追加: "Assistant: "  ← 引导模型开始生成
```

**与 OpenAI 格式的区别：**
- OpenAI：结构化 `messages` 数组，role 区分
- Ollama `/api/generate`：单段 prompt 字符串，前缀区分

---

## 七、安全过滤层

### 7.1 过滤顺序

```
原始输入
  │
  ├── InputValidator.validateAndSanitize()
  │     ├── null 检查 → IllegalArgumentException
  │     ├── 最小长度 (1) → IllegalArgumentException
  │     ├── 最大长度 (4096) → IllegalArgumentException
  │     ├── filterDangerousCharacters() — 移除 {{、}}、{%、%}、<script、javascript: 等
  │     └── containsInjectionPattern() — 11 种正则模式检测
  │
  └── SensitiveFilter.sanitize()
        └── 12 种敏感信息正则 → "***"
```

### 7.2 过滤后的日志安全

```java
// 日志中显示脱敏版本
String maskedMessage = sensitiveFilter.sanitize(sanitizedUserMessage);
log.info("输入内容（已脱敏）: {}", maskedMessage);

// Ollama 日志也脱敏
log.info("Prompt:\n{}", sensitiveFilter.sanitizeLog(prompt));
```

---

## 八、Prompt 模板系统

### 8.1 模板实体

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| name | String(100) | 模板名称（唯一） |
| content | TEXT | 模板内容（支持 `{placeholder}`） |
| description | String(500) | 描述 |
| category | String(50) | 分类 |
| defaults | JSON | 默认参数 |
| version | Integer | 版本号（自增） |
| active | Boolean | 是否启用 |

### 8.2 版本管理

```
更新模板 → 创建新版本
  ├── 旧版本: active = false (保留历史)
  └── 新版本: active = true, version = 旧版本 + 1

查询: 始终取 "最新启用版本" (findActiveLatestVersionByName)
```

### 8.3 缓存策略

```java
@Cacheable(value = "promptTemplates", key = "'active_' + #name")
findActiveLatestVersion(name)

@CacheEvict(value = "promptTemplates", allEntries = true)
// 创建/更新/删除时触发
```

---

## 九、降级与容错

### 9.1 Prompt 组装降级

```
assemble() 主流程异常
  │
  └── fallbackAssemble(userMessage, language)
        ├── SystemMessage: "你是一个智能助手。" + languageClause
        └── UserMessage: 原始用户输入
        
        → 丢弃所有记忆和历史，最简 Prompt
```

### 9.2 记忆召回降级

```
MemoryRecallerImpl.recall() 异常
  └── catch → log.warn → return empty list
      → 对话继续，仅无记忆增强
```

### 9.3 Token 估算降级

```
DefaultTokenEstimator
  ├── jtokkit 可用 → 精确估算 (cl100k_base)
  └── jtokkit 不可用 → SimpleTokenEstimator (字符数/4)
```

### 9.4 模板降级

```
PromptTemplateService.renderTemplate() 抛出 IllegalArgumentException
  └── 使用硬编码 FALLBACK_SYSTEM_PROMPT_TEMPLATE
```

---

## 十、Prompt 指标体系

### 10.1 记录维度

| 指标 | 字段 | 说明 |
|------|------|------|
| Token 总数 | `tokenCount` | 发送给 LLM 的 Prompt Token 估算 |
| 记忆数量 | `memoryCount` | 注入的长期记忆条数 |
| 构建耗时 | `buildDurationMs` | 从进入到返回的时间 |
| 截断标志 | `truncationOccurred` | 是否触发 Token 截断 |
| 截断前 Token | `tokensBeforeTruncation` | 原始 Prompt 大小 |
| 截断后 Token | `tokensAfterTruncation` | 截断后 Prompt 大小 |
| 关联信息 | `conversationId`, `userId`, `modelName`, `promptVersion` | 追溯维度 |

### 10.2 查询能力

```java
PromptMetricsService
├── getByConversationId(conversationId)  // 单次对话的 Prompt 历史
├── getByUserId(userId)                  // 用户维度的 Prompt 使用
├── getByTimeRange(start, end)           // 时间范围统计
├── getRecent(limit)                     // 最近 N 条
└── getOverview()                        // 24h 概览 (avgToken/avgDuration/truncationRate)
```
