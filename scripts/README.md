# KChat 脚本目录

本目录包含 KChat 项目的管理脚本，用于启动、停止和运维整个项目（前端 + 后端 + Cognee 记忆服务）。

## 快速开始

```bash
# 启动全部服务（不含 Cognee）
./scripts/start.sh

# 停止全部服务
./scripts/stop.sh
```

---

## 脚本一览

| 脚本 | 用途 | 是否常用 |
|------|------|---------|
| `start.sh` | 一键启动所有服务 | ✅ 每次开发 |
| `stop.sh` | 一键停止所有服务 | ✅ 每次开发 |
| `start-cognee.sh` | 仅启动 Cognee 记忆服务 | 按需 |
| `cognee-api-server.py` | Cognee REST API 服务器 | 被 start.sh 调用 |
| `cognee-graph.sh` | 导出知识图谱 HTML 可视化 | 按需 |

---

## 详细说明

### `start.sh` — 一键启动

启动后端 (Spring Boot `:8080`)、前端 (Vite `:5173`)，可选附带 Cognee 记忆服务 (`:8000`)。

```bash
# 仅启动后端 + 前端（最常用）
./scripts/start.sh

# 同时启动 Cognee（使用 Ollama 本地模型）
./scripts/start.sh --with-cognee

# 同时启动 Cognee（使用 DeepSeek API，更快不抢资源）
DEEPSEEK_API_KEY=sk-xxx ./scripts/start.sh --deepseek

# 仅启动 Cognee 服务（用于调试）
./scripts/start.sh --cognee-only

# 查看帮助
./scripts/start.sh --help
```

**启动流程：**

```
start.sh
  ├── ① 检查依赖（Java、Maven、Node.js、Python）
  ├── ② 停止旧服务（8080 + 5173 + 8000）
  ├── ③ 启动后端 → 等待 /api/models 就绪
  ├── ④ 启动 Cognee（可选）→ 等待 /health 就绪
  └── ⑤ 启动前端 → 等待首页就绪
```

启动完成后会显示每个服务的地址和 PID。日志文件位于项目 `logs/` 目录下。

---

### `stop.sh` — 一键停止

停止后端 (`:8080`)、前端 (`:5173`)、Cognee (`:8000`)。会逐个检测端口是否在运行，只停止实际在运行的服务。

```bash
./scripts/stop.sh
```

---

### `start-cognee.sh` — 启动 Cognee

独立启动 Cognee 记忆服务（FastAPI `:8000`），可被 `start.sh` 内部调用，也可单独使用：

```bash
# 使用 Ollama（默认）
./scripts/start-cognee.sh

# 使用 DeepSeek
LLM_API_KEY=sk-xxx LLM_MODEL=deepseek/deepseek-v4-flash ./scripts/start-cognee.sh
```

---

### `cognee-api-server.py` — Cognee REST API 服务器

将 cognee 的 Python API 包装为 HTTP 端点，供 Java 后端调用。由 `start-cognee.sh` 或 `start.sh` 自动启动。

**端点：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/add` | 将对话内容索引到知识图谱 |
| POST | `/search` | 搜索相关记忆 |
| GET | `/health` | 健康检查 |

不需要手动运行此文件。

---

### `cognee-graph.sh` — 知识图谱可视化

导出 cognee 数据库中已索引的内容，生成为**交互式 HTML 知识图谱**，浏览器打开即可查看实体关系网络图。

```bash
# 默认输出到 ~/cognee-graph.html
DEEPSEEK_API_KEY=sk-xxx ./scripts/cognee-graph.sh

# 指定输出路径
DEEPSEEK_API_KEY=sk-xxx ./scripts/cognee-graph.sh ~/my-graph.html

# 查看帮助
./scripts/cognee-graph.sh --help
```

> **注意**: 脚本会自动停止正在运行的 cognee 服务（释放数据库锁），生成完成后提醒你重新启动。

---

## 环境变量

以下环境变量会影响脚本行为：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DEEPSEEK_API_KEY` | — | DeepSeek API 密钥，使用 `--deepseek` 时必需 |
| `LLM_API_KEY` | `ollama` | cognee 使用的 LLM API 密钥 |
| `LLM_MODEL` | `ollama/llama3` | cognee 使用的 LLM 模型名 |
| `COGNEE_PORT` | `8000` | Cognee 服务端口 |

## 日志文件

日志统一存放在项目 `logs/` 目录下。

| 服务 | 日志路径 |
|------|---------|
| 后端 | `logs/backend.log` |
| 前端 | `logs/frontend.log` |
| Cognee | `logs/cognee.log` |

```bash
tail -f logs/backend.log   # 实时查看后端日志
```

## 端口占用

| 端口 | 服务 | 说明 |
|------|------|------|
| 8080 | Spring Boot | KChat 后端 API |
| 5173 | Vite | KChat 前端开发服务器 |
| 8000 | FastAPI | Cognee 记忆服务 |
