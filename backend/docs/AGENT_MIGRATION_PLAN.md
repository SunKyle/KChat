# KChat Agent 架构改造计划

## 一、改造概述

### 目标
- **移除**：现有多模态（Multimodal）功能模块（Planner/Execution/Compositor 三阶段 + 配置/服务/DTO/实体）
- **替换为**：全新 Agent 架构（Skill + Tool + Function Calling），将原"多模态"开关改为"Agent 模式"开关

### 核心设计

| 概念 | 定位 | 说明 |
|------|------|------|
| Model | 大脑 | 唯一决策者，通过 Function Calling 驱动 Tool |
| Skill | 角色卡 | 用户可配置，贯穿会话生命周期 |
| Tool | 手脚 | 开发者预置（@Tool 注解），纯函数，不开放用户配置 |

### 改动范围统计

| 类别 | 后端 | 前端 |
|------|------|------|
| 删除文件 | 14 个 Java 文件 | 2 个 TSX 文件 |
| 修改文件 | 7 个 Java 文件 | 6 个 TS/TSX 文件 |
| 新增文件 | 10+ 个 Java 文件 | 1 个 TSX 文件 |

---

## 二、后端改造清单

### 2.1 需删除的文件（14 个）

| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `entity/MultimodalConfig.java` | 多模态配置实体 |
| 2 | `repository/MultimodalConfigRepository.java` | 多模态配置仓库 |
| 3 | `dto/MultimodalConfigDTO.java` | 多模态配置 DTO |
| 4 | `dto/MultimodalPlan.java` | 多模态计划 DTO |
| 5 | `dto/MultimodalPlanStep.java` | 多模态计划步骤 DTO |
| 6 | `dto/MultimodalArtifact.java` | 多模态产物 DTO |
| 7 | `config/MultimodalProperties.java` | 多模态配置属性类 |
| 8 | `service/MultimodalConfigService.java` | 多模态配置服务 |
| 9 | `service/MultimodalPlannerService.java` | 多模态规划服务 |
| 10 | `service/ai/MultimodalPlannerAI.java` | 多模态规划 AI 接口 |
| 11 | `controller/MultimodalConfigController.java` | 多模态配置控制器 |
| 12 | `pipeline/stage/preprocess/MultimodalPlannerStage.java` | 多模态规划 Stage |
| 13 | `pipeline/stage/execution/MultimodalExecutionStage.java` | 多模态执行 Stage |
| 14 | `pipeline/stage/execution/MultimodalCompositorStage.java` | 多模态合成 Stage |
| 15 | `test/.../MultimodalPlannerServiceTest.java` | 多模态测试 |

### 2.2 需修改的文件（7 个）

| # | 文件路径 | 改动内容 |
|---|---------|---------|
| 1 | `dto/ChatRequest.java` | `multimodal` → `agentMode` |
| 2 | `pipeline/context/ConversationContext.java` | 移除 multimodal 字段组，新增 agent 字段组 |
| 3 | `pipeline/config/PipelineConfiguration.java` | 删除 multimodal Stage，新增 agent Stage |
| 4 | `pipeline/stage/execution/ModelRoutingStage.java` | `isApplicable` 改为支持 AGENT 模式走 AiServices + tools |
| 5 | `service/ChatService.java` | `setMultimodal()` → `setAgentMode()` + AGENT 模式走 `executeWithAgentLoop` |
| 6 | `service/StreamingService.java` | `setMultimodal()` → `setAgentMode()` + AGENT 模式走 agent 循环 |
| 7 | `service/ai/AiServiceFactory.java` | 新增带 tools 参数的重载方法 |

### 2.3 需新增的文件（10+ 个）

#### Agent 基础设施
| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `service/tool/ToolRegistry.java` | Tool 注册表，扫描 @Tool 注解 |
| 2 | `service/tool/ToolExecutor.java` | Tool 执行器，统一调用入口 |
| 3 | `service/tool/ToolSpecificationProvider.java` | Tool 规格提供者，生成 function calling schema |
| 4 | `service/tool/tools/DateTimeTool.java` | 内置日期时间 Tool（验证闭环） |
| 5 | `pipeline/stage/agent/SkillResolutionStage.java` (order=330) | Skill 激活 + 多模态仲裁 |
| 6 | `pipeline/stage/agent/ToolDefinitionStage.java` (order=480) | 注入 Tool 规格到上下文 |
| 7 | `pipeline/stage/agent/ToolCallDetectionStage.java` (order=610) | 检测 tool_calls |
| 8 | `pipeline/stage/agent/ToolInvocationStage.java` (order=650) | 执行 Tool（含 Skill 前置钩子） |
| 9 | `pipeline/stage/agent/ToolResultAssemblyStage.java` (order=660) | 结果回填到 ctx |
| 10 | `pipeline/stage/agent/AgentLoopControlStage.java` (order=680) | 循环控制（iteration/token/error） |
| 11 | `pipeline/stage/agent/SkillCompletionHookStage.java` (order=810) | 状态保存 + 轨迹持久化 |

