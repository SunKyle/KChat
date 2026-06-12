# 项目架构优化计划

## 1. 问题分析总结

根据之前的架构分析，当前项目存在以下问题：

### 1.1 无用目录/文件
- `NoteTodoDrawer.tsx` - 已被 `NoteTodoPanel.tsx` 替代

### 1.2 职责不清目录
- `components/ui/` 与 `components/common/` 职责重叠

### 1.3 重复模块
- `ConfirmDialog` 与 `Modal` 功能重复

### 1.4 超大文件/组件
- `NoteTodoPanel.tsx` (~1100行) - 需要拆分

---

## 2. 优化计划

### 2.1 阶段一：删除无用文件

| 序号 | 任务 | 文件 | 说明 |
|------|------|------|------|
| 1 | 删除抽屉组件 | `src/components/NoteTodo/NoteTodoDrawer.tsx` | 已被NoteTodoPanel替代 |

### 2.2 阶段二：拆分超大组件 NoteTodoPanel

将 `NoteTodoPanel.tsx` 拆分为以下子组件：

| 序号 | 任务 | 新文件 | 职责 |
|------|------|--------|------|
| 2 | 创建笔记列表组件 | `src/components/NoteTodo/NoteList.tsx` | 笔记列表展示 |
| 3 | 创建待办列表组件 | `src/components/NoteTodo/TodoList.tsx` | 待办列表展示 |
| 4 | 创建笔记表单组件 | `src/components/NoteTodo/NoteForm.tsx` | 新建/编辑笔记表单 |
| 5 | 创建待办表单组件 | `src/components/NoteTodo/TodoForm.tsx` | 新建/编辑待办表单 |
| 6 | 创建详情预览组件 | `src/components/NoteTodo/DetailPreview.tsx` | 选中项详情展示 |
| 7 | 重构主面板 | `src/components/NoteTodo/NoteTodoPanel.tsx` | 整合子组件 |

### 2.3 阶段三：合并重复组件

| 序号 | 任务 | 文件 | 说明 |
|------|------|------|------|
| 8 | 整合Modal组件 | `src/components/ui/Modal.tsx` | 统一模态框组件 |
| 9 | 删除ConfirmDialog | `src/components/common/ConfirmDialog.tsx` | 功能并入Modal |

### 2.4 阶段四：目录结构优化

| 序号 | 任务 | 说明 |
|------|------|------|
| 10 | 合并ui到common | 将`components/ui/`内容迁移到`components/common/` |

---

## 3. 依赖与风险

### 3.1 依赖关系
- `NoteTodoPanel.tsx` 依赖 `useToast`, `useLocalStorage`, `useDebounce` hooks
- 删除 `ConfirmDialog` 需要更新所有引用

### 3.2 风险处理
- **风险1**: 拆分组件可能引入新的props传递复杂度
  - **应对**: 使用Context或自定义hook管理共享状态
- **风险2**: 删除文件可能导致引用错误
  - **应对**: 先搜索所有引用，再进行删除

---

## 4. 执行步骤

```
Phase 1: 删除无用文件
  └─ 删除 NoteTodoDrawer.tsx

Phase 2: 拆分 NoteTodoPanel
  ├─ 创建 NoteList.tsx
  ├─ 创建 TodoList.tsx
  ├─ 创建 NoteForm.tsx
  ├─ 创建 TodoForm.tsx
  ├─ 创建 DetailPreview.tsx
  └─ 重构 NoteTodoPanel.tsx

Phase 3: 合并Modal组件
  ├─ 增强Modal组件功能
  ├─ 更新App.tsx使用Modal
  └─ 删除ConfirmDialog.tsx

Phase 4: 目录优化
  └─ 合并ui到common
```

---

## 5. 预期成果

- 组件职责单一，便于维护
- 文件大小控制在合理范围内（<500行）
- 目录结构清晰，职责明确
- 消除重复代码