# KChat - ChatGPT 风格对话应用操作文档

---

## 文档版本

**版本**: 1.0  
**创建日期**: 2026-05-22  
**适用环境**: macOS (ARM/x86)

---

## 目录

1. [项目概述](#1-项目概述)
2. [环境要求](#2-环境要求)
3. [安装步骤](#3-安装步骤)
4. [启动方式](#4-启动方式)
5. [API 接口说明](#5-api-接口说明)
6. [前端使用说明](#6-前端使用说明)
7. [Ollama 配置](#7-ollama-配置)
8. [常见问题排查](#8-常见问题排查)
9. [技术支持](#9-技术支持)

---

## 1. 项目概述

KChat 是一个基于 Spring Boot + React 的 ChatGPT 风格对话应用，支持：
- 本地 Ollama 模型集成
- 流式输出（SSE）
- 短期记忆功能
- Markdown 渲染与代码高亮
- 多会话管理

### 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2.0 |
| 前端框架 | React | 19.1.0 |
| 构建工具 | Maven / Vite | 3.9+ / 6.3+ |
| 数据库 | H2 | 内存数据库 |
| AI 集成 | LangChain4j | 0.35.0 |
| 样式框架 | Tailwind CSS | 3.4.14 |

### 项目结构

```
KChat/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/example/app/
│   ├── src/main/resources/
│   └── pom.xml
├── frontend/                   # React 前端
│   ├── src/
│   └── package.json
└── 文档文件...
```

---

## 2. 环境要求

### 2.1 硬件要求
- CPU: 双核以上
- 内存: 8GB 以上（运行 Ollama 模型建议 16GB+）
- 存储: 至少 10GB 可用空间（用于 Ollama 模型）

### 2.2 软件要求

| 软件 | 版本 | 说明 |
|------|------|------|
| Java | 17 | OpenJDK 17 或 Oracle JDK 17 |
| Node.js | 20+ | 前端开发环境 |
| Ollama | 0.1.0+ | 本地 AI 模型服务 |

### 2.3 macOS 环境配置

#### ARM 架构（Apple Silicon）
```bash
# 安装 Homebrew（如未安装）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安装 Java 17
brew install openjdk@17
echo 'export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home' >> ~/.zshrc
source ~/.zshrc

# 安装 Node.js
brew install node@20

# 安装 Ollama
brew install ollama
```

#### x86 架构（Intel）
```bash
# 安装 Java 17
brew install openjdk@17
echo 'export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home' >> ~/.zshrc
source ~/.zshrc

# 安装 Node.js
brew install node@20

# 安装 Ollama
brew install ollama
```

---

## 3. 安装步骤

### 3.1 克隆项目
```bash
cd ~/Desktop
git clone <项目仓库地址>
cd KChat
```

### 3.2 安装后端依赖
```bash
cd backend
mvn clean install -DskipTests
```

### 3.3 安装前端依赖
```bash
cd ../frontend
npm install
```

### 3.4 下载 Ollama 模型
```bash
# 下载默认模型（llama3）
ollama pull llama3

# 可选：下载其他模型
ollama pull mistral
ollama pull phi3
```

---

## 4. 启动方式

### 4.1 启动 Ollama 服务
```bash
# 启动 Ollama（首次启动）
ollama serve

# 或在后台运行
ollama serve &
```

### 4.2 启动后端服务

**方式一：开发模式**
```bash
cd backend
mvn spring-boot:run
```

**方式二：打包运行**
```bash
cd backend
mvn clean package -DskipTests
java -jar target/kchat-backend-1.0.0.jar
```

**验证后端服务**
```bash
curl http://localhost:8080/api/conversations
# 预期输出: [] 或已有的对话列表
```

### 4.3 启动前端服务

**开发模式**
```bash
cd frontend
npm run dev
```

**生产构建**
```bash
cd frontend
npm run build
npm run preview
```

### 4.4 完整启动脚本

创建 `start.sh` 脚本：
```bash
#!/bin/bash
echo "启动 Ollama 服务..."
ollama serve &
sleep 2

echo "启动后端服务..."
cd backend
mvn spring-boot:run &
sleep 10

echo "启动前端服务..."
cd ../frontend
npm run dev
```

---

## 5. API 接口说明

### 5.1 基础路径

后端服务默认运行在: `http://localhost:8080/api`

### 5.2 接口列表

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 创建对话 | POST | `/conversations` | 创建新对话 |
| 获取对话列表 | GET | `/conversations` | 获取所有对话 |
| 获取对话详情 | GET | `/conversations/{id}` | 获取单个对话及消息 |
| 删除对话 | DELETE | `/conversations/{id}` | 删除对话 |
| 同步消息 | POST | `/chat` | 发送消息（同步模式） |
| 流式消息 | POST | `/chat/stream` | 发送消息（流式模式） |

### 5.3 接口详细说明

#### POST /api/conversations - 创建对话

**请求**:
```bash
curl -X POST http://localhost:8080/api/conversations \
  -H "Content-Type: application/json" \
  -d '{"title": "新对话"}'
```

**响应**:
```json
{
  "id": "uuid-string",
  "title": "新对话",
  "createdAt": "2026-05-22 10:30:00"
}
```

#### GET /api/conversations - 获取对话列表

**请求**:
```bash
curl http://localhost:8080/api/conversations
```

**响应**:
```json
[
  {
    "id": "uuid-string",
    "title": "对话标题",
    "createdAt": "2026-05-22 10:30:00"
  }
]
```

#### POST /api/chat - 同步消息

**请求**:
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "你好",
    "conversationId": "可选的对话ID"
  }'
```

**响应**:
```json
{
  "messageId": "uuid-string",
  "content": "您好！有什么我可以帮助您的吗？",
  "role": "assistant",
  "conversationId": "uuid-string"
}
```

#### POST /api/chat/stream - 流式消息

**请求**:
```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"message": "你好"}'
```

**响应**（SSE 事件流）:
```
event: message
data: {"content": "你"}

event: message
data: {"content": "好"}

event: done
data: {"messageId": "uuid-string"}
```

---

## 6. 前端使用说明

### 6.1 访问地址

前端默认运行在: `http://localhost:5173`

### 6.2 界面布局

```
┌──────────────┬──────────────────────────────────────┐
│   Sidebar   │           Main Chat Area            │
│             │                                      │
│ [+] 新对话  │  ┌────────────────────────────┐      │
│             │  │      Chat Messages         │      │
│ 对话列表    │  │                          │      │
│ • Conv 1    │  │  User: Hello             │      │
│ • Conv 2    │  │                          │      │
│ • Conv 3    │  │  Assistant: Hi there!    │      │
│             │  │                          │      │
│             │  └────────────────────────────┘      │
│             │                                      │
│             │  ┌────────────────────────────┐      │
│             │  │     Message Input          │      │
│             │  │  [_________________] [Send]│      │
│             │  └────────────────────────────┘      │
└──────────────┴──────────────────────────────────────┘
```

### 6.3 快捷键

| 快捷键 | 功能 |
|--------|------|
| Enter | 发送消息 |
| Shift + Enter | 插入换行 |
| Delete（右键会话） | 删除会话 |

### 6.4 功能说明

1. **创建对话**: 点击侧边栏「新对话」按钮
2. **切换对话**: 点击侧边栏中的会话项
3. **发送消息**: 在输入框输入内容，按 Enter 或点击发送按钮
4. **删除对话**: 右键点击会话项或点击会话项右侧的删除图标
5. **查看历史**: 滚动查看历史消息记录

---

## 7. Ollama 配置

### 7.1 模型管理

```bash
# 查看已安装的模型
ollama list

# 下载新模型
ollama pull <模型名称>

# 删除模型
ollama rm <模型名称>

# 查看模型详情
ollama show <模型名称>
```

### 7.2 常用模型

| 模型名称 | 大小 | 说明 |
|----------|------|------|
| llama3 | ~4.7GB | 默认推荐模型 |
| mistral | ~4.1GB | 轻量级模型 |
| phi3 | ~2.3GB | 超轻量模型 |
| llama2 | ~7.2GB | 经典模型 |

### 7.3 修改默认模型

编辑 `backend/src/main/resources/application.yml`:
```yaml
ollama:
  base-url: http://localhost:11434
  default-model: llama3  # 改为你想要的模型
```

---

## 8. 常见问题排查

### 8.1 后端启动失败

**问题**: Port 8080 was already in use

**解决方案**:
```bash
# 查找占用端口的进程
lsof -ti:8080

# 停止占用进程
lsof -ti:8080 | xargs kill -9
```

### 8.2 Ollama 连接失败

**问题**: Connection refused to localhost:11434

**解决方案**:
```bash
# 确保 Ollama 服务正在运行
ollama serve

# 检查 Ollama 状态
curl http://localhost:11434/api/tags
```

### 8.3 前端无法连接后端

**问题**: 跨域错误或请求失败

**解决方案**:
- 确保后端服务在 8080 端口运行
- 检查前端 `vite.config.ts` 代理配置
- 确保网络可以访问 localhost:8080

### 8.4 流式输出不显示

**问题**: 发送消息后没有响应

**解决方案**:
- 检查浏览器控制台是否有错误
- 确保后端 `ChatController` 中 `/chat/stream` 接口正常
- 检查 Ollama 服务状态

### 8.5 Maven 依赖无法解析

**问题**: Could not find artifact dev.langchain4j:langchain4j:jar:0.35.0

**解决方案**:
```bash
# 清理 Maven 缓存
mvn clean install -U

# 检查 pom.xml 中的依赖配置
cat backend/pom.xml | grep -A 5 "langchain4j"
```

### 8.6 内存不足

**问题**: Java OutOfMemoryError

**解决方案**:
```bash
# 增加 JVM 内存（Linux/macOS）
export JAVA_OPTS="-Xms512m -Xmx4096m"
mvn spring-boot:run

# 或直接设置环境变量
export NODE_OPTIONS=--max-old-space-size=4096
```

---

## 9. 技术支持

### 9.1 日志查看

**后端日志**:
```bash
# 查看实时日志
cd backend
mvn spring-boot:run

# 或查看日志文件（如果配置了日志输出）
tail -f logs/application.log
```

**前端日志**:
```bash
# 浏览器开发者工具（F12）
# Console 面板查看前端日志
```

### 9.2 调试模式

**后端调试**:
```bash
# 在 IDE 中设置断点
# 或启用调试模式运行
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

**前端调试**:
- 使用 Chrome DevTools（F12）
- 在 VS Code 中配置调试器

### 9.3 联系信息

- 项目文档: [backend-architecture.md](file:///Users/sunxiaokai/Desktop/KChat/backend-architecture.md)
- 任务清单: [task-list.md](file:///Users/sunxiaokai/Desktop/KChat/task-list.md)
- 前端文档: [frontend-architecture.md](file:///Users/sunxiaokai/Desktop/KChat/frontend-architecture.md)

---

## 附录

### A. 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端 API | 8080 | Spring Boot 默认端口 |
| 前端开发 | 5173 | Vite 默认端口 |
| Ollama | 11434 | Ollama 默认端口 |
| H2 Console | 8080/h2-console | 数据库控制台 |

### B. 环境变量

```bash
# Java 环境
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home

# 后端配置（可选）
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=llama3

# 前端配置（可选）
export VITE_API_URL=http://localhost:8080
```

### C. 启动检查清单

✅ Ollama 服务已启动  
✅ 后端服务已启动在 8080 端口  
✅ 前端服务已启动在 5173 端口  
✅ 测试 API 接口正常响应  
✅ 前端页面可以正常访问  
✅ 消息发送和流式输出正常

---

*文档版本: 1.0*  
*创建日期: 2026-05-22*  
*项目: KChat - ChatGPT 风格对话应用*
