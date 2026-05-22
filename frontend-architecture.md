# ChatGPT 风格对话应用 - 前端架构设计文档

---

## 文档说明

本文档详细描述了 ChatGPT 风格对话应用的前端架构设计，包括页面结构、组件划分、状态管理、数据流和文件目录等。

---

## 一、技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | React | 19.1.0 | UI 框架 |
| 构建工具 | Vite | 6.3.5 | 快速构建工具 |
| 语言 | TypeScript | 5.8.3 | 类型安全 |
| 样式 | Tailwind CSS | 3.4.14 | 原子化 CSS 框架 |
| Markdown | react-markdown | 9.0.1 | Markdown 渲染 |
| 代码高亮 | react-syntax-highlighter | 15.5.0 | 语法高亮 |
| 图标 | lucide-react | 0.453.0 | 图标库 |

---

## 二、页面结构

```
┌─────────────────────────────────────────────────────────────┐
│  KChat - ChatGPT Style Application                          │
├──────────────┬──────────────────────────────────────────────┤
│              │                                              │
│   Sidebar    │              Main Chat Area                  │
│   (280px)    │                                              │
│              │  ┌──────────────────────────────────────┐   │
│  [Logo]      │  │                                      │   │
│              │  │         Chat Messages                 │   │
│  [+ New]     │  │                                      │   │
│              │  │  ┌────────────────────────────────┐  │   │
│  ─────────   │  │  │   User: Hello                 │  │   │
│              │  │  └────────────────────────────────┘  │   │
│  Conv 1      │  │                                      │   │
│  Conv 2      │  │  ┌────────────────────────────────┐  │   │
│  Conv 3      │  │  │   Assistant: Hi there!        │  │   │
│  ...         │  │  │   (with Markdown support)     │  │   │
│              │  │  └────────────────────────────────┘  │   │
│              │  │                                      │   │
│              │  │  ┌────────────────────────────────┐  │   │
│              │  │  │   [Typing indicator...]       │  │   │
│              │  │  └────────────────────────────────┘  │   │
│              │  │                                      │   │
│              │  └──────────────────────────────────────┘   │
│              │                                              │
│              │  ┌──────────────────────────────────────┐   │
│              │  │  Message Input                       │   │
│              │  │  [________________________] [Send]   │   │
│              │  └──────────────────────────────────────┘   │
└──────────────┴──────────────────────────────────────────────┘
```

---

## 三、组件划分

### 3.1 组件列表

| 组件名称 | 文件路径 | 职责说明 | 状态 |
|----------|----------|----------|------|
| **Sidebar** | `components/Sidebar/index.tsx` | 侧边栏会话列表，包含新建按钮 | ✅ |
| **ConversationItem** | `components/Sidebar/ConversationItem.tsx` | 单个会话项，显示标题和时间 | ✅ |
| **ChatArea** | `components/ChatArea/index.tsx` | 中间聊天区域，消息列表容器 | ✅ |
| **MessageBubble** | `components/ChatArea/MessageBubble.tsx` | 单个消息气泡，支持 markdown | ✅ |
| **MarkdownRenderer** | `components/ChatArea/MarkdownRenderer.tsx` | Markdown 渲染组件 | ✅ |
| **CodeBlock** | `components/ChatArea/CodeBlock.tsx` | 代码块组件，带语法高亮 | ✅ |
| **TypingIndicator** | `components/ChatArea/TypingIndicator.tsx` | 流式输出时的打字指示器 | ✅ |
| **InputArea** | `components/InputArea/index.tsx` | 输入框区域，支持 Enter/Shift+Enter | ✅ |
| **App** | `App.tsx` | 主应用容器，布局管理 | ✅ |

### 3.2 组件层级

```
App
├── ChatProvider (Context)
├── Sidebar
│   └── ConversationItem (map)
└── Main Content
    ├── ChatArea
    │   ├── MessageBubble (map)
    │   ├── MarkdownRenderer
    │   │   └── CodeBlock
    │   └── TypingIndicator
    └── InputArea
```

---

## 四、状态管理方案

### 4.1 状态类型定义

```typescript
interface Conversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt?: string;
}

interface Message {
  id: string;
  conversationId: string;
  content: string;
  role: 'user' | 'assistant';
  timestamp: string;
}

interface StreamingState {
  isStreaming: boolean;
  currentContent: string;
  messageId: string | null;
}

interface ChatState {
  conversations: Conversation[];
  activeConversation: Conversation | null;
  messages: Message[];
  streamingState: StreamingState;
}
```

