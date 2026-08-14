 # KChat — AGENTS.md

 这是 KChat 项目的持久约定。放入此文件的规则对 Codex 自动生效。

 ## 项目概述

 KChat 是一个 ChatGPT 风格的聊天应用，支持多模型接入、RAG 记忆、Web 搜索和图像生成。
 前后端分离的单一仓库（monorepo），`frontend/` + `backend/` 两个子目录。

 ## 技术栈

 **前端**
 - React 19 + TypeScript 5.8 + Vite 6
 - 样式: TailwindCSS 3.4 + Framer Motion 动画
 - 图标: Lucide React
 - 状态管理: React Context（无 Redux / Zustand 等外部状态库）
 - 虚拟列表: React Virtuoso
 - Markdown 渲染: react-markdown + react-syntax-highlighter + remark-gfm
 - 通知: React Toastify

 **后端**
 - Java 21 + Spring Boot 3.2.0 + Maven
 - LangChain4j 1.4.0（LLM 集成）
 - 数据库: H2（运行时）+ MySQL（可选）
 - 缓存/向量: Redis + LangChain4j Redis
 - 嵌入模型: DJL PyTorch（all-MiniLM-L6-v2，本地进程内推理）
 - 容错: Resilience4j

 ## 开发命令

 所有前端命令在 `frontend/` 目录下执行：
 ```bash
 npm run dev         # 启动开发服务器（默认提供 /api -> localhost:8080 代理）
 npm run build       # 生产构建
 npm run lint        # ESLint 检查
 npm run format      # Prettier 格式化
 npm run format:check # 格式检查
 ```

 所有后端命令在 `backend/` 目录下执行：
 ```bash
 mvn compile            # 编译
 mvn test               # 运行测试
 mvn spring-boot:run    # 启动后端服务（默认端口 8080）
 mvn package -DskipTests # 打包
 ```

 ## 代码约定

 ### 前端

 **禁止硬编码样式值。** 项目有自定义 ESLint 规则，不允许在 JSX 或样式对象中直接写颜色值和字号。一律使用：
 - Tailwind 预定义类名（`text-sm`、`bg-blue-500` 等）
 - 主题 tokens: `src/theme/tokens.ts` 中定义的 CSS 变量
 - `tokens.ts` 本身是唯一允许硬编码的例外文件

 **组件结构规则：**
 - 每个组件放在自己的目录下，`index.tsx` 作为默认导出
 - 通用 UI 组件放在 `components/ui/`
 - 业务组件按功能模块划分（`chat/`, `sidebar/`, `settings/`, `note-todo/`）
 - 跨组件共享逻辑抽取为 hooks，放在 `hooks/`（如 `useStreaming.ts`、`useConversation.ts`）
 - Context 定义放在 `context/` 目录下
 - 类型定义放在 `types/` 目录下

 **ESLint 规则要点（见 `eslint.config.js`）：**
 - `no-explicit-any`: warn
 - `no-unused-vars`: error（`_` 开头的参数忽略）
 - `no-console`: warn（允许 warn/error/log）
 - `react-hooks/exhaustive-deps`: warn
 - `react-refresh/only-export-components`: warn

 **API 调用：**
 - API 客户端在 `src/api/` 下按模块拆分（`chat.ts`、`memory.ts`、`models.ts` 等）
 - 前端发请求到 `/api/*`，Vite 开发服务器自动代理到 `http://localhost:8080`

 ### 后端

 **架构模式：** Controller → Service → Repository，标准 Spring Boot 分层
 **上下文管道架构：** 聊天消息处理经过 `pipeline/` 目录下的多阶段管道
 **DTO：** 前后端通信统一使用 `dto/` 下的 DTO 类
 **实体：** JPA 实体放在 `entity/` 目录
 **记忆系统：** 长短期记忆分别在 `memory/` 和 `service/` 中实现

 **上下文管道（Context Pipeline）架构细节：**

 一次聊天请求经过 6 个阶段、29 个 Stage，全部通过共享的 `ConversationContext` 串联（Stage 之间不互调，只读写上下文）。Executor 由 `ContextPipelineExecutor` 驱动：

 - **PREPROCESS**（100-330）：输入消毒、语言检测、Web 搜索、短期/长期记忆召回、Skill 解析
 - **ASSEMBLY**（398-480）：画像/记忆/搜索格式化、系统提示组装、消息组装、Token 管理、工具定义注入
 - **EXECUTION**（500）：模型路由与 LLM 调用
 - **AGENT**（600-680）：工具检测、执行、结果回填、循环控制（仅 Agent 模式，可重入）
 - **POSTPROCESS**（700-850）：记忆更新、消息持久化、记忆提取、Cognee 索引、标题生成、流式完成
 - **OBSERVABILITY**（900-999）：指标记录、管道审计

 三种管道类型 `SIMPLE_CHAT` / `STREAMING_CHAT` / `AGENT_CHAT` 共用同一份 Stage 列表，差异由每个 Stage 的 `isApplicable()` 守卫决定（流式独有、Agent 独有、WebSearch 开关等）。

 **Stage 设计规则（见 `pipeline/ContextPipelineStage.java`）：**
 - Stage 必须是无状态单例，所有状态写在 `ConversationContext`，不持有请求级状态
 - Stage 之间不互调，只通过 `ConversationContext` 通信
 - `isApplicable()` 只能查静态配置或请求参数，**不能查前序 Stage 产出的动态数据**（否则会被提前过滤掉）
 - `isCritical()` 控制失败是否中断管道；非关键 Stage（标题生成、指标、Agent 各阶段）失败只告警不阻断
 - `getOrder()` 返回阶段内序号，约定用 100 的倍数留插入空间
 - LangChain4j 特定类型（`AiMessage`、`ToolSpecification` 等）通过 `ctx.agentState` 这个 `Map<String,Object>` 中转，避免在 `ConversationContext` 上引入框架特定字段

 **执行入口（见 `service/ChatService.java`、`service/StreamingService.java`）：**
 - 同步：`executeWithAgentLoop()` + `executePostProcessing()`
 - 流式：`executeStreaming()`（跑到 EXECUTION 阶段），LLM 响应完成后回调 `executePostProcessing()`
 - Agent 循环：`executeWithAgentLoop()` 在 EXECUTION + AGENT 之间循环，直到无 tool_calls 或达 `maxAgentIterations`（默认 5）

 **测试约定：**
 - 后端测试放在 `backend/src/test/java/` 下，与主代码包结构一致
 - 前端测试放在组件目录下的 `__tests__/` 子目录

 ## Agent 工具调用闭环

 Agent 模式下，LLM 可通过 function calling 调用工具，Executor 在 EXECUTION ↔ AGENT 之间循环直到 LLM 给出最终文本回复。整个闭环围绕 `ConversationContext` 的三个字段：`toolCalls`（待执行）、`toolResults`（已执行）、`agentState`（Stage 间传递 LangChain4j 类型）。

 **工具注册（声明式 + Spring 自动发现）：**
 - 工具是实现 `ToolComponent` 标记接口的 Spring Bean，方法上用 `@Tool` 注解声明
 - `ToolRegistry`（`service/tool/ToolRegistry.java`）在 `@PostConstruct` 反射扫描所有 `ToolComponent` Bean 的 `@Tool` 方法，构建三张表：工具实例、`Method`、`ToolSpecification`
 - 工具名取 `@Tool(name=...)`，为空回退方法名，全局唯一（重名 override 并告警）
 - 写一个工具只需 `@Component + implements ToolComponent + @Tool` 注解，无需手动注册
 - `ToolSpecificationProvider` 支持按用户过滤已禁用工具（用户在工具箱页面关闭的工具不会出现在 LLM function calling 列表）

 **闭环各 Stage（均在 `pipeline/stage/agent/` 下，仅 `ctx.isAgentMode()` 时执行）：**

 1. **ToolDefinitionStage**（order=480，ASSEMBLY）：从 `ToolSpecificationProvider` 获取按用户过滤后的 `ToolSpecification` 列表，写入 `agentState["toolSpecifications"]` 和 `ctx.enabledToolNames`

 2. **ModelRoutingStage**（order=500，EXECUTION）：Agent 模式走 `executeWithTools` 分支
    - 流式用 `CountDownLatch` 保持同步语义：`onPartialResponse` 实时推 token 到 SSE，`onCompleteResponse` 交付完整 `AiMessage`（含 `toolExecutionRequests`），主线程 `latch.await(10min)` 阻塞
    - 图片只在第 0 轮附加（`currentIteration == 0`），返回新列表不污染 `ctx.assembledMessages`
    - 纯文本模型视觉降级：不具备 `IMAGE_IN` 时不传 imageUrls，在 UserMessage 末尾追加注释，提示 LLM 调用 `analyzeImage` 工具间接获得视觉能力
    - 结果存入 `agentState[KEY_LAST_AI_MESSAGE]`，同时更新 `ctx.llmResponse`（每轮覆盖，最终值是最后一轮文本）

 3. **ToolCallDetectionStage**（order=610）：从 `agentState` 取 `AiMessage`，**先 clear 再填充** `ctx.toolCalls`（循环重入的关键）。空则循环退出，非空转成 `ToolCallRecord` 继续

 4. **ToolInvocationStage**（order=650）：遍历 `ctx.toolCalls`，通过 `ToolExecutor` 逐一执行，结果追加到 `ctx.toolResults`
    - 单个工具失败不中断，返回 `success=false` 的 `ToolResultRecord`，LLM 下一轮可据此决定重试
    - 扫描工具返回结果中的图片 Markdown（`![alt](url)`），自动提取 URL 加入 `ctx.artifacts`，保证前端能渲染图片
    - 推送 `tool_execution` 思考事件（含实际调用的模型）

 5. **ToolResultAssemblyStage**（order=660）：把本轮 `AiMessage`（含 toolExecutionRequests）和 `ToolExecutionResultMessage` 追加到 `assembledMessages`，让下一轮 LLM 看到完整的调用请求 + 返回结果

 6. **AgentLoopControlStage**（order=680）：目前只记录迭代日志，实际终止判断在 Executor 检查 `ctx.toolCalls`

 **工具执行器（`service/tool/ToolExecutor.java`）：**
 - 从 `ToolRegistry` 查实例和 `Method`，用 LangChain4j `DefaultToolExecutor` 反射调用（自动处理参数 JSON 反序列化）
 - `UserContextHolder`（ThreadLocal）：工具由反射调用无法通过参数注入 userId，执行前 `set(userId)`，`finally clear()`，工具内部用 `UserContextHolder.get()` 读取用户为该工具配置的默认模型
 - `ToolModelUtil`：用模型能力的工具在返回文本前用 `wrap(result, modelId)` 包进 `@kc-model:<id>:end@` 标记，`ToolExecutor` 执行后 `unwrap` 把模型单独取出存入 `ToolResultRecord.model`，回填 LLM 的文本保持纯净
 - 失败不抛异常，返回 `success=false` 的 `ToolResultRecord`

 **循环终止条件：**
 - `toolCalls` 为空（LLM 不再调工具，给出最终文本）
 - 达 `maxAgentIterations`（默认 5）
 - 不可恢复错误（Critical Stage 失败）

 **后处理：** 循环退出后，调用方（`ChatService` / `StreamingService`）显式调用 `executePostProcessing()` 跑 `POSTPROCESS + OBSERVABILITY`。这样设计是为了保证 `StreamingDoneStage` 在所有 message 事件之后才发 done 事件

 **思考过程可观测性：**
 - 通过 `ctx.emitAgentThinking(type, data)` 推送 6 种事件给前端 `AgentThinkingPanel`：`tool_definition` → `llm_call` → `tool_detection` → `tool_execution` → `tool_assembly` → `final_response`
 - 每个事件带 `iteration` 和 `timestamp`，前端能完整还原每一轮思考链路
 - 同步路径 no-op，只在流式上下文推 SSE

 ## Git 约定

 - 分支前缀使用 `codex/`
 - Commit 使用约定式提交风格，描述用中文：
   - `feat: <功能描述>`
   - `fix: <修复描述>`
   - `refactor: <重构描述>`
   - `chore: <杂项描述>`
 - 提交前注意 ESLint 和类型检查（`npm run build` 会同时做 tsc 检查）

 ## 避坑指南

 - **不要硬编码颜色和字号**——自定义 ESLint 规则会报 error
 - **不要引入外部状态库**——项目统一用 React Context
 - **前后端同时开发时**，先确保后端在 8080 端口运行，再启动前端 dev server
 - **后端首次运行**需要下载 DJL PyTorch 引擎和嵌入模型（~200MB），首次构建较慢
 - **Husky 已配置**——commit 前会自动触发 lint-staged
 - **Pipeline Stage 的 `isApplicable()` 不能查动态数据**——只能查静态配置或请求参数，查前序 Stage 产出的动态数据会导致 Stage 被提前过滤掉（如 CogneeMemoryIndexStage 曾因在 `isApplicable()` 检查 `newlyExtractedMemories` 而未执行，需把动态检查移到 `execute()`）
 - **Stage 之间不互调**——所有通信通过 `ConversationContext`，Stage 是无状态单例
 - **工具执行失败不抛异常**——返回 `success=false` 的 `ToolResultRecord`，让 LLM 下一轮决定重试
 - **`UserContextHolder` 必须 `finally clear()`**——ThreadLocal 泄漏会导致下一次请求读到错误的 userId
 - **Agent 循环中图片只在第 0 轮附加**——后续轮次消息是 `ToolExecutionResultMessage`，重复附加会污染上下文
 - **后处理必须显式调用**——Agent 循环退出后，调用方需调用 `executePostProcessing()` 跑 POSTPROCESS + OBSERVABILITY，否则消息不会持久化、done 事件不会发送
 - **后端代码改动需重启**——DTO 修改、Service 逻辑变更等需重启后端进程才生效
