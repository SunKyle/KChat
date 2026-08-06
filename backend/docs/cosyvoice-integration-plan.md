# CosyVoice 集成方案

> 目标：将本地已运行的 CosyVoice FastAPI 服务（`/Users/sunxiaokai/Desktop/CosyVoice`）集成进 KChat，支持「朗读 AI 回复」与「声音克隆管理」两个场景。
> 范围：后端调用层 + 前端 UI。本文为先期方案文档，review 后再进入实现。

---

## 1. 现状盘点

### 1.1 CosyVoice 服务（已就绪）

| 项 | 值 |
|---|---|
| 地址 | `http://127.0.0.1:50000` |
| 模型 | `CosyVoice-300M`（CV1） |
| 设备 | CPU（无 CUDA） |
| 采样率 | 22050 |
| 并发 | 1（信号量） |
| 预定义音色 | **0**（`speakers=0`，`spk2info.pt` 为空或未加载） |
| 缓存 | LRU 已启用 |

**可用接口**（`runtime/python/fastapi_enhanced/app.py`）：

- 系统：`GET /health`、`GET /speakers`、`GET /cache/stats`、`DELETE /cache`
- TTS：`POST /tts/{sft,zero-shot,cross-lingual,instruct,instruct2,vc}`
- 响应格式：`wav`（完整 WAV）/ `json`（base64+元信息）/ `stream=true`（raw PCM s16le）
- 统一响应：`{code, message, request_id, data}`

**关键约束**：

1. `speakers=0` ⇒ `/tts/sft`、`/tts/instruct`（CV1 专用，依赖预定义音色）当前**不可用**。可用模式仅 `zero-shot` / `cross-lingual` / `instruct2` / `vc`，均需 `prompt_wav` 或 `zero_shot_spk_id`。
2. CosyVoice 底层（`cosyvoice/cli/cosyvoice.py`）已实现 `add_zero_shot_spk(prompt_text, prompt_wav, zero_shot_spk_id)` 与 `save_spkinfo()`，但 FastAPI enhanced 服务**未暴露注册端点**。
3. CPU 推理慢（一句约数秒~十几秒），流式首包延迟也较高；并发=1，请求需排队。

### 1.2 KChat 现状

- 后端：Spring Boot 3.2 + Java 17 + LangChain4j，分层 `Controller → Service → Client`，`OllamaClient` 用 `HttpURLConnection` + Resilience4j 容错。
- 配置：`application.yml` + `@ConfigurationProperties`（参考 `OllamaConfig`）。
- 前端：React 19 + TS，`src/api/` 按模块拆分，`client.ts` 提供 `request`/`requestStream`/`requestSSE`/`uploadFile`。
- 代理：Vite `/api → localhost:8080`。
- **当前无任何 TTS 代码。**

---

## 2. 架构设计

```
┌──────────────────────────────────────────────────────────────┐
│ 前端 (React)                                                  │
│  - api/tts.ts            TTS API 客户端                       │
│  - types/tts.ts          类型定义                             │
│  - chat 消息播放按钮      朗读 AI 回复                         │
│  - settings/voice        声音克隆管理 UI                      │
└──────────────┬───────────────────────────────────────────────┘
               │ /api/tts/*  (Vite 代理 → 8080)
┌──────────────┴───────────────────────────────────────────────┐
│ 后端 (Spring Boot)                                            │
│  Controller: TtsController        /api/tts/**                 │
│  Service:    TtsService           业务编排                    │
│  Client:     CosyVoiceClient      调用 CosyVoice FastAPI      │
│  Config:     CosyVoiceConfig      连接/默认参数               │
│  Entity:     TtsSpeaker           注册音色元数据              │
│  Repository: TtsSpeakerRepository                             │
└──────────────┬───────────────────────────────────────────────┘
               │ HTTP (multipart/form-data, wav 流)
┌──────────────┴───────────────────────────────────────────────┐
│ CosyVoice FastAPI (50000)    [需扩展 /speakers/register]      │
└──────────────────────────────────────────────────────────────┘
```

