# Auto Mode & Multi-Model Orchestration — 详细设计方案

> 版本: 1.0  
> 日期: 2026-06-30  
> 状态: 设计评审

---

## 目录

1. [现状分析](#1-现状分析)
2. [目标架构](#2-目标架构)
3. [三层决策模型](#3-三层决策模型)
4. [核心数据结构设计](#4-核心数据结构设计)
5. [Stage 设计详案](#5-stage-设计详案)
6. [多模型编排流程](#6-多模型编排流程)
7. [Streaming 模式下的多模型编排](#7-streaming-模式下的多模型编排)
8. [API 设计](#8-api-设计)
9. [数据库变更](#9-数据库变更)
10. [改造范围清单](#10-改造范围清单)
11. [实施计划](#11-实施计划)
12. [风险与降级策略](#12-风险与降级策略)

---

## 1. 现状分析

### 1.1 当前调用链路

```
用户选择模型 → ChatRequest.model → ConversationContext.model
                                         │
                                         ▼
                                  ModelRoutingStage (order=500)
                                         │
                              ┌──────────┴──────────┐
                              ▼                     ▼
                    configName:xxx?            Ollama 本地模型
                    → OpenAICompatibleClient   → OllamaClient
```

### 1.2 当前架构的核心局限

| 问题 | 影响 |
|------|------|
| `ConversationContext` 仅支持单模型 (`model` 字段 ×1, `llmResponse` 字段 ×1) | 一次请求无法调用多个不同模型 |
| `ModelRoutingStage` 是纯静态分支 (`if customConfig ≠ null`) | 无意图分析、无智能路由 |
| `ChatRequest` 无 `mode` 字段 | 前端只能手动选模型，无法切换 auto/manual |
| `ModelConfig` 无 `capabilities` / `priority` 字段 | Layer 2 能力匹配无依据 |
| Pipeline 为线性单次执行设计 | 无法支持 DAG 编排 |
| `executeWithAgentLoop` 设计为同一模型的 tool-calling 循环 | 不支持跨模型的子任务编排 |

### 1.3 已有的有利基础

- Pipeline 架构分层清晰 (`Phase` + `Stage` + `isApplicable`)
- `AGENT` 阶段 (600-699) 已预留
- `ConversationContext` 已有 `toolCalls` / `toolResults` / `agentState` / `enabledToolNames`
- `ModelConfig` 已有 `type` (OPENAI/ANTHROPIC/...) 和 `category` (TEXT/IMAGE/VIDEO)
- `executeWithAgentLoop` 已 scaffold 了循环执行能力
- 前端 `ChatContext` 用 `useReducer` 管理状态，扩展性好

---

## 2. 目标架构

### 2.1 总体分层

```
┌──────────────────────────────────────────────────────────────────┐
│                        FRONTEND                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  Header: 模式切换 [Auto ⬜] [Manual ⬜]                      │  │
│  │  ChatArea: 编排进度展示 (子任务卡片 + 状态)                  │  │
│  │  InputArea: 发送时携带 mode 参数                            │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────┘
                               │ ChatRequest { mode: "auto"|"manual" }
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                        BACKEND                                    │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                 EXECUTION 阶段 (重构)                        │ │
│  │                                                              │ │
│  │  IntentClassificationStage  (485)  ← NEW                     │ │
│  │       │ 分析用户意图 → 输出意图标签 + 所需能力                  │ │
│  │       ▼                                                      │ │
│  │  OrchestrationPlanningStage (488)  ← NEW                     │ │
│  │       │ 意图 → 任务分解 → 生成 SubTask DAG                    │ │
│  │       ▼                                                      │ │
│  │  OrchestrationExecutionStage (490) ← NEW                     │ │
│  │       │ 按 DAG 拓扑顺序 + 并行度执行子任务                     │ │
│  │       │ 每个子任务调用 → ModelRoutingStage (500)              │ │
│  │       ▼                                                      │ │
│  │  ResultAggregationStage (510)  ← NEW                         │ │
│  │       │ 合并所有子任务结果 → ctx.llmResponse                   │ │
│  │       ▼                                                      │ │
│  │  ModelRoutingStage (500) ← 保持，作为原子执行单元              │ │
│  │       │ 单次模型调用（被 Orchestration 循环调用）              │ │
│  │       ▼                                                      │ │
│  │  FallbackStage (520)  ← NEW                                  │ │
│  │       子任务失败时的降级/重试                                   │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                 AGENT 阶段 (600-699)                         │ │
│  │  ToolDefinitionStage                                        │ │
│  │  ToolCallParsingStage                                       │ │
│  │  ToolInvocationStage                                        │ │
│  │  AgentGoalEvaluationStage                                   │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 两种模式的执行路径

```
Manual 模式（不改动）:
  ChatRequest.mode="manual"
  → IntentClassificationStage.isApplicable() → false (跳过所有新 Stage)
  → ModelRoutingStage (直接使用 ctx.model)
  → 与现在完全一致

Auto 模式:
  ChatRequest.mode="auto"
  → IntentClassificationStage → OrchestrationPlanningStage
  → OrchestrationExecutionStage (循环调用 ModelRoutingStage)
  → ResultAggregationStage
```

---

## 3. 三层决策模型

```
Layer 1: Intent Classification (意图分类)
┌─────────────────────────────────────────────────────────────┐
│ 输入: ctx.userMessage                                       │
│ 输出: DetectedIntent { label, confidence, requiredCapabilities }
│                                                              │
│ 意图标签枚举:                                                 │
│   CHAT              - 普通对话                                │
│   CODE_GENERATION   - 代码生成/调试                           │
│   ANALYSIS          - 数据分析/推理                           │
│   IMAGE_GENERATION  - 图片生成                                │
│   IMAGE_ANALYSIS    - 图片理解                                │
│   WEB_RESEARCH      - 需要搜索的研究                          │
│   COMPARISON        - 多对象对比分析                          │
│   SUMMARIZATION     - 内容总结                                │
│   CREATIVE_WRITING  - 创意写作                                │
│   TRANSLATION       - 翻译                                    │
│   MULTI_STEP        - 复合任务（触发多模型编排）               │
│                                                              │
│ 实现方案: 规则匹配 + embedding 兜底                            │
│   - 规则: keyword/regex 快速匹配（零延迟）                      │
│   - Embedding: 对规则未命中的，用本地模型做语义分类             │
│   - 分类模型: ollama 的小模型 (qwen3:3b / phi)                │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
Layer 2: Capability Matching (能力匹配)
┌─────────────────────────────────────────────────────────────┐
│ 输入: DetectedIntent.requiredCapabilities                    │
│ 输出: List<ModelConfig> candidates                           │
│                                                              │
│ 能力维度 (扩展 ModelConfig.capabilities):                     │
│   classification     - 分类/意图识别                          │
│   chat               - 通用对话                               │
│   reasoning          - 复杂推理                               │
│   code               - 代码生成                               │
│   image_generation   - 图片生成                               │
│   vision             - 图片理解                               │
│   tool_calling       - 工具调用                               │
│   long_context       - 长上下文 (>32K)                        │
│   embedding          - 向量嵌入                               │
│   fast               - 低延迟 (<500ms)                        │
│   cost_effective     - 低成本                                 │
│                                                              │
│ 匹配逻辑:                                                    │
│   1. 从 ModelConfig 筛选 enabled=true 的配置                  │
│   2. 按 capabilities 交集得分排序                             │
│   3. 考虑 category (TEXT/IMAGE/VIDEO) 过滤                   │
│   4. 输出候选列表（按匹配得分降序）                             │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
Layer 3: Model Selection Strategy (模型选择策略)
┌─────────────────────────────────────────────────────────────┐
│ 输入: List<ModelConfig> candidates, SelectionStrategy        │
│ 输出: ModelConfig selected                                   │
│                                                              │
│ 策略枚举:                                                     │
│   COST_FIRST       - 成本优先（选最便宜的可行模型）            │
│   QUALITY_FIRST    - 质量优先（选能力最强的）                  │
│   SPEED_FIRST      - 速度优先（选延迟最低的）                  │
│   BALANCED         - 综合平衡（默认）                         │
│                                                              │
│ 评分公式:                                                     │
│   score = w1*costScore + w2*capabilityScore + w3*speedScore   │
│   权重由 SelectionStrategy 决定                                │
│                                                              │
│ 策略来源:                                                     │
│   - 默认: BALANCED                                           │
│   - 用户在 UserSetting 中可设置偏好                           │
│   - 特定意图可覆盖策略（如代码生成用 QUALITY_FIRST）            │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. 核心数据结构设计

### 4.1 ConversationContext 变更

```java
// 文件: backend/src/main/java/com/example/app/pipeline/context/ConversationContext.java

public class ConversationContext {

    // ── 以下字段保持不变 ──
    private String conversationId;
    private String userId;
    private String userMessage;
    private String model;               // auto 模式下被 Orchestration 覆写
    private List<String> imageUrls;
    private boolean webSearchEnabled;
    // ... 其他不变

    // ── 新增: Auto Mode 相关 ──
    private String mode;                           // "auto" | "manual"，默认 "manual"
    private String selectionStrategy;              // "balanced" | "cost_first" | "quality_first" | "speed_first"
    private DetectedIntent detectedIntent;          // Layer 1 输出
    private List<OrchestrationTask> taskPlan;       // Layer 2 输出：编排计划
    private Map<String, TaskResult> taskResults;    // taskId → 子任务执行结果
    private String orchestrationStatus;             // "PLANNING" | "EXECUTING" | "AGGREGATING" | "COMPLETED" | "FAILED"

    // ── 内部类 ──

    @Data
    @Builder
    public static class DetectedIntent {
        private String label;                       // "CODE_GENERATION" | "CHAT" | ...
        private double confidence;                  // 0.0 - 1.0
        private List<String> requiredCapabilities;  // ["code", "reasoning"]
        private boolean isComposite;                // 是否需要多模型编排
        private String reasoning;                   // 分类理由（调试用）
    }

    @Data
    @Builder
    public static class OrchestrationTask {
        private String taskId;                      // UUID
        private String description;                 // "使用 GPT-4 分析代码"
        private String requiredCapability;          // "code"
        private String selectedModel;               // "openai:gpt-4" — 编排时填入
        private String selectedModelConfigName;     // "openai"
        private List<String> dependsOn;             // 依赖的 taskId 列表
        private int priority;                       // 同级优先级，越小越先
        private TaskStatus status;                  // PENDING → RUNNING → COMPLETED → FAILED
        private String systemPromptOverride;        // 可选的子任务专属 system prompt
        private String inputTemplate;               // "{{task.X.output}}" — 从其他任务取输入
        private Map<String, Object> metadata;       // 扩展字段
    }

    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    }

    @Data
    @Builder
    public static class TaskResult {
        private String taskId;
        private String modelUsed;                   // "openai:gpt-4"
        private String output;                      // 模型原始输出
        private String errorMessage;
        private long latencyMs;
        private int tokensUsed;
        private TaskStatus status;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    // ── 工厂方法更新 ──

    public static ConversationContext fromRequest(ChatRequest request) {
        return ConversationContext.builder()
                // ... 现有字段 ...
                .mode(request.getMode() != null ? request.getMode() : "manual")
                .selectionStrategy(request.getSelectionStrategy() != null
                        ? request.getSelectionStrategy() : "balanced")
                .taskResults(new HashMap<>())
                .build();
    }
}
```

### 4.2 ChatRequest 变更

```java
// 文件: backend/src/main/java/com/example/app/dto/ChatRequest.java

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String conversationId;
    private String message;
    private String model;           // manual 模式下的指定模型
    private List<String> imageUrls;
    private String userId;
    private boolean webSearch = false;

    // ── 新增 ──
    private String mode;                       // "auto" | "manual"，默认 "manual"
    private String selectionStrategy;          // "balanced" | "cost_first" | "quality_first" | "speed_first"
    private List<String> allowedModels;        // auto 模式下可选的模型白名单（空=全部可用）
    private int maxParallelTasks = 3;          // 最大并行子任务数
}
```

### 4.3 ModelConfig 实体变更

```java
// 文件: backend/src/main/java/com/example/app/entity/ModelConfig.java

@Entity
@Table(name = "model_configs")
public class ModelConfig {

    // ── 现有字段保持不变 ──
    private Long id;
    private String name;
    private String modelId;
    private String baseUrl;
    private String apiKey;
    private ModelType type;
    private ModelCategory category;
    private Boolean enabled;

    // ── 新增字段 ──
    @Column(name = "capabilities", columnDefinition = "VARCHAR(500) DEFAULT ''")
    private String capabilities;  // JSON 数组: ["chat","code","reasoning","vision",...]

    @Column(name = "priority", columnDefinition = "INT DEFAULT 0")
    private Integer priority;     // 同能力下的优先级，越大越优先

    @Column(name = "cost_per_1k_tokens", columnDefinition = "DOUBLE DEFAULT 0.0")
    private Double costPer1kTokens;  // 成本参考（输入token）

    @Column(name = "avg_latency_ms", columnDefinition = "BIGINT DEFAULT 0")
    private Long avgLatencyMs;       // 平均延迟（运行时更新）

    @Column(name = "max_context_tokens", columnDefinition = "INT DEFAULT 8192")
    private Integer maxContextTokens; // 最大上下文长度

    @Column(name = "supports_streaming", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean supportsStreaming; // 是否支持流式

    @Column(name = "supports_vision", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean supportsVision;    // 是否支持视觉

    // capability 辅助方法
    public List<String> getCapabilityList() {
        if (capabilities == null || capabilities.isBlank()) {
            return List.of("chat"); // 默认能力
        }
        try {
            return new ObjectMapper().readValue(capabilities,
                    new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of("chat");
        }
    }

    public boolean hasCapability(String capability) {
        return getCapabilityList().contains(capability);
    }
}
```

### 4.4 UserSetting 变更

```java
// 新增用户级配置字段
@Column(name = "auto_mode_enabled", columnDefinition = "BOOLEAN DEFAULT FALSE")
private Boolean autoModeEnabled;       // 是否默认使用 auto 模式

@Column(name = "selection_strategy", columnDefinition = "VARCHAR(20) DEFAULT 'balanced'")
private String selectionStrategy;      // balanced | cost_first | quality_first | speed_first

@Column(name = "max_parallel_tasks", columnDefinition = "INT DEFAULT 3")
private Integer maxParallelTasks;      // 最大并行子任务数
```

---

## 5. Stage 设计详案

### 5.1 IntentClassificationStage (order=485)

```java
@Component
@Slf4j
public class IntentClassificationStage implements ContextPipelineStage {

    private final ModelConfigService modelConfigService;
    private final OllamaClient ollamaClient;
    private final OpenAICompatibleClient openAICompatibleClient;

    // 规则库：keyword → intent
    private static final Map<String, String> KEYWORD_INTENT_MAP = Map.ofEntries(
        // 代码相关
        Map.entry("代码", "CODE_GENERATION"),
        Map.entry("写一个", "CODE_GENERATION"),
        Map.entry("bug", "CODE_GENERATION"),
        Map.entry("debug", "CODE_GENERATION"),
        Map.entry("函数", "CODE_GENERATION"),
        // 图片生成
        Map.entry("画", "IMAGE_GENERATION"),
        Map.entry("生成图片", "IMAGE_GENERATION"),
        Map.entry("画一张", "IMAGE_GENERATION"),
        Map.entry("画个", "IMAGE_GENERATION"),
        // 搜索/研究
        Map.entry("搜索", "WEB_RESEARCH"),
        Map.entry("查一下", "WEB_RESEARCH"),
        Map.entry("最新", "WEB_RESEARCH"),
        // 对比
        Map.entry("对比", "COMPARISON"),
        Map.entry("比较", "COMPARISON"),
        Map.entry("区别", "COMPARISON"),
        Map.entry("哪个更好", "COMPARISON"),
        // 翻译
        Map.entry("翻译", "TRANSLATION"),
        Map.entry("translate", "TRANSLATION"),
        // 总结
        Map.entry("总结", "SUMMARIZATION"),
        Map.entry("概括", "SUMMARIZATION"),
        // 分析
        Map.entry("分析", "ANALYSIS"),
        Map.entry("为什么", "ANALYSIS"),
        // 创意写作
        Map.entry("写一篇", "CREATIVE_WRITING"),
        Map.entry("写首诗", "CREATIVE_WRITING"),
        Map.entry("故事", "CREATIVE_WRITING")
    );

    // 意图 → 所需能力
    private static final Map<String, List<String>> INTENT_CAPABILITY_MAP = Map.ofEntries(
        Map.entry("CHAT",              List.of("chat")),
        Map.entry("CODE_GENERATION",   List.of("code", "reasoning")),
        Map.entry("ANALYSIS",          List.of("reasoning", "long_context")),
        Map.entry("IMAGE_GENERATION",  List.of("image_generation")),
        Map.entry("IMAGE_ANALYSIS",    List.of("vision", "reasoning")),
        Map.entry("WEB_RESEARCH",      List.of("chat", "tool_calling")),
        Map.entry("COMPARISON",        List.of("reasoning", "long_context")),
        Map.entry("SUMMARIZATION",     List.of("chat", "long_context")),
        Map.entry("CREATIVE_WRITING",  List.of("chat")),
        Map.entry("TRANSLATION",       List.of("chat")),
        Map.entry("MULTI_STEP",        List.of("reasoning", "tool_calling", "image_generation"))
    );

    @Override
    public Phase getPhase() { return Phase.EXECUTION; }
    @Override
    public int getOrder() { return 485; }
    @Override
    public String getName() { return "intentClassificationStage"; }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return "auto".equals(ctx.getMode());
    }

    @Override
    public void execute(ConversationContext ctx) {
        String userMessage = ctx.getUserMessage();
        if (userMessage == null || userMessage.isBlank()) {
            ctx.setDetectedIntent(defaultIntent());
            return;
        }

        // Step 1: 规则匹配
        DetectedIntent ruleIntent = matchByRules(userMessage);
        if (ruleIntent != null && ruleIntent.getConfidence() >= 0.9) {
            ctx.setDetectedIntent(ruleIntent);
            log.info("[Intent] Rule match: {} (confidence={})",
                    ruleIntent.getLabel(), ruleIntent.getConfidence());
            return;
        }

        // Step 2: Embedding / LLM 分类（规则未命中时）
        DetectedIntent llmIntent = classifyByLlm(ctx, userMessage);
        ctx.setDetectedIntent(llmIntent);
        log.info("[Intent] LLM match: {} (confidence={})",
                llmIntent.getLabel(), llmIntent.getConfidence());
    }

    private DetectedIntent matchByRules(String message) {
        String lower = message.toLowerCase();
        for (Map.Entry<String, String> entry : KEYWORD_INTENT_MAP.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                String intentLabel = entry.getValue();
                return DetectedIntent.builder()
                        .label(intentLabel)
                        .confidence(0.95)
                        .requiredCapabilities(INTENT_CAPABILITY_MAP.getOrDefault(
                                intentLabel, List.of("chat")))
                        .isComposite("COMPARISON".equals(intentLabel)
                                && lower.contains("图表")) // 对比+图表=复合任务
                        .reasoning("Keyword match: " + entry.getKey())
                        .build();
            }
        }

        // 含图片URL → 可能是图片理解
        if (ctx.getImageUrls() != null && !ctx.getImageUrls().isEmpty()) {
            return DetectedIntent.builder()
                    .label("IMAGE_ANALYSIS")
                    .confidence(0.85)
                    .requiredCapabilities(List.of("vision", "reasoning"))
                    .isComposite(false)
                    .reasoning("Image URLs present")
                    .build();
        }

        return null; // 规则未命中
    }

    private DetectedIntent classifyByLlm(ConversationContext ctx, String message) {
        // 使用本地小模型做分类（低成本）
        String classificationPrompt = buildClassificationPrompt(message);
        try {
            String result = ollamaClient.generate(
                List.of(UserMessage.from(classificationPrompt)),
                "qwen3:3b" // 或可配置的分类模型
            );
            return parseClassificationResult(result);
        } catch (Exception e) {
            log.warn("[Intent] LLM classification failed, falling back to CHAT", e);
            return defaultIntent();
        }
    }

    // ... 辅助方法省略
}
```

### 5.2 OrchestrationPlanningStage (order=488)

```java
@Component
@Slf4j
public class OrchestrationPlanningStage implements ContextPipelineStage {

    private final ModelConfigService modelConfigService;

    @Override
    public Phase getPhase() { return Phase.EXECUTION; }
    @Override
    public int getOrder() { return 488; }
    @Override
    public String getName() { return "orchestrationPlanningStage"; }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        if (!"auto".equals(ctx.getMode())) return false;

        DetectedIntent intent = ctx.getDetectedIntent();
        if (intent == null) return false;

        // 只有复合任务需要编排，简单任务走直通
        return intent.isComposite() || isMultiModelIntent(intent);
    }

    @Override
    public void execute(ConversationContext ctx) {
        DetectedIntent intent = ctx.getDetectedIntent();
        List<OrchestrationTask> plan;

        if (intent.isComposite()) {
            // 复合任务 → LLM 分解
            plan = planCompositeTask(ctx);
        } else {
            // 简单任务 → 单任务计划
            plan = planSimpleTask(ctx);
        }

        // 为每个子任务匹配最佳模型 (Layer 2 + Layer 3)
        for (OrchestrationTask task : plan) {
            String bestModel = selectBestModel(ctx, task.getRequiredCapability());
            task.setSelectedModel(bestModel);
            if (bestModel != null && bestModel.contains(":")) {
                task.setSelectedModelConfigName(bestModel.split(":")[0]);
            }
        }

        ctx.setTaskPlan(plan);
        ctx.setOrchestrationStatus("PLANNED");

        log.info("[Orchestration] Plan generated: {} tasks", plan.size());
        plan.forEach(t -> log.info("  - {}: {} → {} (dependsOn: {})",
                t.getTaskId(), t.getDescription(), t.getSelectedModel(), t.getDependsOn()));
    }

    private List<OrchestrationTask> planSimpleTask(ConversationContext ctx) {
        // 简单任务：一个子任务 = 整个请求
        return List.of(OrchestrationTask.builder()
                .taskId(UUID.randomUUID().toString().substring(0, 8))
                .description("处理用户请求: " + truncate(ctx.getUserMessage(), 50))
                .requiredCapability(ctx.getDetectedIntent().getRequiredCapabilities().get(0))
                .dependsOn(List.of())
                .priority(0)
                .status(TaskStatus.PENDING)
                .build());
    }

    private List<OrchestrationTask> planCompositeTask(ConversationContext ctx) {
        // 复合任务 → 调用 LLM 生成编排计划
        String planPrompt = buildPlanningPrompt(ctx);
        String planJson = callPlanningModel(planPrompt);
        return parseTaskPlan(planJson);
    }

    private String selectBestModel(ConversationContext ctx, String requiredCapability) {
        List<ModelConfig> enabled = modelConfigService.getAllEnabledConfigs();

        // Step 1: 能力过滤 (Layer 2)
        List<ModelConfig> capable = enabled.stream()
                .filter(c -> c.getCategory() == ModelConfig.ModelCategory.TEXT) // 文本类
                .filter(c -> c.hasCapability(requiredCapability))
                .sorted((a, b) -> {
                    // 能力匹配数多的优先
                    int scoreA = intersectionSize(a.getCapabilityList(),
                            ctx.getDetectedIntent().getRequiredCapabilities());
                    int scoreB = intersectionSize(b.getCapabilityList(),
                            ctx.getDetectedIntent().getRequiredCapabilities());
                    return Integer.compare(scoreB, scoreA);
                })
                .toList();

        // Step 2: 策略选择 (Layer 3)
        String strategy = ctx.getSelectionStrategy();
        return applyStrategy(capable, strategy);
    }

    private String applyStrategy(List<ModelConfig> candidates, String strategy) {
        if (candidates.isEmpty()) return null;

        return switch (strategy) {
            case "cost_first" -> candidates.stream()
                    .min(Comparator.comparingDouble(ModelConfig::getCostPer1kTokens))
                    .map(c -> c.getName() + ":" + c.getModelId())
                    .orElse(null);
            case "speed_first" -> candidates.stream()
                    .min(Comparator.comparingLong(ModelConfig::getAvgLatencyMs))
                    .map(c -> c.getName() + ":" + c.getModelId())
                    .orElse(null);
            case "quality_first" -> candidates.get(0).getName() // 按能力排序的第一名
                    + ":" + candidates.get(0).getModelId();
            default -> candidates.get(0).getName() // balanced: 默认第一候选
                    + ":" + candidates.get(0).getModelId();
        };
    }
}
```

### 5.3 OrchestrationExecutionStage (order=490)

```java
@Component
@Slf4j
public class OrchestrationExecutionStage implements ContextPipelineStage {

    private final ModelRoutingStage modelRoutingStage;
    private final ModelConfigService modelConfigService;
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(4);

    @Override
    public Phase getPhase() { return Phase.EXECUTION; }
    @Override
    public int getOrder() { return 490; }
    @Override
    public String getName() { return "orchestrationExecutionStage"; }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return "auto".equals(ctx.getMode())
                && ctx.getTaskPlan() != null
                && !ctx.getTaskPlan().isEmpty();
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<OrchestrationTask> plan = ctx.getTaskPlan();
        ctx.setOrchestrationStatus("EXECUTING");

        // 单任务 → 直接执行
        if (plan.size() == 1 && plan.get(0).getDependsOn().isEmpty()) {
            executeSingleTask(ctx, plan.get(0));
            return;
        }

        // 多任务 → 按 DAG 拓扑顺序，支持并行
        executeTaskGraph(ctx, plan);
    }

    private void executeSingleTask(ConversationContext ctx, OrchestrationTask task) {
        task.setStatus(TaskStatus.RUNNING);
        ctx.emitSseEvent("orchestration", buildProgressJson(task));

        // 覆写 ctx.model 为选中的模型
        ctx.setModel(task.getSelectedModel());

        long start = System.currentTimeMillis();
        try {
            // 复用现有 ModelRoutingStage
            modelRoutingStage.execute(ctx);

            task.setStatus(TaskStatus.COMPLETED);
            TaskResult result = TaskResult.builder()
                    .taskId(task.getTaskId())
                    .modelUsed(task.getSelectedModel())
                    .output(ctx.getLlmResponse())
                    .latencyMs(System.currentTimeMillis() - start)
                    .status(TaskStatus.COMPLETED)
                    .build();
            ctx.getTaskResults().put(task.getTaskId(), result);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            TaskResult result = TaskResult.builder()
                    .taskId(task.getTaskId())
                    .errorMessage(e.getMessage())
                    .status(TaskStatus.FAILED)
                    .build();
            ctx.getTaskResults().put(task.getTaskId(), result);
        }
    }

    private void executeTaskGraph(ConversationContext ctx, List<OrchestrationTask> plan) {
        Set<String> completed = new HashSet<>();
        Set<String> failed = new HashSet<>();
        Set<String> running = new HashSet<>();

        while (completed.size() + failed.size() < plan.size()) {
            // 找出所有依赖已满足且未执行的任务
            List<OrchestrationTask> ready = plan.stream()
                    .filter(t -> t.getStatus() == TaskStatus.PENDING)
                    .filter(t -> completed.containsAll(t.getDependsOn()))
                    .filter(t -> t.getDependsOn().stream().noneMatch(failed::contains))
                    .toList();

            if (ready.isEmpty() && running.isEmpty()) {
                // 死锁或无更多可执行任务
                break;
            }

            // 并行执行（受 maxParallelTasks 限制）
            int maxParallel = Math.min(ready.size(), 3); // 可配置
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < maxParallel; i++) {
                OrchestrationTask task = ready.get(i);
                task.setStatus(TaskStatus.RUNNING);
                running.add(task.getTaskId());

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    executeSingleTaskForGraph(ctx, task);
                }, taskExecutor);

                futures.add(future);
            }

            // 等待本批次完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 更新状态
            for (OrchestrationTask task : ready.subList(0, maxParallel)) {
                running.remove(task.getTaskId());
                if (task.getStatus() == TaskStatus.COMPLETED) {
                    completed.add(task.getTaskId());
                } else {
                    failed.add(task.getTaskId());
                }
            }
        }

        // 标记因依赖失败而无法执行的任务
        plan.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .forEach(t -> t.setStatus(TaskStatus.SKIPPED));
    }

    private void executeSingleTaskForGraph(ConversationContext ctx, OrchestrationTask task) {
        // 构建子任务的独立 context
        String inputMessage = resolveInputTemplate(ctx, task);

        ConversationContext subCtx = ctx.toBuilder()
                .model(task.getSelectedModel())
                .userMessage(inputMessage)
                .build();

        long start = System.currentTimeMillis();
        try {
            modelRoutingStage.execute(subCtx);
            task.setStatus(TaskStatus.COMPLETED);
            ctx.getTaskResults().put(task.getTaskId(), TaskResult.builder()
                    .taskId(task.getTaskId())
                    .modelUsed(task.getSelectedModel())
                    .output(subCtx.getLlmResponse())
                    .latencyMs(System.currentTimeMillis() - start)
                    .status(TaskStatus.COMPLETED)
                    .build());
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            ctx.getTaskResults().put(task.getTaskId(), TaskResult.builder()
                    .taskId(task.getTaskId())
                    .errorMessage(e.getMessage())
                    .status(TaskStatus.FAILED)
                    .build());
        }
    }

    /**
     * 解析子任务的输入模板，从已完成任务的结果中引用
     * 例如: "{{task.abc123.output}}" → 取 taskId=abc123 的输出
     */
    private String resolveInputTemplate(ConversationContext ctx, OrchestrationTask task) {
        String template = task.getInputTemplate();
        if (template == null || template.isBlank()) {
            return ctx.getUserMessage();
        }
        String resolved = template;
        Pattern pattern = Pattern.compile("\\{\\{task\\.([a-zA-Z0-9]+)\\.output\\}\\}");
        Matcher matcher = pattern.matcher(resolved);
        while (matcher.find()) {
            String refTaskId = matcher.group(1);
            TaskResult refResult = ctx.getTaskResults().get(refTaskId);
            if (refResult != null && refResult.getOutput() != null) {
                resolved = resolved.replace(matcher.group(0), refResult.getOutput());
            }
        }
        return resolved;
    }
}
```

### 5.4 ResultAggregationStage (order=510)

```java
@Component
@Slf4j
public class ResultAggregationStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() { return Phase.EXECUTION; }
    @Override
    public int getOrder() { return 510; }
    @Override
    public String getName() { return "resultAggregationStage"; }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return "auto".equals(ctx.getMode())
                && ctx.getTaskResults() != null
                && !ctx.getTaskResults().isEmpty();
    }

    @Override
    public void execute(ConversationContext ctx) {
        Map<String, TaskResult> results = ctx.getTaskResults();
        List<OrchestrationTask> plan = ctx.getTaskPlan();

        if (plan == null || plan.size() == 1) {
            // 单任务：直接使用任务输出
            TaskResult sole = results.values().iterator().next();
            ctx.setLlmResponse(sole.getOutput());
            ctx.setOrchestrationStatus("COMPLETED");
            return;
        }

        // 多任务：按 plan 顺序拼接 + 格式化
        StringBuilder aggregated = new StringBuilder();

        for (OrchestrationTask task : plan) {
            TaskResult result = results.get(task.getTaskId());
            if (result == null || result.getStatus() != TaskStatus.COMPLETED) {
                continue;
            }
            aggregated.append("## ").append(task.getDescription()).append("\n\n");
            aggregated.append(result.getOutput()).append("\n\n");
            if (result.getModelUsed() != null) {
                aggregated.append("*（由 ")
                        .append(result.getModelUsed())
                        .append(" 生成，耗时 ")
                        .append(result.getLatencyMs())
                        .append("ms）*\n\n");
            }
            aggregated.append("---\n\n");
        }

        ctx.setLlmResponse(aggregated.toString().trim());
        ctx.setOrchestrationStatus("COMPLETED");
    }
}
```

### 5.5 FallbackStage (order=520)

```java
@Component
@Slf4j
public class FallbackStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() { return Phase.EXECUTION; }
    @Override
    public int getOrder() { return 520; }
    @Override
    public String getName() { return "fallbackStage"; }
    @Override
    public boolean isCritical() { return false; }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return "auto".equals(ctx.getMode());
    }

    @Override
    public void execute(ConversationContext ctx) {
        // 检查是否有失败的任务
        boolean hasFailure = ctx.getTaskResults().values().stream()
                .anyMatch(r -> r.getStatus() == TaskStatus.FAILED);

        if (!hasFailure) return;

        long failedCount = ctx.getTaskResults().values().stream()
                .filter(r -> r.getStatus() == TaskStatus.FAILED)
                .count();

        // 所有任务都失败了 → 降级到默认模型
        long totalCount = ctx.getTaskPlan().size();
        if (failedCount == totalCount) {
            log.warn("[Fallback] All {} tasks failed, falling back to default model", totalCount);
            fallbackToDefaultModel(ctx);
        }
    }

    private void fallbackToDefaultModel(ConversationContext ctx) {
        // 使用配置的默认模型直接回答
        ctx.setModel("llama3"); // 或从配置读取
        ctx.setOrchestrationStatus("FALLBACK");

        String fallbackResponse = "抱歉，自动编排执行失败。以下是默认模型的回复：\n\n"
                + ctx.getLlmResponse();
        ctx.setLlmResponse(fallbackResponse);
    }
}
```

---

## 6. 多模型编排流程

### 6.1 完整时序图

```
用户发送: "对比 GPT-4 和 Claude 的编码能力，画个图表"
mode = "auto"

┌─ IntentClassificationStage(485) ────────────────────────────┐
│                                                               │
│  rules: "对比"→COMPARISON, "图表"→MULTI_STEP flag             │
│  output: {                                                    │
│    label: "COMPARISON",                                       │
│    isComposite: true,                                         │
│    requiredCapabilities: ["reasoning", "image_generation"]     │
│  }                                                            │
└────────────────────────────┬──────────────────────────────────┘
                             ▼
┌─ OrchestrationPlanningStage(488) ──────────────────────────┐
│                                                               │
│  LLM 分解任务:                                                 │
│                                                               │
│  Task A: "搜索GPT-4编码能力评测"                                │
│    capability: "chat", model: "ollama:llama3"                  │
│    dependsOn: []                                               │
│                                                               │
│  Task B: "搜索Claude编码能力评测"                               │
│    capability: "chat", model: "ollama:llama3"                  │
│    dependsOn: []                                               │
│                                                               │
│  Task C: "综合分析两者编码能力"                                  │
│    capability: "reasoning", model: "openai:gpt-4"              │
│    dependsOn: [A, B]                                           │
│                                                               │
│  Task D: "生成对比图表"                                         │
│    capability: "image_generation", model: "openai:dall-e-3"    │
│    dependsOn: [C]                                              │
│                                                               │
└────────────────────────────┬──────────────────────────────────┘
                             ▼
┌─ OrchestrationExecutionStage(490) ──────────────────────────┐
│                                                               │
│  Round 1: A ∥ B  (并行，无依赖)                               │
│    ctx.model = "ollama:llama3"                                │
│    → ModelRoutingStage(500).execute() ×2                      │
│                                                               │
│  Round 2: C  (等 A,B 完成)                                    │
│    ctx.model = "openai:gpt-4"                                 │
│    input = ctx.userMessage + A.output + B.output              │
│    → ModelRoutingStage(500).execute()                         │
│                                                               │
│  Round 3: D  (等 C 完成)                                      │
│    ctx.model = "openai:dall-e-3"                              │
│    input = C.output 中的数据描述                               │
│    → ModelRoutingStage(500).execute()                         │
│                                                               │
└────────────────────────────┬──────────────────────────────────┘
                             ▼
┌─ ResultAggregationStage(510) ──────────────────────────────┐
│                                                               │
│  ctx.llmResponse =                                            │
│    "## GPT-4编码能力搜索\n{A.output}\n---\n                    │
│     ## Claude编码能力搜索\n{B.output}\n---\n                  │
│     ## 综合分析\n{C.output}\n---\n                            │
│     ## 对比图表\n![chart]({D.output})\n---"                   │
│                                                               │
└───────────────────────────────────────────────────────────────┘
                             ▼
                       POSTPROCESS (700+)
                (记忆更新、消息持久化、标题生成)
```

### 6.2 Streaming 模式下的多模型编排

Streaming 模式下编排需要特殊处理：

```java
// Streaming 模式下的特殊流式策略
public void executeStreamingOrchestration(ConversationContext ctx) {
    SseEmitter emitter = (SseEmitter) ctx.getSseEmitter();

    for (OrchestrationTask task : topologicalSort(ctx.getTaskPlan())) {
        // 每个子任务开始时，发送编排进度事件
        ctx.emitSseEvent("orchestration_progress", json{
            "type": "task_start",
            "taskId": task.taskId,
            "description": task.description,
            "model": task.selectedModel,
            "totalTasks": plan.size(),
            "completedTasks": completed.size()
        });

        // 执行子任务（流式）
        ctx.setModel(task.getSelectedModel());
        modelRoutingStage.execute(ctx); // 内部会做 SSE streaming

        // 子任务完成
        ctx.emitSseEvent("orchestration_progress", json{
            "type": "task_complete",
            "taskId": task.taskId,
            "latencyMs": result.latencyMs
        });
    }

    // 全部完成后发送聚合事件
    ctx.emitSseEvent("orchestration_complete", json{
        "type": "all_tasks_complete",
        "totalLatencyMs": totalTime,
        "modelsUsed": [...]
    });
}
```

---

## 7. 前端改造

### 7.1 ChatContext 状态扩展

```typescript
// frontend/src/context/ChatContext.tsx

// 新增类型
export interface OrchestrationTaskInfo {
  taskId: string
  description: string
  model: string
  status: 'pending' | 'running' | 'completed' | 'failed'
  progress?: string
}

export interface OrchestrationState {
  isOrchestrating: boolean
  tasks: OrchestrationTaskInfo[]
  currentTaskId: string | null
  totalTasks: number
  completedTasks: number
}

// ChatState 新增字段
interface ChatState {
  // ... 现有字段
  mode: 'auto' | 'manual'                    // NEW
  selectionStrategy: string                   // NEW: 'balanced' | 'cost_first' | ...
  orchestration: OrchestrationState | null    // NEW
}

// 新增 Action types
type ChatAction =
  | { type: 'SET_MODE'; payload: 'auto' | 'manual' }
  | { type: 'SET_SELECTION_STRATEGY'; payload: string }
  | { type: 'START_ORCHESTRATION'; payload: { tasks: OrchestrationTaskInfo[] } }
  | { type: 'UPDATE_ORCHESTRATION_TASK'; payload: { taskId: string; status: string } }
  | { type: 'END_ORCHESTRATION' }
  // ... 现有 actions
```

### 7.2 Header 组件改造

```tsx
// frontend/src/components/chat/Header.tsx

// 模式切换按钮
<div className="flex items-center gap-2">
  <div className="flex bg-[var(--bg-card)] rounded-lg border theme-border-primary p-0.5">
    <button
      onClick={() => setMode('auto')}
      className={`px-3 py-1 text-xs rounded-md transition-all ${
        mode === 'auto'
          ? 'bg-[var(--brand-primary)] text-white'
          : 'theme-text-secondary'
      }`}
    >
      🤖 Auto
    </button>
    <button
      onClick={() => setMode('manual')}
      className={`px-3 py-1 text-xs rounded-md transition-all ${
        mode === 'manual'
          ? 'bg-[var(--brand-primary)] text-white'
          : 'theme-text-secondary'
      }`}
    >
      🎯 Manual
    </button>
  </div>

  {/* Manual 模式 → 显示模型下拉框（现有） */}
  {mode === 'manual' && (
    <ModelDropdown ... />
  )}

  {/* Auto 模式 → 显示策略选择器 */}
  {mode === 'auto' && (
    <StrategySelector
      value={selectionStrategy}
      onChange={setSelectionStrategy}
    />
  )}
</div>
```

### 7.3 编排进度展示组件

```tsx
// frontend/src/components/chat/OrchestrationProgress.tsx

export function OrchestrationProgress({ state }: { state: OrchestrationState }) {
  if (!state || !state.isOrchestrating) return null;

  return (
    <div className="px-4 py-3 border-b theme-border-secondary bg-[var(--bg-hover)]">
      <div className="flex items-center gap-2 mb-2">
        <Loader2 className="w-4 h-4 animate-spin theme-brand-primary" />
        <span className="text-sm font-medium theme-text-primary">
          Auto 编排中 ({state.completedTasks}/{state.totalTasks})
        </span>
      </div>
      <div className="space-y-1">
        {state.tasks.map(task => (
          <div key={task.taskId} className="flex items-center gap-2 text-xs">
            <TaskStatusIcon status={task.status} />
            <span className="theme-text-secondary">{task.description}</span>
            <span className="theme-text-muted ml-auto">{task.model}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
```

### 7.4 ChatRequest 发送改动

```typescript
// 在 sendMessage 中:
const request: ChatRequest = {
  conversationId,
  message: content.trim() || '分析图片',
  model: state.mode === 'manual' ? state.currentModel : undefined,
  imageUrls: imageUrls.length > 0 ? imageUrls : undefined,
  userId: 'default',
  webSearch,
  mode: state.mode,                          // NEW
  selectionStrategy: state.selectionStrategy,  // NEW
};
```

### 7.5 SSE 事件扩展

```typescript
// frontend/src/api/client.ts — SSE 解析增强

// 新增 SSE 事件类型处理
case 'orchestration_progress':
  const orchData = JSON.parse(event.data);
  dispatch({
    type: 'UPDATE_ORCHESTRATION_TASK',
    payload: {
      taskId: orchData.taskId,
      status: orchData.type === 'task_start' ? 'running' : 'completed',
    },
  });
  break;

case 'orchestration_complete':
  dispatch({ type: 'END_ORCHESTRATION' });
  break;
```

---

## 8. API 设计

### 8.1 现有 API 变更

| 端点 | 变更 |
|------|------|
| `POST /api/chat` | `ChatRequest` 新增 `mode`, `selectionStrategy`, `allowedModels`, `maxParallelTasks` |
| `POST /api/chat/stream` | 同上；SSE 流新增 `orchestration_progress` 和 `orchestration_complete` 事件 |
| `GET /api/models` | 新增可选参数 `?capability=code` 按能力过滤 |

### 8.2 新增 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/models/capabilities` | GET | 返回所有可用的能力标签列表 |
| `/api/models/match` | POST | 输入 `{intent, strategy}` → 返回推荐的模型列表 |
| `/api/orchestration/strategies` | GET | 返回可用的选择策略列表 |
| `/api/user/settings/auto-mode` | PUT | 更新用户的 auto mode 偏好设置 |

---

## 9. 数据库变更

### 9.1 model_configs 表新增列

```sql
ALTER TABLE model_configs
  ADD COLUMN capabilities VARCHAR(500) DEFAULT '["chat"]' COMMENT '能力标签JSON数组',
  ADD COLUMN priority INT DEFAULT 0 COMMENT '同能力下优先级',
  ADD COLUMN cost_per_1k_tokens DOUBLE DEFAULT 0.0 COMMENT '每千token成本(美元)',
  ADD COLUMN avg_latency_ms BIGINT DEFAULT 0 COMMENT '平均延迟(毫秒)',
  ADD COLUMN max_context_tokens INT DEFAULT 8192 COMMENT '最大上下文长度',
  ADD COLUMN supports_streaming BOOLEAN DEFAULT TRUE COMMENT '是否支持流式',
  ADD COLUMN supports_vision BOOLEAN DEFAULT FALSE COMMENT '是否支持视觉';
```

### 9.2 user_settings 表新增列

```sql
ALTER TABLE user_settings
  ADD COLUMN auto_mode_enabled BOOLEAN DEFAULT FALSE COMMENT '默认启用Auto模式',
  ADD COLUMN selection_strategy VARCHAR(20) DEFAULT 'balanced' COMMENT '模型选择策略',
  ADD COLUMN max_parallel_tasks INT DEFAULT 3 COMMENT '最大并行子任务数';
```

### 9.3 新增表: orchestration_logs

```sql
CREATE TABLE orchestration_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id VARCHAR(36) NOT NULL,
  message_id VARCHAR(36),
  user_message TEXT,
  detected_intent VARCHAR(50),
  is_composite BOOLEAN DEFAULT FALSE,
  total_tasks INT DEFAULT 1,
  completed_tasks INT DEFAULT 0,
  failed_tasks INT DEFAULT 0,
  models_used JSON COMMENT '使用的模型列表',
  total_latency_ms BIGINT,
  selection_strategy VARCHAR(20),
  status VARCHAR(20) COMMENT 'COMPLETED|PARTIAL|FAILED|FALLBACK',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_conversation_id (conversation_id),
  INDEX idx_created_at (created_at)
);
```

---

## 10. 改造范围清单

### 10.1 后端 — 新增文件

| # | 文件 | 说明 |
|---|------|------|
| 1 | `pipeline/stage/execution/IntentClassificationStage.java` | Layer 1: 意图分类 |
| 2 | `pipeline/stage/execution/OrchestrationPlanningStage.java` | Layer 2+3: 任务分解 + 模型匹配 |
| 3 | `pipeline/stage/execution/OrchestrationExecutionStage.java` | DAG 执行引擎 |
| 4 | `pipeline/stage/execution/ResultAggregationStage.java` | 结果合并 |
| 5 | `pipeline/stage/execution/FallbackStage.java` | 降级处理 |
| 6 | `dto/OrchestrationProgressEvent.java` | SSE 进度事件 DTO |
| 7 | `dto/ModelMatchRequest.java` | 模型匹配请求 DTO |
| 8 | `dto/ModelMatchResponse.java` | 模型匹配响应 DTO |
| 9 | `config/OrchestrationConfig.java` | 编排配置（线程池、超时等） |
| 10 | `service/OrchestrationLogService.java` | 编排日志服务 |
| 11 | `repository/OrchestrationLogRepository.java` | 编排日志 Repository |
| 12 | `entity/OrchestrationLog.java` | 编排日志 Entity |

### 10.2 后端 — 修改文件

| # | 文件 | 变更内容 |
|---|------|----------|
| 1 | `pipeline/context/ConversationContext.java` | 新增 `mode`, `selectionStrategy`, `detectedIntent`, `taskPlan`, `taskResults`, `orchestrationStatus`；新增内部类 `DetectedIntent`, `OrchestrationTask`, `TaskResult` |
| 2 | `dto/ChatRequest.java` | 新增 `mode`, `selectionStrategy`, `allowedModels`, `maxParallelTasks` |
| 3 | `entity/ModelConfig.java` | 新增 `capabilities`, `priority`, `costPer1kTokens`, `avgLatencyMs`, `maxContextTokens`, `supportsStreaming`, `supportsVision` 及辅助方法 |
| 4 | `entity/UserSetting.java` | 新增 `autoModeEnabled`, `selectionStrategy`, `maxParallelTasks` |
| 5 | `dto/ModelConfigDTO.java` | 新增对应字段 |
| 6 | `service/ModelConfigService.java` | 新增 `getConfigsByCapability()`, `matchModels()` 方法 |
| 7 | `pipeline/config/PipelineConfiguration.java` | `FULL_PIPELINE` 中插入 5 个新 Stage |
| 8 | `pipeline/stage/execution/ModelRoutingStage.java` | 支持被 Orchestration 循环调用时的上下文隔离 |
| 9 | `pipeline/ContextPipelineExecutor.java` | 新增 `executeOrchestration()` 方法（处理 streaming 下的编排进度推送） |
| 10 | `service/ChatService.java` | `generateResponse()` 中传递 mode 参数 |
| 11 | `service/StreamingService.java` | `streamResponse()` 中传递 mode 参数 |
| 12 | `controller/ChatController.java` | 新增 `/api/models/capabilities`, `/api/models/match`, `/api/orchestration/strategies` 端点 |
| 13 | `service/UserSettingService.java` | 新增 auto mode 偏好读写方法 |
| 14 | `application.yml` | 新增 `orchestration` 配置段 |

### 10.3 前端 — 新增文件

| # | 文件 | 说明 |
|---|------|------|
| 1 | `components/chat/OrchestrationProgress.tsx` | 编排进度展示组件 |
| 2 | `components/chat/StrategySelector.tsx` | 策略选择器组件 |
| 3 | `hooks/useAutoMode.ts` | Auto 模式状态管理 Hook |

### 10.4 前端 — 修改文件

| # | 文件 | 变更内容 |
|---|------|----------|
| 1 | `context/ChatContext.tsx` | 新增 `mode`, `selectionStrategy`, `orchestration` 状态；新增相关 actions 和 reducer cases |
| 2 | `components/chat/Header.tsx` | 新增 Auto/Manual 切换按钮 + 策略选择器 |
| 3 | `components/chat/InputArea/` | 发送消息时携带 `mode` 和 `selectionStrategy` |
| 4 | `types/index.ts` | 新增 `OrchestrationTaskInfo`, `OrchestrationState` 等类型；`ChatRequest` 新增字段 |
| 5 | `api/chat.ts` | SSE 解析增强（`orchestration_progress`, `orchestration_complete` 事件） |
| 6 | `api/models.ts` | 新增 `matchModels()`, `getCapabilities()` 方法 |
| 7 | `components/chat/ChatArea/` | 编排进度展示区域 |

---

## 11. 实施计划

### Phase 1: 数据基础（3-5 天）

- [ ] ModelConfig 实体 + 数据库迁移
- [ ] UserSetting 实体扩展
- [ ] ModelConfigDTO 扩展
- [ ] ModelConfigService 新增能力匹配方法
- [ ] 前端 ModelSettings UI 增加 capabilities/priority 编辑
- [ ] 为现有模型配置填充默认 capabilities

### Phase 2: 意图分类（3-4 天）

- [ ] IntentClassificationStage 实现
- [ ] 规则库建设（keyword → intent 映射）
- [ ] LLM 分类 fallback（使用本地小模型）
- [ ] 意图分类准确性测试（至少 100 条用例）
- [ ] ConversationContext 扩展（DetectedIntent）

### Phase 3: 任务编排（5-7 天）

- [ ] OrchestrationPlanningStage 实现
- [ ] OrchestrationExecutionStage 实现（DAG 执行引擎）
- [ ] ResultAggregationStage 实现
- [ ] FallbackStage 实现
- [ ] PipelineConfiguration 更新
- [ ] ContextPipelineExecutor 适配
- [ ] ChatRequest + ConversationContext 完整改造

### Phase 4: 前端适配（3-5 天）

- [ ] ChatContext 状态扩展
- [ ] Header 模式切换按钮 + 策略选择器
- [ ] OrchestrationProgress 组件
- [ ] SSE 事件解析增强
- [ ] Types 和 API 扩展

### Phase 5: Streaming + 优化（3-4 天）

- [ ] Streaming 模式下的编排进度推送
- [ ] Streaming 模式下的结果聚合
- [ ] 并行执行优化（线程池 + CompletableFuture）
- [ ] 超时控制 + 重试机制

### Phase 6: 测试 + 降级（2-3 天）

- [ ] 单元测试（各 Stage）
- [ ] 集成测试（完整 Auto 模式流程）
- [ ] 降级场景测试（所有子任务失败 → fallback）
- [ ] 性能测试（并行执行 vs 串行执行）
- [ ] orchestration_logs 写入验证

---

## 12. 风险与降级策略

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| LLM 意图分类错误 | 选错模型，用户体验差 | 规则优先，LLM 仅作为 fallback；规则命中率目标 >80% |
| 任务分解不合理 | 编排计划质量差 | 限制最大子任务数 (≤5)；提供 skip 机制 |
| 编排导致延迟增加 | 用户等待时间过长 | 并行执行；显示编排进度；超时 60s 触发 fallback |
| 子任务模型调用失败 | 部分结果缺失 | FallbackStage 降级到默认模型；重试 2 次 |
| Auto 模式成本不可控 | 大量 token 消耗 | cost_first 策略；用户可设月度预算上限（Phase 2） |
| 编排循环死锁 | 请求卡住 | 最大迭代次数限制；超时强制终止 |
| 与现有 Manual 模式冲突 | 破坏现有功能 | 所有新 Stage 通过 `isApplicable()` 隔离；Manual 路径完全不变 |

---

## 附录 A: 配置参考 (application.yml 新增)

```yaml
orchestration:
  enabled: true
  default-mode: manual             # 全局默认模式
  default-strategy: balanced       # 默认选择策略
  max-parallel-tasks: 3            # 最大并行子任务数
  max-subtasks: 5                  # 单次请求最大子任务数
  timeout-seconds: 60              # 编排总超时
  subtask-timeout-seconds: 30      # 单个子任务超时
  retry-count: 2                   # 子任务失败重试次数
  classification-model: qwen3:3b   # 意图分类模型
  planning-model: llama3           # 任务分解模型
  fallback-model: llama3           # 降级模型
  log:
    enabled: true                  # 是否记录编排日志
    retention-days: 30             # 日志保留天数
```

## 附录 B: SSE 事件协议

```
// 编排进度事件
event: orchestration_progress
data: {
  "type": "task_start" | "task_complete" | "task_failed",
  "taskId": "abc12345",
  "description": "搜索GPT-4编码能力",
  "model": "ollama:llama3",
  "totalTasks": 4,
  "completedTasks": 0,
  "timestamp": 1719700000000
}

// 编排完成事件
event: orchestration_complete
data: {
  "type": "all_tasks_complete",
  "totalLatencyMs": 8500,
  "modelsUsed": ["ollama:llama3", "openai:gpt-4", "openai:dall-e-3"],
  "taskCount": 4,
  "successCount": 4,
  "failedCount": 0
}
```