#### 后续扩展（MVP 后）
| # | 文件路径 | 说明 |
|---|---------|------|
| 12 | `entity/Skill.java` | Skill 实体 |
| 13 | `entity/SkillExecution.java` | Skill 执行轨迹实体 |
| 14 | `service/SkillService.java` | Skill CRUD 服务 |
| 15 | `controller/SkillController.java` | Skill 管理 API |

---

## 三、前端改造清单

### 3.1 需删除/替换的文件

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| 1 | `components/settings/MultimodalSettings.tsx` | **删除** | 替换为 AgentSettings |
| 2 | `types/user.ts` 中的 `MultimodalConfig` | **删除** | 替换为 `AgentConfig` |

### 3.2 需修改的文件（6 个）

| # | 文件路径 | 改动内容 |
|---|---------|---------|
| 1 | `types/index.ts` | `ChatRequest.multimodal` → `agentMode` |
| 2 | `components/chat/InputArea/index.tsx` | 多模态开关 → Agent 模式开关（图标 Orbit → Bot/Agent，文案变更） |
| 3 | `context/ChatContext.tsx` | `sendMessage` 参数 `multimodal` → `agentMode` |
| 4 | `hooks/useSettings.ts` | `SettingsTab.multimodal` → `agent` |
| 5 | `components/settings/UserSettings.tsx` | tab 配置：多模态 → Agent 模式 |
| 6 | `api/user.ts` | `multimodalApi` → `agentApi`，接口路径调整 |

### 3.3 需新增的文件

| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `components/settings/AgentSettings.tsx` | Agent 模式配置页（Skill 选择、Tool 权限、执行策略） |

---

## 四、实施步骤（按依赖顺序）

### 阶段 1：清理 + 基础设施（后端优先）

**Step 1.1：删除多模态文件**
- 删除 14 个后端文件（见 2.1 清单）
- 删除 2 个前端文件（MultimodalSettings.tsx、MultimodalConfig 类型）
- 清理 import 引用，确保编译通过

**Step 1.2：改造 ConversationContext**
- 移除字段：`multimodal`、`multimodalPlan`、`artifacts`
- 保留/新增 agent 字段：`toolCalls`、`toolResults`、`enabledToolNames`、`agentState`（已存在）
- 新增：`agentMode`（boolean）、`activeSkill`、`currentIteration`、`maxAgentIterations`（已存在）
- 新增 agentState 常用 key 常量

**Step 1.3：改造 ChatRequest DTO**
- `multimodal` → `agentMode`

**Step 1.4：新建 Tool 基础设施**
- 创建 `service/tool/` 包
- 实现 `ToolRegistry`：扫描 `@Tool` 注解的 Spring Bean，注册工具
- 实现 `ToolExecutor`：统一执行入口，处理异常和权限
- 实现 `ToolSpecificationProvider`：从 `@Tool` 注解生成 function calling schema
- 内置 `DateTimeTool`（获取当前时间/日期）

**Step 1.5：改造 AiServiceFactory**
- 新增 `create(Class<T>, String modelId, List<Object> tools)` 重载
- 支持将 Tool 列表绑定到 AiServices

**Step 1.6：改造 PipelineConfiguration**
- 从 FULL_PIPELINE 删除：`multimodalPlannerStage`、`multimodalExecutionStage`、`multimodalCompositorStage`
- 新增：`skillResolutionStage`(330)、`toolDefinitionStage`(480)、`toolCallDetectionStage`(610)、`toolInvocationStage`(650)、`toolResultAssemblyStage`(660)、`agentLoopControlStage`(680)、`skillCompletionHookStage`(810)

**Step 1.7：改造 ModelRoutingStage**
- `isApplicable`: `!ctx.isMultimodal()` → 始终返回 true（不再需要 multimodal 互斥）
- `execute`: AGENT 模式走 AiServices + tools，非 AGENT 模式保持现有逻辑
- 新增 `executeWithTools()` 方法：构建带 tool 的 AiServices 调用

**Step 1.8：创建 AGENT Stage**
- `SkillResolutionStage`(330)：解析 Skill 配置，注入 systemPrompt
- `ToolDefinitionStage`(480)：将 Tool 规格注入上下文
- `ToolCallDetectionStage`(610)：检测 LLM 响应中的 tool_calls
- `ToolInvocationStage`(650)：执行检测到的 Tool 调用
- `ToolResultAssemblyStage`(660)：将 Tool 结果回填到 assembledMessages
- `AgentLoopControlStage`(680)：判断是否继续循环或终止
- `SkillCompletionHookStage`(810)：保存执行状态和轨迹

**Step 1.9：改造 ChatService + StreamingService**
- `setMultimodal()` → `setAgentMode()`
- AGENT 模式下调用 `pipelineExecutor.executeWithAgentLoop(ctx)`

### 阶段 2：前端改造

**Step 2.1：类型定义改造**
- `ChatRequest.multimodal` → `agentMode`
- 删除 `MultimodalConfig`，新增 `AgentConfig`