**职责划分**：

- **CosyVoice 服务**：模型推理 + 音色向量存储（`spk2info.pt`）。需新增注册端点。
- **KChat 后端**：业务编排、音色元数据持久化（名称/归属/创建时间）、代理转发、容错、用户隔离。
- **KChat 前端**：UI 触发与音频播放。

---

## 3. CosyVoice 服务待扩展

当前 FastAPI enhanced 服务缺少音色注册/删除端点。需在 `runtime/python/fastapi_enhanced/app.py` 新增：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/speakers/register` | 上传 `prompt_wav` + `prompt_text` + `zero_shot_spk_id`，调用 `manager.model.add_zero_shot_spk(...)` + `save_spkinfo()` 持久化 |
| DELETE | `/speakers/{spk_id}` | 从 `spk2info` 移除并 `save_spkinfo()` |
| GET | `/speakers` | 已有，补充返回 `source`（predefined/zero_shot）与 `prompt_text` 预览 |

> 这部分改动在 CosyVoice 仓库内，不属于 KChat 仓库，但属于集成前置依赖。若不愿改 CosyVoice 服务，可走「方案 B」（见 §6.2）。

---

## 4. 后端集成方案

### 4.1 配置（`application.yml` 新增）

```yaml
cosyvoice:
  base-url: http://127.0.0.1:50000
  default-mode: zero-shot        # sft | zero-shot | cross-lingual | instruct2
  default-spk-id: ""             # 注册的默认朗读音色
  default-speed: 1.0
  default-text-frontend: true
  use-cache: true
  connect-timeout-ms: 5000
  read-timeout-ms: 180000        # CPU 推理慢，给足 3 分钟
  max-text-length: 2000          # 与 CosyVoice 上限对齐
```

对应 `config/CosyVoiceConfig.java`（仿 `OllamaConfig`，`@ConfigurationProperties(prefix = "cosyvoice")`）。

### 4.2 CosyVoiceClient（`client/CosyVoiceClient.java`）

职责：封装对 CosyVoice FastAPI 的 HTTP 调用。仿 `OllamaClient`，用 `HttpURLConnection`（与项目现状一致，不引入新依赖），加 `@Retry`/`@CircuitBreaker`。

核心方法骨架：

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CosyVoiceClient {
    private final CosyVoiceConfig config;
    private final ObjectMapper objectMapper;

    // 健康检查
    public CosyVoiceHealth health();

    // 列出音色
    public List<SpeakerInfo> listSpeakers();

    // 注册音色（调 CosyVoice /speakers/register）
    public void registerSpeaker(String spkId, String promptText, byte[] promptWav);

    // 删除音色
    public void deleteSpeaker(String spkId);

    // 合成：零样本，用已注册 spk_id
    public byte[] synthesizeZeroShot(String text, String spkId, double speed, boolean textFrontend);

    // 合成：零样本，直接传 prompt（用于临时试听，不注册）
    public byte[] synthesizeZeroShot(String text, String promptText, byte[] promptWav, double speed);

    // 流式合成（返回 InputStream，逐片 PCM；可选增强）
    public InputStream streamSynthesize(String text, String spkId, double speed);
}
```

**请求构造**：CosyVoice 接口为 `multipart/form-data`，需手工拼 boundary 或引入 `RestClient`/`WebClient`。考虑项目现状，建议用 Spring 6 的 `RestClient`（Spring Boot 3.2 内置，无需额外依赖），比 `HttpURLConnection` 拼 multipart 简洁可靠。

### 4.3 TtsService（`service/TtsService.java` + `impl/TtsServiceImpl.java`）

职责：业务编排，叠加 KChat 侧逻辑。

```java
public interface TtsService {
    // 朗读文本（朗读 AI 回复入口）
    TtsResult speak(String text, String spkId);

    // 临时试听（不注册音色）
    TtsResult preview(String text, String promptText, byte[] promptWav);

    // 音色管理
    SpeakerVo registerSpeaker(String name, String promptText, byte[] promptWav, String ownerUserId);
    List<SpeakerVo> listSpeakers(String ownerUserId);
    void deleteSpeaker(String spkId, String ownerUserId);

    // 服务状态
    CosyVoiceHealth health();
}
```

