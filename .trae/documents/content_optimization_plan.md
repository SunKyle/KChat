# 输入框内容优化功能 - 实现计划

## 1. 需求分析

根据用户需求，需要实现一个输入框内容优化功能，包含：

### 1.1 前端需求
- 在现有输入框组件中添加"内容优化"按钮及状态指示
- 实现优化过程的加载状态显示
- 提供"应用优化"和"取消"选项
- 响应时间不超过 300ms

### 1.2 后端需求
- 开发内容优化 API 接口
- 实现文本优化算法（语法纠错、语义优化、格式规范化、关键词提取与强化）
- 设计请求频率限制机制
- 添加错误处理与日志记录
- 响应时间不超过 500ms

### 1.3 交付物
- 完整的前后端代码实现
- API 接口文档
- 单元测试与集成测试用例
- 功能使用说明

---

## 2. 技术方案

### 2.1 前端设计

**修改文件**: `frontend/src/components/chat/InputArea/index.tsx`

**新增组件**: 
- 添加"内容优化"按钮（使用 Sparkles 图标）
- 实现优化结果展示区域
- 添加"应用优化"和"取消"按钮

**状态管理**:
```typescript
interface OptimizationState {
  isOptimizing: boolean;
  optimizedContent: string | null;
  showOptimizationResult: boolean;
  lastOptimizedInput: string;
}
```

**API 调用**: 调用 `/api/chat/optimize` POST 接口

### 2.2 后端设计

**新增文件**:

| 文件路径 | 说明 |
|---------|------|
| `controller/ContentOptimizationController.java` | 内容优化 API 控制器 |
| `service/ContentOptimizationService.java` | 内容优化服务接口 |
| `service/impl/ContentOptimizationServiceImpl.java` | 内容优化服务实现 |
| `dto/ContentOptimizationRequest.java` | 请求 DTO |
| `dto/ContentOptimizationResponse.java` | 响应 DTO |
| `aspect/RateLimitAspect.java` | 限流切面 |

**API 端点**:
- `POST /api/chat/optimize` - 内容优化接口

**限流机制**:
- 使用 Redis 实现滑动窗口限流
- 配置：每分钟最多 10 次请求

**文本优化算法**:
- 基于 LLM 模型进行智能优化
- 支持语法纠错、语义优化、格式规范化、关键词提取

---

## 3. 文件修改清单

### 3.1 前端文件

| 文件 | 操作 | 说明 |
|-----|------|------|
| `frontend/src/components/chat/InputArea/index.tsx` | 修改 | 添加优化按钮和结果展示 |
| `frontend/src/api/chat.ts` | 修改 | 添加优化 API 调用方法 |

### 3.2 后端文件

| 文件 | 操作 | 说明 |
|-----|------|------|
| `controller/ContentOptimizationController.java` | 新增 | 优化 API 控制器 |
| `service/ContentOptimizationService.java` | 新增 | 优化服务接口 |
| `service/impl/ContentOptimizationServiceImpl.java` | 新增 | 优化服务实现 |
| `dto/ContentOptimizationRequest.java` | 新增 | 请求 DTO |
| `dto/ContentOptimizationResponse.java` | 新增 | 响应 DTO |
| `aspect/RateLimitAspect.java` | 新增 | 限流切面 |
| `application.yml` | 修改 | 添加限流配置 |

### 3.3 测试文件

| 文件 | 操作 | 说明 |
|-----|------|------|
| `controller/ContentOptimizationControllerTest.java` | 新增 | 控制器测试 |
| `service/ContentOptimizationServiceTest.java` | 新增 | 服务层测试 |

---

## 4. 实现步骤

### 步骤 1: 创建后端 DTO 类
- `ContentOptimizationRequest.java`
- `ContentOptimizationResponse.java`

### 步骤 2: 创建限流切面
- `RateLimitAspect.java` - 使用 Redis 实现滑动窗口限流

### 步骤 3: 创建内容优化服务
- `ContentOptimizationService.java` 接口
- `ContentOptimizationServiceImpl.java` 实现

### 步骤 4: 创建 API 控制器
- `ContentOptimizationController.java`

### 步骤 5: 更新配置文件
- `application.yml` 添加限流配置

### 步骤 6: 更新前端输入框组件
- 添加优化按钮和状态管理
- 实现优化结果展示和操作

### 步骤 7: 更新前端 API 模块
- 添加优化接口调用方法

### 步骤 8: 编写测试用例
- 单元测试
- 集成测试

---

## 5. API 接口设计

### 5.1 POST /api/chat/optimize

**请求体**:
```json
{
  "content": "用户输入的文本内容",
  "userId": "可选，用户标识"
}
```

**响应体**:
```json
{
  "success": true,
  "optimizedContent": "优化后的文本内容",
  "originalContent": "原始文本内容",
  "optimizations": [
    {"type": "grammar", "description": "语法错误修正"},
    {"type": "semantic", "description": "语义优化"},
    {"type": "format", "description": "格式规范化"},
    {"type": "keyword", "description": "关键词强化"}
  ],
  "processingTimeMs": 120
}
```

**错误响应**:
```json
{
  "success": false,
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "请求过于频繁，请稍后重试",
  "retryAfterSeconds": 60
}
```

---

## 6. 限流策略

| 配置项 | 值 | 说明 |
|-------|-----|------|
| `rate-limit.enabled` | true | 是否启用限流 |
| `rate-limit.requests-per-minute` | 10 | 每分钟最大请求数 |
| `rate-limit.cache-prefix` | "optimize:rate:" | Redis 缓存前缀 |

---

## 7. 风险评估

| 风险 | 描述 | 应对策略 |
|-----|------|---------|
| API 滥用 | 恶意用户频繁调用 | Redis 限流 + IP 封禁 |
| 响应延迟 | 优化算法耗时过长 | 设置超时时间 + 异步处理 |
| 数据安全 | 用户输入包含敏感内容 | 输入过滤 + 日志脱敏 |
| 并发问题 | 高并发请求 | Redis 分布式锁 |

---

## 8. 测试计划

### 8.1 单元测试
- 限流切面测试
- 优化服务测试
- API 控制器测试

### 8.2 集成测试
- 端到端接口测试
- 限流策略测试
- 错误处理测试

### 8.3 性能测试
- 响应时间测试（目标 < 500ms）
- 并发请求测试

---

## 9. 交付时间预估

| 阶段 | 时间 | 说明 |
|-----|------|------|
| 后端实现 | 1天 | DTO、Service、Controller、限流 |
| 前端实现 | 0.5天 | 按钮、状态、交互 |
| 测试编写 | 0.5天 | 单元测试、集成测试 |
| 文档编写 | 0.5天 | API文档、使用说明 |
| **总计** | **2.5天** | |