# 001: 组件架构设计

## 状态
✅ 已接受

## 上下文
项目有一些组件重复和目录结构不一致的问题，需要统一的组件分类策略。

## 决策

### 组件目录结构
```
src/components/
├── layout/            # 页面布局组件
│   ├── Header.tsx
│   └── Sidebar/
├── features/         # 业务功能组件（按功能模块）
│   ├── ChatArea/
│   ├── InputArea/
│   ├── Memory/
│   └── Settings/
├── common/           # 通用业务组件
│   ├── ConfirmDialog.tsx
│   └── ErrorCard.tsx
├── ui/               # 基础 UI 组件
│   ├── Icon/
│   ├── Modal.tsx
│   └── ThemeToggle.tsx
└── [legacy]/         # 待迁移的旧组件（按需）
```

### 组件分类原则
- **layout/**: 页面框架组件，不包含业务逻辑
- **features/**: 按功能模块组织，包含业务逻辑
- **common/**: 可复用的业务组件
- **ui/**: 无业务逻辑的基础 UI 组件

## 理由
- 清晰的职责分离
- 便于组件复用和维护
- 符合现代前端项目最佳实践

## 后果
- 组件查找更直观
- 新开发者更容易理解项目结构
- 便于代码审查和测试