### 4.2 Context 设计

采用 React Context + useReducer 模式管理全局状态：

```typescript
interface ChatContextType {
  conversations: Conversation[];
  activeConversation: Conversation | null;
  messages: Message[];
  streamingState: StreamingState;
  setActiveConversation: (conv: Conversation) => void;
  createConversation: () => Promise<void>;
  deleteConversation: (id: string) => Promise<void>;
  sendMessage: (content: string) => Promise<void>;
  loadMessages: (conversationId: string) => Promise<void>;
}
```

### 4.3 Reducer Actions

```typescript
type ChatAction =
  | { type: 'SET_CONVERSATIONS'; payload: Conversation[] }
  | { type: 'SET_ACTIVE_CONVERSATION'; payload: Conversation | null }
  | { type: 'SET_MESSAGES'; payload: Message[] }
  | { type: 'ADD_MESSAGE'; payload: Message }
  | { type: 'UPDATE_MESSAGE'; payload: { id: string; content: string } }
  | { type: 'START_STREAMING' }
  | { type: 'UPDATE_STREAMING_CONTENT'; payload: string }
  | { type: 'END_STREAMING'; payload: string }
  | { type: 'ADD_CONVERSATION'; payload: Conversation }
  | { type: 'REMOVE_CONVERSATION'; payload: string };
```

---

## 五、数据流

### 5.1 数据流向图

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│    UI Layer     │      │  Context Layer  │      │   API Layer     │
├─────────────────┤      ├─────────────────┤      ├─────────────────┤
│                 │      │                 │      │                 │
│ Sidebar ───────►│      │ conversations  │◄────►│ GET /conversations│
│ (选择会话)      │      │ activeConv     │      │                 │
│                 │      │                 │      │                 │
│ ChatArea ◄──────│      │ messages       │◄────►│ GET /conversation/│
│ (显示消息)      │      │                 │      │   {id}           │
│                 │      │                 │      │                 │
│ InputArea ──────►│      │ sendMessage()  │─────►│ POST /api/chat   │
│ (发送消息)       │      │                 │      │                 │
│                 │      │                 │      │                 │
│ TypingIndicator◄─│      │ streamingState │◄────►│ POST /api/chat/stream│
│ (流式输出)       │      │                 │      │ (SSE)           │
│                 │      │                 │      │                 │
└─────────────────┘      └─────────────────┘      └─────────────────┘
```

### 5.2 关键业务流程

#### 初始化流程
1. 页面加载 → ChatProvider 初始化
2. 调用 `GET /api/conversations` 获取会话列表
3. 设置第一个会话为激活状态
4. 调用 `GET /api/conversations/{id}` 获取消息列表

#### 发送消息流程
1. 用户在 InputArea 输入消息
2. 按 Enter 或点击发送按钮
3. 创建用户消息（role: user）并添加到消息列表
4. 调用 `POST /api/chat/stream` 建立 SSE 连接
5. 实时接收流式数据，更新 AI 消息内容
6. 显示打字指示器
7. 接收完成后隐藏指示器

#### 会话管理流程
1. 创建会话 → `POST /api/conversations`
2. 选择会话 → 更新 activeConversation → 加载对应消息
3. 删除会话 → `DELETE /api/conversations/{id}` → 更新列表

---

## 六、API 接口对接

### 6.1 后端 API 列表

| 接口 | HTTP 方法 | 路径 | 说明 |
|------|----------|------|------|
| 创建对话 | POST | `/api/conversations` | 创建新对话 |
| 获取对话列表 | GET | `/api/conversations` | 获取所有对话 |
| 获取对话详情 | GET | `/api/conversations/{id}` | 获取单个对话及消息 |
| 删除对话 | DELETE | `/api/conversations/{id}` | 删除对话 |
| 发送消息（同步） | POST | `/api/chat` | 发送消息并等待响应 |
| 发送消息（流式） | POST | `/api/chat/stream` | SSE 流式响应 |

### 6.2 API 封装

```typescript
// utils/api.ts
const BASE_URL = '/api';