**Step 2.2：开关改造**
- InputArea：多模态开关 → Agent 模式开关
- 图标：Orbit → Bot（或 Activity）
- 文案："多模态" → "Agent"
- 状态颜色：保持 brand-primary 色系

**Step 2.3：API 层改造**
- `multimodalApi` → `agentApi`
- 请求参数 `multimodal` → `agentMode`

**Step 2.4：设置页改造**
- UserSettings tab："多模态模型" → "Agent 模式"
- 新建 AgentSettings.tsx 组件

### 阶段 3：验证

**Step 3.1：后端编译 + 测试**
```bash
cd backend && mvn compile test
```

**Step 3.2：前端构建**
```bash
cd frontend && npm run build
```

**Step 3.3：手动验证**
- 普通聊天模式正常工作
- Agent 模式下 Tool calling 闭环（DateTimeTool 可用）
- SSE 流式 Agent 响应正常

---

## 五、风险点与应对

| # | 风险 | 影响 | 应对 |
|---|------|------|------|
| 1 | LangChain4j 0.35 流式 + Tool calling 不稳定 | 高 | Agent 循环强制同步；流式场景下 Tool 执行走同步分支，结果通过 SSE 推送 |
| 2 | 删除多模态代码后 H2 数据库表不存在 | 中 | H2 自动建表，无历史数据迁移需求；MySQL 场景需手动 DROP TABLE |
| 3 | Function calling 与现有 Ollama 模型兼容性 | 中 | Ollama 部分模型不支持 function calling，需做能力降级检测 |
| 4 | Agent 循环 token 溢出 | 中 | 设 maxIterations（默认 5）+ 每轮 token 检查 |
| 5 | 前端开关状态持久化 | 低 | Agent 模式开关为会话级（非持久化），与原多模态行为一致 |

---

## 六、Agent 架构 Pipeline 流程图

```
用户输入
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  PREPROCESS Phase                                        │
│  100 InputSanitizationStage                              │
│  110 LanguageDetectionStage                              │
│  200 WebSearchStage                                      │
│  300 ShortTermMemoryStage                                │
│  310 LongTermMemoryStage                                 │
│  330 SkillResolutionStage  ← 新增：Skill 激活            │
└─────────────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  ASSEMBLY Phase                                          │
│  398 UserProfileFormatStage                              │
│  400 MemoryFormatStage                                   │
│  405 SearchContextFormatStage                            │
│  410 SystemPromptAssemblyStage  ← 改造：注入 Skill Prompt│
│  430 MessageAssemblyStage                                │
│  440 TokenManagementStage                                │
│  480 ToolDefinitionStage  ← 新增：注入 Tool 规格         │
└─────────────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  EXECUTION Phase                                         │
│  500 ModelRoutingStage  ← 改造：AGENT 走 AiServices+Tools│
└─────────────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  AGENT Phase（循环执行）                                 │
│  610 ToolCallDetectionStage  ← 检测 tool_calls          │
│  650 ToolInvocationStage  ← 执行 Tool                   │
│  660 ToolResultAssemblyStage  ← 结果回填                │
│  680 AgentLoopControlStage  ← 循环控制                  │
└─────────────────────────────────────────────────────────┘
  │ (无 tool_calls 或达 maxIterations 后退出循环)
  ▼
┌─────────────────────────────────────────────────────────┐
│  POSTPROCESS Phase                                       │
│  700 ShortTermMemoryUpdateStage                          │
│  710 MessagePersistenceStage                             │
│  720 MemoryExtractionStage                               │
│  800 TitleGenerationStage                                │
│  810 SkillCompletionHookStage  ← 新增：状态持久化       │
│  850 StreamingDoneStage                                  │
└─────────────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  OBSERVABILITY Phase                                     │
│  900 MetricsRecordingStage                               │
│  999 PipelineAuditStage                                  │
└─────────────────────────────────────────────────────────┘
```

---

## 七、关键文件索引（改造后）

| 关注点 | 文件路径 |
|--------|---------|
| Pipeline 核心 | `backend/.../pipeline/ContextPipelineStage.java` |
| 上下文 | `backend/.../pipeline/context/ConversationContext.java` |
| 编排器 | `backend/.../pipeline/ContextPipelineExecutor.java` |
| Stage 配置 | `backend/.../pipeline/config/PipelineConfiguration.java` |
| LLM 工厂 | `backend/.../service/ai/AiServiceFactory.java` |
| Model 路由 | `backend/.../pipeline/stage/execution/ModelRoutingStage.java` |
| Tool 注册 | `backend/.../service/tool/ToolRegistry.java` |
| Tool 执行 | `backend/.../service/tool/ToolExecutor.java` |
| 前端开关 | `frontend/.../components/chat/InputArea/index.tsx` |
| 前端类型 | `frontend/.../types/index.ts` |
| 设置页 | `frontend/.../components/settings/UserSettings.tsx` |
