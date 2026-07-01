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
 - Java 17 + Spring Boot 3.2.0 + Maven
 - LangChain4j 0.35.0（LLM 集成）
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

 **测试约定：**
 - 后端测试放在 `backend/src/test/java/` 下，与主代码包结构一致
 - 前端测试放在组件目录下的 `__tests__/` 子目录

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
