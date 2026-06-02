# KChat UI 设计系统优化任务清单

---

## 文档说明

本文档基于 UI 体系分析报告创建，包含具体的设计系统优化任务清单，用于跟踪进度。

**状态说明**:
- ✅ **已完成**
- 🔄 **进行中**
- ⏳ **待开始**
- ⚠️ **有风险**

---

## 目录

1. [创建 Design Token 系统](#1-创建-design-token-系统)
2. [颜色体系优化](#2-颜色体系优化)
3. [字体体系优化](#3-字体体系优化)
4. [间距体系优化](#4-间距体系优化)
5. [圆角体系优化](#5-圆角体系优化)
6. [阴影体系优化](#6-阴影体系优化)
7. [图标体系优化](#7-图标体系优化)
8. [代码规范检查](#8-代码规范检查)

---

## 1. 创建 Design Token 系统

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 创建 `src/theme/tokens.ts` 集中管理设计 Token | ✅ | 高 | `src/theme/tokens.ts` |
| 更新 `tailwind.config.js` 扩展自定义主题 | ✅ | 高 | `tailwind.config.js` |
| 更新 `src/index.css` 引入 Token 变量 | ✅ | 高 | `src/index.css` |

---

## 2. 颜色体系优化

### 2.1 清理硬编码颜色

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 替换 `#E5E7EB` → `text-slate-200` | ✅ | 高 | 全局替换 |
| 替换 `#0EA5E9` → `bg-primary-500` | ✅ | 高 | 全局替换 |
| 替换 `#0F172A` → `bg-dark-900` | ✅ | 高 | 全局替换 |
| 替换 `#111827` → `bg-dark-800` | ✅ | 高 | 全局替换 |
| 替换 `#1E293B` → `bg-dark-700` | ✅ | 高 | 全局替换 |
| 替换 `#334155` → `bg-dark-600` | ✅ | 中 | 全局替换 |
| 替换 `rgba(255,255,255,0.05)` → `bg-white/5` | ✅ | 中 | `src/index.css` |

### 2.2 文件级优化

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 优化 `App.tsx` 颜色 | ✅ | 高 | `src/App.tsx` |
| 优化 `InputArea/index.tsx` 颜色 | ✅ | 高 | `src/components/InputArea/index.tsx` |
| 优化 `Sidebar/index.tsx` 颜色 | ⏳ | 高 | `src/components/Sidebar/index.tsx` |
| 优化 `layout/Sidebar/index.tsx` 颜色 | ✅ | 高 | `src/components/layout/Sidebar/index.tsx` |
| 优化 `layout/Header.tsx` 颜色 | ✅ | 高 | `src/components/layout/Header.tsx` |
| 优化 `ChatArea/index.tsx` 颜色 | ✅ | 高 | `src/components/ChatArea/index.tsx` |
| 优化 `ChatArea/MessageBubble.tsx` 颜色 | ✅ | 高 | `src/components/ChatArea/MessageBubble.tsx` |
| 优化 `Settings/ModelSettings.tsx` 颜色 | ✅ | 中 | `src/components/Settings/ModelSettings.tsx` |
| 优化 `Memory/MemoryPanel.tsx` 颜色 | ⏳ | 中 | `src/components/Memory/MemoryPanel.tsx` |
| 优化 `index.css` 颜色 | ✅ | 中 | `src/index.css` |

---

## 3. 字体体系优化

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 添加 `text-[10px]` → `text-[10px]`（Tailwind 默认支持） | ✅ | 低 | - |
| 添加 `text-[11px]` → `text-[11px]`（Tailwind 默认支持） | ✅ | 低 | - |
| 统一图标大小规范 | ⏳ | 中 | 全局 |

---

## 4. 间距体系优化

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 定义 `max-w-content` = 800px | ✅ | 中 | `tailwind.config.js` |
| 定义 `max-w-modal` = 600px | ✅ | 中 | `tailwind.config.js` |
| 替换 `max-w-[800px]` → `max-w-content` | ✅ | 中 | 全局替换 |
| 替换 `max-h-[60vh/70vh]` → 统一命名 | ⏳ | 低 | `src/App.tsx` |

---

## 5. 圆角体系优化

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 定义自定义圆角 Token | ✅ | 中 | `tailwind.config.js` |
| 清理 `index.css` 中硬编码圆角 | ✅ | 中 | `src/index.css` |

---

## 6. 阴影体系优化

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 定义自定义阴影 Token | ⏳ | 低 | `tailwind.config.js` |
| 统一颜色阴影命名 | ✅ | 低 | 全局（替换为 `shadow-primary-500/30`） |

---

## 7. 图标体系优化

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 创建统一图标组件 `Icon.tsx` | ⏳ | 中 | `src/components/ui/Icon.tsx` |
| 统一图标大小和颜色 | ⏳ | 低 | 全局 |

---

## 8. 代码规范检查

| 任务 | 状态 | 优先级 | 相关文件 |
|------|------|--------|----------|
| 运行 ESLint 检查样式相关规则 | ⏳ | 中 | 项目根目录 |
| 运行构建验证 | ✅ | 高 | 项目根目录 |

---

## 任务进度统计

### 按优先级统计

| 优先级 | 总任务数 | 已完成 | 进行中 | 待开始 |
|--------|----------|--------|--------|--------|
| 高 | 13 | 12 | 0 | 1 |
| 中 | 12 | 7 | 0 | 5 |
| 低 | 4 | 3 | 0 | 1 |
| **总计** | **29** | **22** | **0** | **7** |

### 按模块统计

| 模块 | 总任务数 | 已完成 | 进行中 | 待开始 |
|------|----------|--------|--------|--------|
| Design Token 系统 | 3 | 3 | 0 | 0 |
| 颜色体系优化 | 15 | 13 | 0 | 2 |
| 字体体系优化 | 3 | 2 | 0 | 1 |
| 间距体系优化 | 4 | 3 | 0 | 1 |
| 圆角体系优化 | 2 | 2 | 0 | 0 |
| 阴影体系优化 | 2 | 1 | 0 | 1 |
| 图标体系优化 | 2 | 0 | 0 | 2 |
| 代码规范检查 | 2 | 1 | 0 | 1 |
| **总计** | **33** | **24** | **0** | **9** |

---

## 任务完成记录

| 日期 | 完成任务 | 执行者 | 备注 |
|------|----------|--------|------|
| 2026-06-02 | 创建 Design Token 系统 | Trae | 创建 `src/theme/tokens.ts` |
| 2026-06-02 | 更新 Tailwind 配置 | Trae | 添加 `max-w-content`、`max-w-modal`、`rounded-10` |
| 2026-06-02 | 更新 index.css | Trae | 引入 CSS 变量替换硬编码颜色 |
| 2026-06-02 | 优化 App.tsx 颜色 | Trae | `bg-[#0F172A]` → `bg-dark-900` |
| 2026-06-02 | 优化 InputArea 颜色 | Trae | 颜色和间距优化 |
| 2026-06-02 | 优化 layout/Sidebar 颜色 | Trae | 颜色优化 |
| 2026-06-02 | 优化 layout/Header 颜色 | Trae | 颜色优化 |
| 2026-06-02 | 优化 ChatArea 颜色 | Trae | 颜色和按钮样式优化 |
| 2026-06-02 | 优化 MessageBubble 颜色 | Trae | 颜色优化 |
| 2026-06-02 | 优化 ModelSettings 颜色 | Trae | 颜色优化 |
| 2026-06-02 | 构建验证 | Trae | ✅ 构建成功 |

---

**文档版本**: v1.1  
**创建日期**: 2026-06-02  
**适用项目**: KChat Frontend