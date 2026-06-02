# KChat 前端优化任务清单

---

## 文档说明

本文档基于 [frontend-refactor-plan.md](../docs/frontend-refactor-plan.md) 创建，包含具体的优化任务清单，用于跟踪重构进度。

**状态说明**:
- ✅ **已完成**
- 🔄 **进行中**
- ⏳ **待开始**
- ⚠️ **有风险**

---

## 目录

1. [API 层重构](#1-api-层重构)
2. [工具函数抽离](#2-工具函数抽离)
3. [组件结构调整](#3-组件结构调整)
4. [状态管理重构](#4-状态管理重构)
5. [解耦页面通信](#5-解耦页面通信)
6. [组件复用率提升](#6-组件复用率提升)

---

## 1. API 层重构

### 1.1 创建统一请求客户端

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 创建 `api/client.ts` 统一请求封装 | ✅ | 高 | `src/api/client.ts` |
| 添加超时控制（30s） | ✅ | 高 | `src/api/client.ts` |
| 添加统一错误处理 | ✅ | 高 | `src/api/client.ts` |

### 1.2 拆分 API 模块

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 拆分聊天 API → `api/chat.ts` | ✅ | 高 | `src/api/chat.ts` |
| 迁移记忆 API → `api/memory.ts` | ✅ | 高 | `src/api/memory.ts` |
| 拆分模型 API → `api/models.ts` | ✅ | 高 | `src/api/models.ts` |
| 创建 `api/index.ts` 统一导出 | ✅ | 高 | `src/api/index.ts` |

### 1.3 修复错误处理

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 统一错误处理（通过新 API 层） | ✅ | 高 | `src/api/client.ts` |

### 1.4 清理未使用接口

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 标记/删除未使用接口 | ⏳ | 低 | `src/utils/api.ts`, `src/utils/memoryApi.ts` |

---

## 2. 工具函数抽离

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 创建 `hooks/useLocalStorage.ts` | ✅ | 高 | `src/hooks/useLocalStorage.ts` |
| 创建 `hooks/useStreaming.ts` | ✅ | 高 | `src/hooks/useStreaming.ts` |
| 创建 `utils/storage.ts` 存储工具 | ⏳ | 中 | `src/utils/storage.ts` |
| 创建 `utils/format.ts` 格式化工具 | ⏳ | 中 | `src/utils/format.ts` |

---

## 3. 组件结构调整

### 3.1 移动布局组件

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 创建 `components/layout/Header.tsx` | ✅ | 高 | `src/components/layout/Header.tsx` |
| 创建 `components/layout/Sidebar/index.tsx` | ✅ | 高 | `src/components/layout/Sidebar/index.tsx` |
| 创建 `components/layout/Sidebar/ConversationItem.tsx` | ✅ | 高 | `src/components/layout/Sidebar/ConversationItem.tsx` |

### 3.2 删除重复文件

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 删除重复的 `MessageBubble.tsx` | ✅ | 高 | `src/components/MessageBubble.tsx` |

---

## 4. 状态管理重构

### 4.1 创建 Context

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 创建 `ModalContext.tsx` 弹窗状态管理 | ✅ | 高 | `src/context/ModalContext.tsx` |
| 更新 `App.tsx` 使用 `ModalProvider` | ✅ | 高 | `src/App.tsx` |

---

## 5. 解耦页面通信

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 移除 `App.tsx` 中的 `window.addEventListener` | ✅ | 高 | `src/App.tsx` |
| 使用 `ModalContext` 管理弹窗显示 | ✅ | 高 | `src/App.tsx` |
| 在 `Sidebar` 中使用 `useModal` Hook | ✅ | 高 | `src/components/layout/Sidebar/index.tsx` |

---

## 6. 组件复用率提升

### 6.1 创建通用 UI 组件

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 创建 `Modal.tsx` 统一弹窗容器 | ✅ | 高 | `src/components/ui/Modal.tsx` |
| 使用 `Modal` 重构模型设置弹窗 | ✅ | 高 | `src/App.tsx` |
| 使用 `Modal` 重构记忆管理弹窗 | ✅ | 高 | `src/App.tsx` |

---

## 7. 更新配置文件

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 创建 `.env` 环境变量配置 | ✅ | 高 | `.env` |

---

## 任务进度统计

### 按优先级统计

| 优先级 | 总任务数 | 已完成 | 进行中 | 待开始 |
|--------|----------|--------|--------|--------|
| 高 | 18 | 18 | 0 | 0 |
| 中 | 2 | 0 | 0 | 2 |
| 低 | 1 | 0 | 0 | 1 |
| **总计** | **21** | **18** | **0** | **3** |

### 按模块统计

| 模块 | 总任务数 | 已完成 | 进行中 | 待开始 |
|------|----------|--------|--------|--------|
| API 层重构 | 7 | 6 | 0 | 1 |
| 工具函数抽离 | 4 | 2 | 0 | 2 |
| 组件结构调整 | 4 | 4 | 0 | 0 |
| 状态管理重构 | 2 | 2 | 0 | 0 |
| 解耦页面通信 | 3 | 3 | 0 | 0 |
| 组件复用率提升 | 3 | 3 | 0 | 0 |
| **总计** | **23** | **20** | **0** | **3** |

---

## 任务完成记录

| 日期 | 完成任务 | 执行者 | 备注 |
|------|----------|--------|------|
| 2026-06-02 | 创建统一 API 客户端 `api/client.ts` | Trae | 含超时控制和错误处理 |
| 2026-06-02 | 创建 API 模块文件 | Trae | chat.ts, models.ts, memory.ts |
| 2026-06-02 | 创建自定义 Hooks | Trae | useLocalStorage.ts, useStreaming.ts |
| 2026-06-02 | 创建 ModalContext | Trae | 统一弹窗状态管理 |
| 2026-06-02 | 创建 Modal 组件 | Trae | 统一弹窗容器 |
| 2026-06-02 | 移动布局组件到新目录 | Trae | Header, Sidebar |
| 2026-06-02 | 删除重复文件 | Trae | MessageBubble.tsx |
| 2026-06-02 | 解耦页面通信 | Trae | 移除全局事件监听 |
| 2026-06-02 | 更新 App.tsx | Trae | 使用新架构 |
| 2026-06-02 | 修复 TypeScript 错误 | Trae | 构建成功 |

---

**文档版本**: v1.1  
**创建日期**: 2026-06-02  
**适用项目**: KChat Frontend