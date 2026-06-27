# 19. 部署与运维

> 生成日期：2026-06-27 | 分支：main

---

## 一、环境概览

| 环境 | 后端端口 | 前端端口 | 数据库 | 用途 |
|------|---------|---------|--------|------|
| 本地开发 | 8080 | 5173 (Vite) | MySQL + H2 降级 | 日常开发调试 |
| 测试 | — | — | — | 当前未配置独立环境 |
| 生产 | — | — | — | 当前未配置 |

**当前状态：** 项目处于开发阶段，仅有本地开发环境。尚无 Docker 化、CI/CD 流水线和生产部署配置。

---

## 二、本地开发环境

### 2.1 前置依赖

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | `JAVA_HOME` 指向 JDK 17 |
| Maven | 3.6+ | 或使用 `mvnw` wrapper |
| Node.js | 18+ | npm 包管理器 |
| MySQL | 8.0+ | 数据库 `kchatdb`，用户 `admin` |
| Redis | 6.0+ | 端口 6379 |
| Ollama | (可选) | 本地模型服务，端口 11434 |

### 2.2 数据库初始化

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS kchatdb
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 用户配置 (application.yml)
-- spring.datasource.username=admin
-- spring.datasource.password=sxk1997sxk
```

**JPA 策略：** `ddl-auto: update` —— 应用启动时自动创建/更新表结构。不适用于生产环境。

**手动迁移脚本：**
- `backend/src/main/resources/db/migration/V2__create_user_profile_tables.sql`
- `backend/src/main/resources/db/migration/V3__create_notes_todos_tables.sql`
- `backend/src/main/resources/schema/prompt_templates.sql`

注意：项目未集成 Flyway/Liquibase，迁移脚本需手动执行。

### 2.3 环境变量

**前端 (`.env` / `.env.example`)：**
```bash
VITE_API_URL=http://localhost:8080/api
```

**后端 (无 `.env`，所有配置在 `application.yml`)：**
- 数据库连接信息硬编码于 `application.yml`
- 无环境变量覆盖机制
- 敏感信息（数据库密码、Bing API Key）以明文存储在配置文件中

### 2.4 快速启动

**一键启动：**
```bash
bash scripts/start.sh
```

脚本执行流程：
```
1. 停止现有服务 (kill :8080 :5173)
2. 启动后端 (mvn spring-boot:run → /tmp/kchat-backend.log)
3. 等待后端就绪 (轮询 :8080 最多 30s)
4. 启动前端 (npm run dev → /tmp/kchat-frontend.log)
5. 打印访问地址
```

**分步启动：**

```bash
# 终端 1: 后端
cd backend
mvn spring-boot:run

# 终端 2: 前端
cd frontend
npm run dev
```

**停止服务：**
```bash
bash scripts/stop.sh
# 或手动: lsof -ti:8080 | xargs kill; lsof -ti:5173 | xargs kill
```

### 2.5 Vite 代理配置

开发模式下前端请求通过 Vite 代理转发到后端：

```typescript
// frontend/vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

### 2.6 构建

```bash
# 后端
cd backend
mvn clean package -DskipTests
# 产物: target/kchat-backend-1.0.0.jar

# 前端
cd frontend
npm run build
# 产物: dist/
```

---

## 三、配置文件详解

### 3.1 `application.yml` 关键配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/kchatdb?...    # MySQL连接
    hikari:
      maximum-pool-size: 10                          # 连接池最大连接数
      minimum-idle: 5                                # 最小空闲连接
      connection-timeout: 30000                      # 获取连接超时 30s
  jpa:
    hibernate.ddl-auto: update                       # 开发环境自动建表
    show-sql: true                                   # 打印 SQL
  data.redis:
    host: localhost
    port: 6379
    timeout: 60000ms                                 # Redis 超时 60s
    enabled: true

ollama:
  base-url: http://localhost:11434
  default-model: llama3