`TtsResult`：`{ byte[] audio, String format("wav"), int sampleRate, double durationS }`。

**Service 层职责**：

- 文本长度/安全校验（复用 `InputValidator`）。
- spk_id 解析：空时取 `config.defaultSpkId`；校验是否属于该用户。
- 调 `CosyVoiceClient`，处理异常（服务不可用 503、超时、spk 不存在 400）。
- 可选：后端侧二次缓存（Redis，key=文本+spk+speed），减轻 CosyVoice CPU 压力。

### 4.4 TtsController（`controller/TtsController.java`）

```java
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class TtsController {
    private final TtsService ttsService;

    // 朗读文本
    @PostMapping("/speak")
    public ResponseEntity<byte[]> speak(@RequestBody SpeakRequest req);  // {text, spkId?}

    // 临时试听
    @PostMapping("/preview")
    public ResponseEntity<byte[]> preview(@RequestBody PreviewRequest req); // {text, promptText, promptWavBase64?}

    // 音色管理
    @PostMapping("/speakers")
    public ResponseEntity<SpeakerVo> register(@RequestParam MultipartFile promptWav,
                                              @RequestParam String name,
                                              @RequestParam String promptText);
    @GetMapping("/speakers")
    public ResponseEntity<List<SpeakerVo>> listSpeakers();
    @DeleteMapping("/speakers/{spkId}")
    public ResponseEntity<Void> deleteSpeaker(@PathVariable String spkId);

    // 健康状态
    @GetMapping("/health")
    public ResponseEntity<CosyVoiceHealth> health();
}
```

**响应**：`/speak` 与 `/preview` 直接返回 `audio/wav` 字节流，响应头带 `X-Duration-S`、`X-Sample-Rate`。前端用 `blob` + `Audio` 播放。

### 4.5 实体与仓储（声音克隆元数据）

```java
@Entity
@Table(name = "tts_speaker")
public class TtsSpeaker {
    @Id private String spkId;          // zero_shot_spk_id，全局唯一
    private String name;               // 友好名称
    private String promptText;         // 注册时的 prompt 文本
    private String promptWavPath;      // 方案B：本地存的 prompt 音频路径
    private String ownerUserId;        // 归属用户
    private LocalDateTime createdAt;
}
```

- **方案 A**（推荐）：`spkId` + 元数据入表，向量存 CosyVoice 侧（`spk2info.pt`）。`promptWavPath` 可留空。
- **方案 B**：KChat 侧存 `promptWavPath`（本地文件），合成时传 prompt_wav 给 CosyVoice，不依赖 CosyVoice 注册。

### 4.6 DTO

- `SpeakRequest`：`{ text, spkId? }`
- `PreviewRequest`：`{ text, promptText, promptWavBase64? }`
- `SpeakerVo`：`{ spkId, name, promptText, source, createdAt }`
- `CosyVoiceHealth`：`{ status, modelType, sampleRate, device, speakers, queueSize, concurrency }`

### 4.7 容错

`application.yml` 新增 Resilience4j 实例：

```yaml
resilience4j:
  retry:
    instances:
      cosyvoiceRetry:
        max-attempts: 2
        wait-duration: 1s
        retry-exceptions: [java.io.IOException, java.net.SocketTimeoutException]
  circuitbreaker:
    instances:
      cosyvoiceCB:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 15s
```

---

## 5. 前端集成方案

### 5.1 API 客户端（`src/api/tts.ts`）

