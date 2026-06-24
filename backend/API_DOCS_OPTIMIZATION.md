# 内容优化 API 接口文档

## 概述

内容优化接口提供对用户输入文本的智能化优化处理，包括语法纠错、语义优化、格式规范化和关键词提取等功能。

---

## API 端点

### POST /api/chat/optimize

优化文本内容

#### 请求体

| 字段 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| content | String | 是 | 需要优化的文本内容，最大长度 4096 字符 |
| userId | String | 否 | 用户标识，用于限流统计 |
| optimizationType | String | 否 | 优化类型：`grammar`/`semantic`/`format`/`keyword`/`all`，默认全部优化 |
| modelId | String | 否 | 用户当前选择的模型ID |
| modelType | String | 否 | 模型类型：`OPENAI_COMPATIBLE`/`OLLAMA`/`OPENAI`/`ANTHROPIC`/`GOOGLE`/`AZURE`/`CUSTOM` |
| baseUrl | String | 否 | 模型基础URL（当使用自定义模型时需要提供） |
| apiKey | String | 否 | API密钥（当使用需要认证的模型时需要提供） |

#### 请求示例

```json
{
  "content": "这是一段需要优化的文本内容，可能存在一些语法错误和表达问题。",
  "userId": "user123",
  "optimizationType": "all",
  "modelId": "llama3",
  "modelType": "OLLAMA"
}
```

#### 使用 OpenAI 模型的请求示例

```json
{
  "content": "这是一段需要优化的文本内容。",
  "userId": "user123",
  "modelId": "gpt-4o-mini",
  "modelType": "OPENAI",
  "baseUrl": "https://api.openai.com/v1",
  "apiKey": "sk-xxx"
}
```

#### 成功响应

| 字段 | 类型 | 说明 |
|-----|------|------|
| success | Boolean | 操作是否成功 |
| optimizedContent | String | 优化后的文本内容 |
| originalContent | String | 原始文本内容 |
| optimizations | Array | 优化详情列表 |
| processingTimeMs | Number | 处理耗时（毫秒） |

**响应示例**:

```json
{
  "success": true,
  "optimizedContent": "这是一段需要优化的文本内容，可能存在一些语法错误和表达问题。",
  "originalContent": "这是一段需要优化的文本内容，可能存在一些语法错误和表达问题。",
  "optimizations": [
    {
      "type": "grammar",
      "description": "语法错误修正"
    },
    {
      "type": "semantic",
      "description": "语义优化"
    },
    {
      "type": "format",
      "description": "格式规范化"
    },
    {
      "type": "keyword",
      "description": "关键词强化"
    }
  ],
  "processingTimeMs": 120
}
```

#### 失败响应

| 字段 | 类型 | 说明 |
|-----|------|------|
| success | Boolean | 操作是否成功 |
| error | String | 错误码 |
| message | String | 错误消息 |
| retryAfterSeconds | Number | 重试等待时间（限流时返回） |

**限流响应示例**:

```json
{
  "success": false,
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "请求过于频繁，请稍后重试",
  "retryAfterSeconds": 60
}
```

**错误响应示例**:

```json
{
  "success": false,
  "error": "OPTIMIZATION_FAILED",
  "message": "内容优化失败：模型服务不可用"
}
```

---

## 优化类型说明

| 类型 | 描述 |
|-----|------|
| grammar | 语法纠错：检查并修正文本中的语法错误、拼写错误和标点符号问题 |
| semantic | 语义优化：改进句子结构，使表达更加清晰、流畅、专业 |
| format | 格式规范化：统一标点符号、大小写，优化段落结构 |
| keyword | 关键词强化：识别并突出关键信息，增强表达力度 |
| all | 全部优化（默认）：执行上述所有优化操作 |

---

## 限流策略

- **限制频率**: 每分钟最多 10 次请求
- **限流对象**: 基于用户 ID 或客户端 IP 进行限制
- **缓存机制**: 使用 Redis 实现滑动窗口限流

---

## HTTP 状态码

| 状态码 | 说明 |
|-------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误或优化失败 |
| 429 | 请求过于频繁（限流） |
| 500 | 服务器内部错误 |

---

## 使用示例

### cURL

```bash
curl -X POST http://localhost:8080/api/chat/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "content": "我今天非常高兴见到了你，希望下次还能再见面。",
    "userId": "user123"
  }'
```

### JavaScript

```javascript
const response = await fetch('/api/chat/optimize', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    content: '需要优化的文本内容',
    userId: 'user123'
  })
});

const result = await response.json();
console.log(result.optimizedContent);
```

---

## 注意事项

1. **内容长度限制**: 单次请求内容最大长度为 4096 字符
2. **响应时间**: 正常情况下响应时间不超过 500ms
3. **限流机制**: 每分钟最多 10 次请求，超出限制将返回 429 状态码
4. **模型依赖**: 优化服务依赖 LLM 模型（默认使用 Ollama llama3）