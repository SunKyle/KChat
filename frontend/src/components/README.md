# Components 组件目录结构

本目录包含 KChat 前端项目的所有 React 组件，按照页面布局结构进行分类组织。

## 目录结构

```
components/
├── sidebar/                 # 左侧边栏区域
│   └── Sidebar/
│       ├── ConversationItem.tsx  # 对话项组件
│       └── index.tsx        # Sidebar主组件
├── chat/                    # 中间会话区域
│   ├── ChatArea/            # 聊天区域组件
│   │   ├── CodeBlock.tsx    # 代码块渲染组件
│   │   ├── MarkdownRenderer.tsx  # Markdown渲染组件
│   │   ├── MessageBubble.tsx     # 消息气泡组件
│   │   ├── TypingIndicator.tsx   # 输入指示器组件
│   │   └── index.tsx        # ChatArea主组件
│   ├── InputArea/           # 输入区域组件
│   │   └── index.tsx        # InputArea主组件
│   └── Header.tsx           # 顶部导航栏组件
├── note-todo/               # 右侧笔记待办区域
│   ├── DetailPreview.tsx    # 详情预览组件
│   ├── NoteForm.tsx         # 笔记表单
│   ├── NoteList.tsx         # 笔记列表
│   ├── NoteTodoPanel.tsx    # 笔记待办面板主组件
│   ├── TodoForm.tsx         # 待办表单
│   └── TodoList.tsx         # 待办列表
├── settings/                # 设置页面
│   ├── APIKeys.tsx          # API密钥设置
│   ├── ModelSettings.tsx    # 模型设置
│   ├── Preferences.tsx      # 偏好设置
│   ├── Privacy.tsx          # 隐私设置
│   ├── ProfileInfo.tsx      # 个人信息设置
│   └── UserSettings.tsx     # 设置面板主组件
└── common/                  # 公共组件（可复用）
    ├── Icon/                # 图标组件及Provider
    │   └── index.tsx
    ├── Drawer.tsx           # 抽屉组件
    ├── ErrorCard.tsx        # 错误提示卡片
    ├── Modal.tsx            # 模态框组件（含确认对话框功能）
    ├── Skeleton.tsx         # 骨架屏组件
    ├── ThemeToggle.tsx      # 主题切换组件
    └── ToastContainer.tsx   # Toast通知容器
```

## 布局结构分类说明

### 1. sidebar/ - 左侧边栏区域
负责展示对话列表和导航功能，包括：
- 对话列表项
- 新建对话按钮
- 笔记待办入口
- 设置入口

### 2. chat/ - 中间会话区域
核心聊天功能区域，包括：
- **Header**: 顶部导航栏，显示当前对话标题和模型选择
- **ChatArea**: 消息展示区域，包含消息气泡、代码块、Markdown渲染
- **InputArea**: 输入区域，支持文本输入、图片上传、代码输入

### 3. note-todo/ - 右侧笔记待办区域
悬浮卡片形式的笔记和待办管理功能：
- 笔记列表和详情
- 待办列表和状态管理
- 新建、编辑、删除操作
- 搜索和筛选功能

### 4. settings/ - 设置页面
用户配置和系统设置面板：
- 个人信息设置
- 偏好设置
- 隐私设置
- API密钥管理
- 模型设置
- 记忆体管理

### 5. common/ - 公共组件
不依赖业务逻辑的可复用组件：
- 图标系统（Icon）
- 模态框（Modal）
- 抽屉（Drawer）
- 骨架屏（Skeleton）
- 主题切换（ThemeToggle）
- Toast通知（ToastContainer）
- 错误卡片（ErrorCard）

## 文件命名规范

1. **组件文件名**: 使用 PascalCase（大驼峰命名）
   - 示例: `MessageBubble.tsx`, `UserSettings.tsx`
   
2. **目录命名**: 使用 kebab-case（短横线连接）
   - 示例: `note-todo/`, `chat-area/`

3. **索引文件**: 使用 `index.tsx` 作为目录入口

## 导入路径规范

- 使用相对路径导入同目录或子目录组件
- 优先使用绝对路径导入跨模块组件（如 `@/components/common/Modal`）

## 组件开发规范

1. **单一职责**: 每个组件只负责一个功能
2. **Props定义**: 明确定义组件的Props类型接口
3. **默认Props**: 为可选Props提供默认值
4. **类型安全**: 充分利用TypeScript类型系统
5. **可复用性**: 通用组件应设计为可复用的纯组件