```ts
import { request } from './client'

export interface Speaker { spkId: string; name: string; promptText: string; source: string; createdAt: string }
export interface TtsHealth { status: string; modelType: string; sampleRate: number; device: string; speakers: number }

export const tts = {
  // 朗读文本，返回 wav blob
  speak: async (text: string, spkId?: string): Promise<Blob> => {
    const res = await fetch(`${BASE_URL}/tts/speak`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, spkId }),
    })
    if (!res.ok) throw new Error(`TTS failed: ${res.status}`)
    return res.blob()
  },

  preview: async (text: string, promptText: string, promptWav?: File): Promise<Blob> => { /* multipart */ },

  listSpeakers: async (): Promise<Speaker[]> => request('/tts/speakers'),
  registerSpeaker: async (name: string, promptText: string, promptWav: File): Promise<Speaker> => { /* multipart */ },
  deleteSpeaker: async (spkId: string): Promise<void> => request(`/tts/speakers/${spkId}`, { method: 'DELETE' }),
  health: async (): Promise<TtsHealth> => request('/tts/health'),
}
```

> 注意：`speak` 返回二进制，不能走 `client.ts` 的 `request`（它默认按 JSON/text 解析）。需直接 `fetch` 或在 `client.ts` 新增 `requestBlob`。

### 5.2 类型定义（`src/types/tts.ts`）

放 `Speaker`、`TtsHealth` 等类型，与 API 模块对应。

### 5.3 朗读 AI 回复（chat 消息播放按钮）

在 AI 消息组件（`components/chat/` 下消息渲染处）增加「播放」图标按钮：

- 点击 → 调 `tts.speak(messageContent, currentUserSpeakerId)` → 拿到 `Blob` → `URL.createObjectURL` → `new Audio(url).play()`。
- 加载中显示 spinner，播放中显示「停止」按钮，播完释放 `URL`。
- 长消息：CosyVoice 单次上限 2000 字，超长需分段（后端 Service 层切段，前端无感）。
- 权限开关：用户设置里加「启用语音朗读」开关，控制按钮显隐。

建议封装 hook `hooks/useTts.ts`：

```ts
export function useTts() {
  const [state, setState] = useState<'idle' | 'loading' | 'playing'>('idle')
  const audioRef = useRef<HTMLAudioElement | null>(null)

  const speak = async (text: string, spkId?: string) => { /* ... */ }
  const stop = () => { /* ... */ }
  return { state, speak, stop }
}
```

### 5.4 声音克隆管理（settings/voice）

新建 `components/settings/voice/`：

- 音色列表：表格展示 `name / spkId / promptText / createdAt`，行内「试听」「删除」。
- 新建音色：表单 `name + promptText + promptWav(上传)` → 「试听」→「保存注册」。
- 试听：调 `tts.preview(sampleText, promptText, promptWav)` 播放。
- 默认音色：单选，存 `UserSetting`（朗读 AI 回复时用）。

---

## 6. 关键决策与权衡

### 6.1 音频交付：非流式 wav vs 流式 PCM

| 维度 | 非流式 wav（推荐） | 流式 PCM |
|---|---|---|
| 实现复杂度 | 低（后端返回字节，前端 blob 播放） | 高（前端 MediaSource/Web Audio 拼流） |
| 首字延迟 | 高（等整句合成完） | 中（首片到达即可播，但 CPU 下首片仍慢） |
| CPU 推理适配 | 合适 | 收益有限 |
| 缓存复用 | 可（相同文本命中缓存秒回） | 流式不写缓存 |

**结论**：默认非流式 wav + 缓存。流式作为后续可选增强（`stream=true`，前端用 Web Audio API 队列播放）。

### 6.2 音色存储：方案 A vs 方案 B

| 维度 | 方案 A（CosyVoice 侧存向量） | 方案 B（KChat 侧存 prompt_wav） |
|---|---|---|
| CosyVoice 改动 | 需新增 `/speakers/register` 端点 | 不改 |
| 合成请求 | 仅传 `spk_id`，轻量、推理跳过 prompt 处理 | 每次传 `prompt_wav`，重传+重复处理 |
| 持久化 | `spk2info.pt` 文件 | KChat DB + 本地文件 |
| 多实例 | CosyVoice 单实例，音色不共享 | KChat 集中管理，可多实例 |

**结论**：推荐方案 A（合成性能更好，符合 CosyVoice 设计）。若不想改 CosyVoice 服务，退方案 B。