resilience4j:
  retry.ollamaRetry:
    max-attempts: 3
    wait-duration: 2s
  circuitbreaker.ollamaCB:
    sliding-window-size: 10
    failure-rate-threshold: 50
    wait-duration-in-open-state: 10s

memory:
  long-term:
    similarity-threshold: 0.3                        # 余弦相似度阈值
    min-importance: 3
    vector-dimension: 384
  extractor:
    message-threshold: 5                             # 记忆提取触发消息数
    min-confidence: 50                               # 最低置信度 %
    min-importance: 4
    idle-timeout-minutes: 5

prompt:
  security:
    max-input-length: 4096
    enable-sanitize: true
  token:
    max-tokens: 8192
    encoding-type: cl100k_base

websearch:
  enabled: true
  engine: bing
  bing-api-key: ""                                   # 留空则使用 HTML 抓取

rate-limit:
  enabled: true
  requests-per-minute: 10
```

---

## 四、技术栈版本

### 4.1 后端依赖 (`pom.xml`)

| 依赖 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.0 | 核心框架 |
| Java | 17 | 编译目标 |
| langchain4j | 0.35.0 | LLM 集成框架 |
| langchain4j-redis | 0.35.0 | Redis 向量存储 |
| langchain4j-ollama | 0.35.0 | Ollama 模型适配 |
| langchain4j-embeddings | 0.35.0 | 嵌入向量生成 |
| Resilience4j | 2.1.0 | 弹性保护 |
| Lombok | 1.18.30 | 代码简化 |
| H2 | runtime | 开发降级数据库 |
| MySQL Connector/J | runtime | MySQL JDBC 驱动 |
| OkHttp | 4.x (传递依赖) | 来自 langchain4j |

### 4.2 前端依赖 (`package.json`)

| 依赖 | 说明 |
|------|------|
| React 19 | UI 框架 |
| TypeScript | 类型系统 |
| Vite | 构建工具 |
| Tailwind CSS | 样式框架 |
| react-markdown | Markdown 渲染 |
| react-syntax-highlighter | 代码语法高亮 |
| remark-gfm | GitHub Flavored Markdown |

---

## 五、数据库表清单

| 表名 | 主键策略 | 说明 |
|------|---------|------|
| `conversation` | UUID (手动生成) | 对话会话 |
| `message` | UUID (手动生成) | 对话消息 |
| `long_term_memory` | AUTO_INCREMENT | 长期记忆 |
| `model_configs` | AUTO_INCREMENT | 模型配置 |
| `user_profile` | UUID (手动) | 用户资料 |
| `user_setting` | UUID (手动) | 用户设置 |
| `user_device` | UUID (手动) | 用户设备 |
| `api_key` | UUID (手动) | API 密钥 |
| `notes` | UUID (手动) | 笔记 |
| `todos` | UUID (手动) | 待办事项 |
| `prompt_templates` | UUID (手动) | Prompt 模板 |
| `prompt_metrics` | AUTO_INCREMENT | Prompt 指标 |

**注意：** 主键生成策略不一致。部分实体使用手动 `UUID.randomUUID()`，部分使用数据库 `AUTO_INCREMENT`。

---

## 六、Redis 键空间设计

```
kchat:memory:{conversationId}                # 短期记忆 (String, JSON, 24h TTL)
memory:embedding:{userId}:{memoryId}          # 向量嵌入 (序列化的 float[])
memory:index:{userId}                         # 用户记忆索引 (Set<memoryId>)
optimize:rate:{userId|ip}                     # 限流计数器 (ZSet, 1min 窗口)
```

---

## 七、日志配置

```yaml
logging:
  level:
    root: INFO
    com.example.app: DEBUG
    com.example.app.service: DEBUG
    com.example.app.controller: DEBUG
    com.example.app.aspect: DEBUG
    org.hibernate.SQL: WARN
  pattern:
    console: '%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n'
