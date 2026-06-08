# KChat 前端项目改进方案

## 目录
- [一、问题根源分析](#一问题根源分析)
- [二、系统性改进策略](#二系统性改进策略)
- [三、预期改进目标及关键成功指标](#三预期改进目标及关键成功指标)
- [四、改进任务清单](#四改进任务清单)

---

## 一、问题根源分析

### 1.1 架构问题根源

| 问题 | 根源分析 |
|------|---------|
| 组件重复与结构混乱 | 缺乏统一的组件架构规范，多人协作时未遵循相同的分类标准 |
| ChatContext 过于臃肿 | 初期快速迭代时未进行状态管理分层，功能逐步累加导致 |
| API 层架构缺陷 | 缺少 API 层设计规范，memoryApi 为临时实现未与主 API 层整合 |
| 状态管理策略不清晰 | 未明确状态分层策略，混合使用多种存储方式 |

### 1.2 代码组织缺陷根源

| 问题 | 根源分析 |
|------|---------|
| 组件文件过大 | 缺少代码审查和重构流程，功能累加未及时拆分 |
| 命名规范不统一 | 缺少团队编码规范文档 |
| 工具函数组织混乱 | utils 目录定位不清晰，与其他目录职责重叠 |
| 类型定义分散 | 缺少类型组织规范 |

### 1.3 性能隐患根源

| 问题 | 根源分析 |
|------|---------|
| 不必要的重新渲染 | 状态管理未优化，缺少性能监控机制 |
| 消息列表渲染优化不足 | 未考虑大量消息场景，缺少性能测试 |
| 没有代码分割 | 未实施路由级别的按需加载策略 |
| 图片资源处理不完善 | 缺少资源优化策略 |

### 1.4 可维护性风险根源

| 问题 | 根源分析 |
|------|---------|
| 测试缺失 | 项目早期未建立测试文化和流程 |
| 文档不足 | 缺少文档编写规范和激励机制 |
| ESLint 规则不全 | 仅关注样式规范，未覆盖最佳实践 |
| Git 管理不规范 | 缺少 Git 工作流程规范 |

### 1.5 安全问题根源

| 问题 | 根源分析 |
|------|---------|
| 环境变量暴露 | 缺少安全意识和 Git 配置检查流程 |
| XSS 风险 | 未实施输入验证和输出编码策略 |
| API 请求安全不足 | 未考虑安全性设计 |
| localStorage 安全 | 缺少数据安全策略 |

---

## 二、系统性改进策略

### 2.1 架构优化策略

**核心原则：**
- 单一职责原则
- 分层架构
- 高内聚低耦合

**具体策略：**

1. **状态管理重构**
   - 拆分大型 Context 为多个小型 Context
   - 考虑引入 Zustand 进行状态管理（可选）
   - 明确状态分层策略（本地状态、应用状态、服务器状态）

2. **API 层统一**
   - 建立统一的 API 客户端封装
   - 实现请求/响应拦截器
   - 统一错误处理机制

3. **组件架构规范化**
   - 制定组件分类标准
   - 建立组件文件结构规范
   - 实现组件复用策略

### 2.2 代码质量提升策略

**核心原则：**
- 可读性优先
- 可测试性优先
- 渐进式重构

**具体策略：**

1. **代码规范化**
   - 制定编码规范文档
   - 完善 ESLint 规则
   - 配置 Prettier 格式化
   - 建立 Git hooks 流程

2. **测试体系建立**
   - 单元测试（Vitest）
   - 组件测试（React Testing Library）
   - E2E 测试（Playwright，可选）

3. **文档体系完善**
   - 架构决策记录（ADR）
   - 组件使用文档
   - 开发指南

### 2.3 性能优化策略

**核心原则：**
- 按需加载
- 避免不必要的渲染
- 资源优化

**具体策略：**

1. **渲染优化**
   - React.memo 优化组件
   - useMemo/useCallback 合理使用
   - 虚拟化长列表

2. **代码分割**
   - 路由级别的懒加载
   - 组件级别的按需加载

3. **资源优化**
   - 图片压缩和懒加载
   - Bundle 分析和优化

### 2.4 安全加固策略

**核心原则：**
- 防御性编程
- 最小权限原则
- 安全左移

**具体策略：**

1. **输入验证**
   - 用户输入验证
   - XSS 防护
   - 类型安全

2. **数据安全**
   - 敏感数据加密
   - 环境变量管理
   - 安全的存储策略

3. **API 安全**
   - CSRF 防护
   - 请求签名
   - 错误信息脱敏

---

## 三、预期改进目标及关键成功指标

### 3.1 架构质量目标

| 目标 | 关键成功指标（KPI） |
|------|-------------------|
| 组件结构清晰 | 无重复组件，组件分类清晰，单一文件不超过 300 行 |
| 状态管理高效 | Context 拆分后单个 Context 不超过 200 行，重新渲染减少 50% |
| API 层统一 | 所有 API 调用通过统一客户端，错误处理覆盖率 100% |

### 3.2 代码质量目标

| 目标 | 关键成功指标（KPI） |
|------|-------------------|
| 测试覆盖 | 核心业务逻辑单元测试覆盖率 ≥ 70% |
| 代码规范 | ESLint 无错误，Prettier 格式化 100% |
| 文档完善 | 新增组件都有使用文档，架构决策有记录 |

### 3.3 性能目标

| 目标 | 关键成功指标（KPI） |
|------|-------------------|
| 首屏加载 | LCP &lt; 2s，FID &lt; 100ms |
| 运行性能 | 消息列表滚动 60fps，无明显卡顿 |
| Bundle 大小 | Gzip 后 &lt; 500KB |

### 3.4 安全目标

| 目标 | 关键成功指标（KPI） |
|------|-------------------|
| 无敏感信息泄露 | .env 等敏感文件不提交到 Git |
| XSS 防护 | 用户输入全部经过验证和清理 |
| 安全审计 | 通过 ESLint 安全规则检查 |

---

## 四、改进任务清单

### Phase 1: 安全与基础设施（高优先级，1-2 周）

#### Task 1.1: 修复 Git 安全问题

**任务描述：** 移除仓库中的敏感文件，建立安全的环境变量管理机制

**具体内容：**
- 从 Git 历史中移除 `.env` 文件
- 创建 `.env.example` 模板文件
- 更新 `.gitignore` 添加前端特定规则
- 配置 Git 预提交钩子检查敏感文件

**验收标准：**
- `.env` 不出现在 Git 仓库中
- `.env.example` 包含所有必需的环境变量示例
- `.gitignore` 包含 `node_modules`、`.env`、`dist` 等规则

**涉及文件：**
- [frontend/.env](file:///Users/sunxiaokai/Desktop/KChat/frontend/.env)
- [frontend/.gitignore](file:///Users/sunxiaokai/Desktop/KChat/frontend/.gitignore)

**预估时间：** 2 小时

---

#### Task 1.2: 完善 ESLint 配置并修复现有问题

**任务描述：** 完善 ESLint 规则配置，修复现有代码中的问题

**具体内容：**
- 配置 TypeScript 严格模式
- 添加 React 最佳实践规则
- 添加安全相关规则
- 添加 Import 排序规则
- 修复现有代码中的 ESLint 错误

**验收标准：**
- `npm run lint` 无错误
- 关键规则已启用（react-hooks/exhaustive-deps 等）

**涉及文件：**
- [frontend/eslint.config.js](file:///Users/sunxiaokai/Desktop/KChat/frontend/eslint.config.js)
- [frontend/tsconfig.json](file:///Users/sunxiaokai/Desktop/KChat/frontend/tsconfig.json)

**预估时间：** 4-6 小时

---

#### Task 1.3: 配置 Prettier 和 Git hooks

**任务描述：** 配置 Prettier 格式化工具和 Husky Git hooks

**具体内容：**
- 安装 Prettier 和相关依赖
- 创建 Prettier 配置文件
- 配置 package.json 格式化脚本
- 安装和配置 Husky
- 配置 lint-staged
- 添加 pre-commit hook（lint + format）

**验收标准：**
- `npm run format` 可以格式化所有代码
- 提交代码时自动运行 lint 和 format
- 代码风格一致

**涉及文件：**
- frontend/package.json
- frontend/.prettierrc
- frontend/.prettierignore

**预估时间：** 3 小时

---

### Phase 2: API 层统一与重构（高优先级，1 周）

#### Task 2.1: 重构 API 客户端

**任务描述：** 完善 `api/client.ts`，添加请求/响应拦截器

**具体内容：**
- 实现请求拦截器（添加认证头、请求 ID 等）
- 实现响应拦截器（统一错误处理、数据转换）
- 完善超时和重试机制
- 添加请求取消机制（AbortController）
- 添加请求日志（开发环境）

**验收标准：**
- 所有 API 请求通过统一客户端
- 错误处理统一且完善
- 支持请求取消

**涉及文件：**
- [frontend/src/api/client.ts](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/api/client.ts)

**预估时间：** 4-5 小时

---

#### Task 2.2: 整合 Memory API

**任务描述：** 将 `utils/memoryApi.ts` 的功能迁移到 `api/` 目录

**具体内容：**
- 在 `api/` 目录下创建 `memory.ts`
- 使用统一的 API 客户端重写 memory API
- 更新所有使用 `memoryApi` 的地方
- 删除 `utils/memoryApi.ts`
- 更新相关类型定义

**验收标准：**
- 所有记忆相关功能正常工作
- 无 `utils/memoryApi.ts` 引用
- 类型安全无错误

**涉及文件：**
- [frontend/src/utils/memoryApi.ts](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/utils/memoryApi.ts)
- frontend/src/api/memory.ts（新建）
- 使用 memoryApi 的组件

**预估时间：** 3-4 小时

---

#### Task 2.3: 完善 API 错误处理

**任务描述：** 建立统一的错误处理和用户提示机制

**具体内容：**
- 定义标准错误类型
- 实现错误重试策略
- 添加全局错误状态管理
- 实现友好的错误提示组件
- 错误信息脱敏（生产环境）

**验收标准：**
- 所有 API 错误都有适当的用户提示
- 生产环境不泄露敏感错误信息
- 可重试的错误自动重试

**涉及文件：**
- frontend/src/api/client.ts
- frontend/src/context/ErrorContext.tsx（新建）
- frontend/src/components/common/ErrorToast.tsx（新建）

**预估时间：** 3-4 小时

---

### Phase 3: 组件架构优化（高优先级，1-2 周）

#### Task 3.1: 合并重复组件

**任务描述：** 合并重复的 Header 和 Icon 组件

**具体内容：**
- 对比两个 Header 组件的功能差异
- 合并为一个统一的 Header 组件（保留在 layout/ 目录）
- 删除 `components/Header/index.tsx`
- 更新所有引用
- 对比两个 Icon 组件的功能差异
- 合并为一个统一的 Icon 组件（保留在 ui/ 目录）
- 删除 `components/Icon/index.tsx`
- 更新所有引用

**验收标准：**
- 无重复组件
- 所有功能正常工作
- 组件引用全部更新

**涉及文件：**
- [frontend/src/components/Header/index.tsx](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/components/Header/index.tsx)
- [frontend/src/components/layout/Header.tsx](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/components/layout/Header.tsx)
- [frontend/src/components/Icon/index.tsx](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/components/Icon/index.tsx)
- [frontend/src/components/ui/Icon.tsx](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/components/ui/Icon.tsx)

**预估时间：** 3-4 小时

---

#### Task 3.2: 建立组件分类规范

**任务描述：** 制定组件架构规范并重组目录结构

**具体内容：**
- 制定组件分类文档（ADR）
- 明确各目录职责：
  - `layout/`: 页面布局组件
  - `features/`: 业务功能组件（按功能模块划分）
  - `common/`: 通用业务组件
  - `ui/`: 基础 UI 组件
- 重组现有组件到正确的目录
- 更新所有 import 路径
- 创建组件模板文件

**验收标准：**
- 所有组件分类清晰
- 目录结构符合规范
- 无 import 错误

**涉及文件：**
- frontend/src/components/ 目录结构重组
- docs/adr/001-component-architecture.md（新建）

**预估时间：** 4-5 小时

---

#### Task 3.3: 重构 App.tsx

**任务描述：** 将 App.tsx 拆分为更小的模块

**具体内容：**
- 提取侧边栏状态管理为独立 hook
- 提取设置面板逻辑为独立组件
- 提取确认对话框逻辑
- 简化主 App 组件
- 添加类型注解

**验收标准：**
- App.tsx 不超过 100 行
- 功能保持完整
- 代码更易读

**涉及文件：**
- [frontend/src/App.tsx](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/App.tsx)
- frontend/src/hooks/useSidebar.ts（新建）
- frontend/src/components/SettingsPanel.tsx（新建）

**预估时间：** 3-4 小时

---

### Phase 4: 状态管理重构（高优先级，1-2 周）

#### Task 4.1: 拆分 ChatContext

**任务描述：** 将 ChatContext 拆分为多个小型 Context

**具体内容：**
- 创建 ConversationContext（对话列表管理）
- 创建 MessageContext（消息管理）
- 创建 ModelContext（模型管理）
- 迁移相关状态和方法
- 更新所有使用 ChatContext 的组件
- 删除原 ChatContext

**验收标准：**
- 功能完整无回归
- 每个 Context 不超过 200 行
- 类型安全

**涉及文件：**
- [frontend/src/context/ChatContext.tsx](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/context/ChatContext.tsx)
- frontend/src/context/ConversationContext.tsx（新建）
- frontend/src/context/MessageContext.tsx（新建）
- frontend/src/context/ModelContext.tsx（新建）

**预估时间：** 8-10 小时

---

#### Task 4.2: 优化状态更新性能

**任务描述：** 使用 useRef 和 useMemo 优化 Context 性能

**具体内容：**
- 使用 useRef 保持对 state 的引用，避免依赖问题
- 使用 useMemo 优化 Context.Provider 的 value
- 使用 React.memo 优化消费 Context 的组件
- 添加状态变更日志（开发环境）
- 验证性能提升

**验收标准：**
- 不必要的重新渲染减少 50%
- 功能正常无回归

**涉及文件：**
- 所有 Context 文件
- 主要消费组件

**预估时间：** 4-5 小时

---

#### Task 4.3: 实现状态持久化策略

**任务描述：** 建立统一的状态持久化机制

**具体内容：**
- 创建持久化工具函数
- 实现 IndexedDB 存储（大量数据）
- 实现 localStorage 存储（配置数据）
- 添加数据版本管理和迁移策略
- 实现持久化的配置选项

**验收标准：**
- 刷新页面后状态保持
- 大数据量存储性能良好
- 支持数据迁移

**涉及文件：**
- frontend/src/utils/storage.ts（新建）
- frontend/src/hooks/usePersistedState.ts（新建）

**预估时间：** 4-5 小时

---

### Phase 5: 性能优化（中优先级，1-2 周）

#### Task 5.1: 优化消息列表渲染

**任务描述：** 使用 React.memo 和虚拟化优化消息列表

**具体内容：**
- 使用 React.memo 包装 MessageBubble 组件
- 实现消息的浅比较
- 添加 react-window 或 react-virtualized
- 实现虚拟化列表
- 添加滚动性能监控

**验收标准：**
- 1000 条消息滚动流畅（60fps）
- 内存占用合理

**涉及文件：**
- [frontend/src/components/ChatArea/MessageBubble.tsx](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/components/ChatArea/MessageBubble.tsx)
- [frontend/src/components/ChatArea/index.tsx](file:///Users/sunxiaokai/Desktop/KChat/frontend/src/components/ChatArea/index.tsx)

**预估时间：** 4-5 小时

---

#### Task 5.2: 实现代码分割

**任务描述：** 添加路由级别的代码分割和懒加载

**具体内容：**
- 配置 React.lazy 和 Suspense
- 设置页面懒加载
- 记忆面板懒加载
- 设置面板懒加载
- 添加加载状态组件
- 配置 preload 策略

**验收标准：**
- 首屏 bundle 减少 30%
- 按需加载工作正常
- 加载体验流畅

**涉及文件：**
- frontend/src/App.tsx
- frontend/src/components/LazyLoad.tsx（新建）

**预估时间：** 3-4 小时

---

#### Task 5.3: 图片资源优化

**任务描述：** 实现图片懒加载和优化

**具体内容：**
- 添加 react-lazyload 或 IntersectionObserver
- 图片上传前压缩
- 图片格式检查和限制
- 添加图片加载失败占位图
- 配置 Vite 图片优化

**验收标准：**
- 图片懒加载正常工作
- 上传图片有大小限制
- 加载失败有降级处理

**涉及文件：**
- 图片相关组件
- [frontend/vite.config.ts](file:///Users/sunxiaokai/Desktop/KChat/frontend/vite.config.ts)

**预估时间：** 3-4 小时

---

### Phase 6: 测试体系建立（中优先级，2-3 周）

#### Task 6.1: 配置测试环境

**任务描述：** 安装和配置 Vitest 和 React Testing Library

**具体内容：**
- 安装 Vitest、jsdom、@testing-library/react 等依赖
- 配置 Vitest 配置文件
- 配置测试 setup 文件
- 添加测试脚本到 package.json
- 配置覆盖率报告

**验收标准：**
- `npm run test` 可以运行测试
- 测试覆盖率报告正常生成

**涉及文件：**
- frontend/package.json
- frontend/vitest.config.ts（新建）
- frontend/src/test/setup.ts（新建）

**预估时间：** 3 小时

---

#### Task 6.2: 编写单元测试

**任务描述：** 为核心工具函数和 hooks 编写单元测试

**具体内容：**
- 测试 API 客户端函数
- 测试 storage 工具函数
- 测试自定义 hooks
- 测试主题相关函数
- 达到 70% 核心代码覆盖率

**验收标准：**
- 核心逻辑测试覆盖 ≥ 70%
- 所有测试通过

**涉及文件：**
- frontend/src/api/__tests__/
- frontend/src/utils/__tests__/
- frontend/src/hooks/__tests__/

**预估时间：** 8-10 小时

---

#### Task 6.3: 编写组件测试

**任务描述：** 为关键组件编写集成测试

**具体内容：**
- 测试 MessageBubble 组件
- 测试 InputArea 组件
- 测试主要布局组件
- 测试表单组件
- 使用 Mock Service Worker 模拟 API

**验收标准：**
- 关键组件都有测试
- 测试覆盖主要用户交互
- 所有测试通过

**涉及文件：**
- frontend/src/components/__tests__/

**预估时间：** 8-10 小时

---

### Phase 7: 文档体系完善（低优先级，1-2 周）

#### Task 7.1: 编写架构决策记录

**任务描述：** 为关键技术决策编写 ADR

**具体内容：**
- 组件架构决策 ADR
- 状态管理方案 ADR
- API 层设计 ADR
- 测试策略 ADR
- 样式方案 ADR

**验收标准：**
- 至少 5 份 ADR
- 文档格式规范
- 决策背景和理由清晰

**涉及文件：**
- docs/adr/001-component-architecture.md
- docs/adr/002-state-management.md
- docs/adr/003-api-layer.md
- ...

**预估时间：** 4-5 小时

---

#### Task 7.2: 编写开发指南

**任务描述：** 编写完整的开发文档

**具体内容：**
- 项目结构说明
- 组件开发指南
- 状态管理指南
- API 调用指南
- 样式规范指南
- 测试指南
- Git 工作流程

**验收标准：**
- 新开发者可以根据文档独立开发
- 文档结构清晰易读

**涉及文件：**
- docs/development.md
- docs/component-guide.md
- docs/testing-guide.md
- ...

**预估时间：** 5-6 小时

---

#### Task 7.3: 组件文档化

**任务描述：** 为主要组件编写使用文档和示例

**具体内容：**
- 使用 JSDoc 注释组件 Props
- 编写组件使用示例
- 配置 Storybook（可选）
- 为通用组件编写 README

**验收标准：**
- 主要组件都有文档
- Props 类型和说明清晰
- 有使用示例

**涉及文件：**
- 各组件文件的 JSDoc
- frontend/src/components/ui/README.md

**预估时间：** 4-5 小时

---

### Phase 8: 持续优化（低优先级，持续进行）

#### Task 8.1: Bundle 分析和优化

**任务描述：** 定期分析和优化 bundle 大小

**具体内容：**
- 配置 rollup-plugin-visualizer
- 定期运行 bundle 分析
- 识别大依赖并优化
- 实施 tree-shaking 优化
- 考虑按需引入第三方库

**验收标准：**
- Gzip 后 bundle &lt; 500KB
- 无重复依赖
- 按需加载工作正常

**涉及文件：**
- [frontend/vite.config.ts](file:///Users/sunxiaokai/Desktop/KChat/frontend/vite.config.ts)

**预估时间：** 3-4 小时（持续）

---

#### Task 8.2: 性能监控

**任务描述：** 建立性能监控机制

**具体内容：**
- 添加 Core Web Vitals 监控
- 添加错误监控（Sentry 可选）
- 添加性能埋点
- 建立性能基准测试

**验收标准：**
- 有性能数据收集
- 有性能退化告警机制
- 有性能基准对比

**涉及文件：**
- frontend/src/utils/performance.ts（新建）
- frontend/src/main.tsx

**预估时间：** 4-5 小时

---

#### Task 8.3: 安全审计和加固

**任务描述：** 定期进行安全审计和加固

**具体内容：**
- 定期运行 npm audit
- 配置 dependabot
- 添加 eslint-plugin-security
- 进行 XSS 测试
- 进行依赖漏洞扫描

**验收标准：**
- 无高危漏洞
- 依赖保持更新
- 通过安全工具扫描

**涉及文件：**
- frontend/package.json
- frontend/eslint.config.js
- .github/dependabot.yml（新建）

**预估时间：** 3-4 小时（持续）

---

## 附录：任务追踪表

| 阶段 | 任务 | 状态 | 负责人 | 预计时间 | 实际时间 |
|------|------|------|--------|----------|----------|
| Phase 1 | Task 1.1: 修复 Git 安全问题 | 待开始 | | 2h | |
| Phase 1 | Task 1.2: 完善 ESLint 配置 | 待开始 | | 4-6h | |
| Phase 1 | Task 1.3: 配置 Prettier 和 Git hooks | 待开始 | | 3h | |
| Phase 2 | Task 2.1: 重构 API 客户端 | 待开始 | | 4-5h | |
| Phase 2 | Task 2.2: 整合 Memory API | 待开始 | | 3-4h | |
| Phase 2 | Task 2.3: 完善 API 错误处理 | 待开始 | | 3-4h | |
| Phase 3 | Task 3.1: 合并重复组件 | 待开始 | | 3-4h | |
| Phase 3 | Task 3.2: 建立组件分类规范 | 待开始 | | 4-5h | |
| Phase 3 | Task 3.3: 重构 App.tsx | 待开始 | | 3-4h | |
| Phase 4 | Task 4.1: 拆分 ChatContext | 待开始 | | 8-10h | |
| Phase 4 | Task 4.2: 优化状态更新性能 | 待开始 | | 4-5h | |
| Phase 4 | Task 4.3: 实现状态持久化策略 | 待开始 | | 4-5h | |
| Phase 5 | Task 5.1: 优化消息列表渲染 | 待开始 | | 4-5h | |
| Phase 5 | Task 5.2: 实现代码分割 | 待开始 | | 3-4h | |
| Phase 5 | Task 5.3: 图片资源优化 | 待开始 | | 3-4h | |
| Phase 6 | Task 6.1: 配置测试环境 | 待开始 | | 3h | |
| Phase 6 | Task 6.2: 编写单元测试 | 待开始 | | 8-10h | |
| Phase 6 | Task 6.3: 编写组件测试 | 待开始 | | 8-10h | |
| Phase 7 | Task 7.1: 编写架构决策记录 | 待开始 | | 4-5h | |
| Phase 7 | Task 7.2: 编写开发指南 | 待开始 | | 5-6h | |
| Phase 7 | Task 7.3: 组件文档化 | 待开始 | | 4-5h | |
| Phase 8 | Task 8.1: Bundle 分析和优化 | 待开始 | | 3-4h | |
| Phase 8 | Task 8.2: 性能监控 | 待开始 | | 4-5h | |
| Phase 8 | Task 8.3: 安全审计和加固 | 待开始 | | 3-4h | |

---

**总计预计时间：** 80-100 小时（约 2-3 个月，按每周 10 小时计算）

**建议执行顺序：**
1. 先完成 Phase 1（安全与基础设施）
2. 再完成 Phase 2-4（架构重构）
3. 最后进行 Phase 5-8（优化、测试、文档）

**风险提示：**
- 重构时注意保持功能完整，建议每次重构后进行完整测试
- 优先保证核心功能不受影响
- 建议采用小步快跑策略，每次只重构一个模块