### 6.3 超长文本处理

CosyVoice 单次 ≤ 2000 字。`TtsService.speak` 内部按句号/换行切段（≤ 500 字/段），逐段合成后拼接 wav（需 WAV 头重写，或前端分段播放）。推荐：**后端切段 → 前端分段请求 → 顺序播放**，避免单次超时。

### 6.4 并发与排队

CosyVoice `concurrency=1`，并发请求排队。`/health` 的 `queueSize` 可监控。建议：
- 后端 `CosyVoiceClient` 设合理超时（180s）。
- 前端朗读按钮加 loading 态，防重复点击。
- 可选：后端用 `Semaphore` 限流，超时直接返回 503，前端提示「服务繁忙」。

### 6.5 安全

- `InputValidator` 校验 `text`（长度、敏感词）。
- 音色管理需用户鉴权（`ownerUserId` 隔离，防越权删除他人音色）。
- `prompt_wav` 大小限制（≤ 5MB，≤ 30s），格式校验（wav/mp3）。

---

## 7. 接口映射表

| KChat 前端 | KChat 后端 | CosyVoice FastAPI |
|---|---|---|
| `POST /api/tts/speak` | `TtsController.speak` | `POST /tts/zero-shot`（spk_id） |
| `POST /api/tts/preview` | `TtsController.preview` | `POST /tts/zero-shot`（prompt_wav） |
| `POST /api/tts/speakers` | `TtsController.register` | `POST /speakers/register`（**待扩展**） |
| `GET /api/tts/speakers` | `TtsController.listSpeakers` | `GET /speakers` + KChat DB |
| `DELETE /api/tts/speakers/{id}` | `TtsController.deleteSpeaker` | `DELETE /speakers/{id}`（**待扩展**） |
| `GET /api/tts/health` | `TtsController.health` | `GET /health` |

---

## 8. 实施阶段

**阶段 0：CosyVoice 服务扩展**（前置，在 CosyVoice 仓库）
- 新增 `POST /speakers/register`、`DELETE /speakers/{id}`。
- 重启服务验证 `speakers` 计数变化。

**阶段 1：后端调用层**
- `CosyVoiceConfig` + `application.yml`。
- `CosyVoiceClient`（health、listSpeakers、synthesizeZeroShot）。
- `TtsService` + `TtsController`（speak、preview、health）。
- 单测：mock CosyVoice 验证链路。

**阶段 2：声音克隆管理后端**
- `TtsSpeaker` 实体 + `TtsSpeakerRepository`。
- register/list/delete 接口。
- 用户隔离与鉴权。

**阶段 3：前端朗读 AI 回复**
- `api/tts.ts` + `types/tts.ts`。
- `hooks/useTts.ts`。
- 聊天消息播放按钮。

**阶段 4：前端声音克隆管理**
- `components/settings/voice/` 列表 + 新建 + 试听。
- 默认音色选择（存 `UserSetting`）。

**阶段 5（可选）：流式与增强**
- 流式 PCM 播放。
- Redis 二次缓存。
- 超长文本分段。

---

## 9. 风险与注意点

1. **`speakers=0` 待排查**：`CosyVoice-300M` 正常应含预定义音色（中文女等）。可能是 `spk2info.pt` 缺失或加载路径问题。若修复后 `sft` 可用，朗读场景可简化（无需注册音色）。
2. **CPU 推理延迟**：首次朗读体验差，建议首句合成后给 toast 提示「正在合成…」。长期建议上 GPU。
3. **`save_spkinfo()` 并发**：多用户同时注册音色可能写文件冲突，CosyVoice 侧需加锁。
4. **prompt_wav 质量**：建议 ≥ 16kHz、≤ 30s、单人清晰语音，否则克隆效果差。
5. **不引入新前端状态库**：TTS 状态用 `useTts` hook 局部管理，不破项目约定。
6. **RestClient 依赖**：Spring Boot 3.2 自带 `RestClient`，无需改 `pom.xml`。