export const api = {
  conversations: {
    list: () => fetch(`${BASE_URL}/conversations`).then(r => r.json()),
    get: (id: string) => fetch(`${BASE_URL}/conversations/${id}`).then(r => r.json()),
    create: (title?: string) => fetch(`${BASE_URL}/conversations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(title ? { title } : {})
    }).then(r => r.json()),
    delete: (id: string) => fetch(`${BASE_URL}/conversations/${id}`, { method: 'DELETE' })
  },
  chat: {
    send: (request) => fetch(`${BASE_URL}/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    }).then(r => r.json()),
    stream: (request, onMessage, onComplete, onError) => {
      // SSE 流式处理实现
    }
  }
};
```

---

## 七、文件目录结构

```
frontend/                              # Vite React TypeScript 项目根目录
├── src/
│   ├── components/                    # 组件目录
│   │   ├── Sidebar/                   # 侧边栏组件
│   │   │   ├── index.tsx             # Sidebar 主组件
│   │   │   └── ConversationItem.tsx  # 会话项组件
│   │   ├── ChatArea/                  # 聊天区域组件
│   │   │   ├── index.tsx             # ChatArea 主组件
│   │   │   ├── MessageBubble.tsx     # 消息气泡
│   │   │   ├── MarkdownRenderer.tsx  # Markdown 渲染
│   │   │   ├── CodeBlock.tsx         # 代码块
│   │   │   └── TypingIndicator.tsx   # 打字指示器
│   │   └── InputArea/                 # 输入区域组件
│   │       └── index.tsx             # InputArea 主组件
│   ├── context/                       # Context 状态管理
│   │   └── ChatContext.tsx           # 聊天全局状态
│   ├── types/                         # TypeScript 类型定义
│   │   └── index.ts                  # 全局类型
│   ├── utils/                         # 工具函数
│   │   └── api.ts                    # API 封装
│   ├── App.tsx                        # 主应用组件
│   ├── main.tsx                       # 入口文件
│   ├── index.css                      # 全局样式
│   └── react-syntax-highlighter.d.ts # 类型声明
├── public/                            # 静态资源
├── index.html                         # HTML 模板
├── package.json                       # 依赖配置
├── tsconfig.json                      # TypeScript 配置
├── vite.config.ts                     # Vite 配置（含 API 代理）
├── tailwind.config.js                 # Tailwind 配置
├── postcss.config.js                  # PostCSS 配置
└── eslint.config.js                  # ESLint 配置
```

---

## 八、交互设计

### 8.1 键盘快捷键

| 快捷键 | 功能 |
|--------|------|
| Enter | 发送消息 |
| Shift + Enter | 插入换行 |

### 8.2 鼠标交互

| 操作 | 功能 |
|------|------|
| 点击会话项 | 切换到该会话 |
| 右键会话项 | 删除该会话 |
| 点击新建按钮 | 创建新对话 |

### 8.3 视觉反馈

| 状态 | 视觉反馈 |
|------|----------|
| 发送中 | 显示旋转加载动画 |
| 流式输出中 | 显示打字指示器 |
| 消息加载中 | 显示骨架屏 |

---

## 九、样式设计

### 9.1 配色方案

```css
/* 主色调 */
primary-500: #0ea5e9    /* 主按钮颜色 */
primary-600: #0284c7    /* 按钮悬停 */

/* 背景色 */
bg-slate-900: #0f172a   /* 主背景 */
bg-slate-800: #1e293b   /* 侧边栏背景 */
bg-slate-700: #334155   /* 消息气泡背景 */

/* 文字色 */
text-slate-100: #f1f5f9 /* 主要文字 */
text-slate-400: #94a3b8 /* 次要文字 */
text-slate-500: #64748b /* 占位符文字 */
```

### 9.2 布局尺寸

| 元素 | 尺寸 |
|------|------|
| 侧边栏宽度 | 280px |
| 头像大小 | 40px |
| 头像圆角 | 50% (圆形) |
| 消息气泡圆角 | 12px (24px) |
| 输入框最小高度 | 60px |
| 输入框最大高度 | 200px |

---

## 十、Vite 代理配置

```typescript
// vite.config.ts
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

---

## 十一、扩展规划

### 11.1 V2 功能（可选）

- [ ] 文件上传支持
- [ ] 语音输入
- [ ] 消息复制功能
- [ ] 对话导出

### 11.2 V3 功能（可选）

- [ ] 多模型切换
- [ ] 主题切换（亮色/暗色）
- [ ] 快捷命令
- [ ] 预设提示词

### 11.3 V4 功能（可选）

- [ ] 历史记录搜索
- [ ] 消息编辑
- [ ] 会话分享
- [ ] 多语言支持

---

## 十二、启动方式

### 12.1 开发环境

```bash
cd frontend
npm install
npm run dev
```

### 12.2 生产构建

```bash
cd frontend
npm run build
npm run preview
```

---

*文档版本: 1.0*  
*创建日期: 2026-05-22*  
*适用项目: ChatGPT 风格对话应用前端*