```

**日志输出位置：**
- 后端：`/tmp/kchat-backend.log`（通过 `scripts/start.sh` 启动时）
- 前端：`/tmp/kchat-frontend.log`（同上）
- 直接 `mvn spring-boot:run`：标准输出

---

## 八、部署建议（待实现）

### 8.1 推荐的生产化改造

**配置外部化：**
```
当前问题: 敏感信息硬编码在 application.yml
建议:
  - 使用环境变量覆盖: spring.datasource.password=${DB_PASSWORD}
  - 使用 Spring Cloud Config 或 Kubernetes ConfigMap/Secret
  - 前端: 构建时注入 VITE_API_URL
```

**数据库迁移：**
```
当前问题: ddl-auto: update 不可用于生产
建议:
  - 集成 Flyway 或 Liquibase
  - 将现有 DDL 整理为版本化迁移脚本
  - 生产环境改为 ddl-auto: validate
```

**容器化：**
```
建议的 Docker Compose 结构:
  services:
    mysql:      mysql:8.0
    redis:      redis:7-alpine
    ollama:     ollama/ollama:latest
    backend:    openjdk:17-slim + kchat-backend.jar
    frontend:   nginx:alpine (serve dist/)
```

**CI/CD 流水线：**
```
建议的 GitHub Actions 流程:
  1. checkout
  2. setup-java (17)
  3. setup-node (18)
  4. mvn test (backend)
  5. npm test (frontend)
  6. npm run build (frontend)
  7. mvn package -DskipTests (backend)
  8. docker build & push
  9. deploy (ssh / k8s)
```

**健康检查端点：**
```
当前: 无
建议: 添加 Spring Boot Actuator
  - /actuator/health
  - /actuator/health/readiness (DB + Redis 可用性)
  - /actuator/health/liveness
  - /actuator/metrics (Prometheus)
```

### 8.2 安全加固清单

| 项目 | 当前状态 | 建议 |
|------|---------|------|
| 身份认证 | 无（userId 查询参数） | 实现 JWT + Spring Security |
| HTTPS | 无 | 生产环境强制 HTTPS |
| 数据库密码 | 明文硬编码 | 环境变量/密钥管理服务 |
| API 限流 | 仅内容优化接口 | 扩展到所有 /api/chat 端点 |
| CORS | `localhost:*` | 生产限定具体域名 |
| 输入验证 | 仅 Prompt 层 | 在 Controller 层统一校验 |
| SQL 注入 | JPA 参数化查询（安全） | — |
| CVE 扫描 | 无 | 集成 Dependabot / Snyk |

### 8.3 可观测性

```
当前: 仅有控制台日志 + PromptMetrics 数据库表
建议:
  - 集成 Micrometer + Prometheus
  - 请求级别: 响应时间、状态码分布
  - LLM 级别: 首 token 延迟、token 生成速率、模型可用性
  - 业务级别: 对话数、记忆提取成功率、截断率
  - 告警: LLM 服务不可用、Redis 连接失败、数据库连接池耗尽
```

---

## 九、当前运维操作速查

```bash
# === 启动 ===
bash scripts/start.sh

# === 停止 ===
bash scripts/stop.sh

# === 查看后端日志 ===
tail -f /tmp/kchat-backend.log

# === 查看前端日志 ===
tail -f /tmp/kchat-frontend.log

# === 后端单独重启 ===
lsof -ti:8080 | xargs kill -9
cd backend && mvn spring-boot:run

# === 前端单独重启 ===
lsof -ti:5173 | xargs kill -9
cd frontend && npm run dev

# === 清理并重新构建 ===
cd backend && mvn clean package -DskipTests
cd frontend && rm -rf dist node_modules && npm install && npm run build

# === 数据库查看 ===
mysql -u admin -p kchatdb
> SHOW TABLES;
> SELECT COUNT(*) FROM conversation;
> SELECT COUNT(*) FROM long_term_memory;

# === Redis 查看 ===
redis-cli
> KEYS kchat:*
> KEYS memory:*
> ZCARD optimize:rate:*

# === 检查服务状态 ===
curl http://localhost:8080/api/models         # 后端健康
curl http://localhost:5173                     # 前端健康
```
