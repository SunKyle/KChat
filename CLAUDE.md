# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KChat is a ChatGPT-style AI chat application with a **Spring Boot (Java 17) backend** and a **React 19 + TypeScript + Vite frontend**. It supports multi-model AI chat (OpenAI, Anthropic, Google, Ollama, Azure, custom), streaming (SSE), long-term memory with vector recall, and notes/todos.

## Commands

### Frontend (`frontend/`)

```bash
npm run dev              # Start Vite dev server (port 5173, proxies /api → localhost:8080)
npm run build            # Type-check + production build
npm run lint             # ESLint
npm run format           # Prettier write
npm run format:check     # Prettier check
npm run preview          # Preview production build
```

### Backend (`backend/`)

```bash
mvn spring-boot:run      # Start backend (port 8080)
mvn test                 # Run tests
mvn clean package -DskipTests  # Build JAR
java -jar target/kchat-backend-1.0.0.jar  # Run JAR
```

### Start everything

```bash
bash scripts/start.sh    # One-click start: kills existing, starts backend + frontend
bash scripts/stop.sh     # Stop all services
```

## Architecture

### Backend (Spring Boot, Java 17, Maven)

Standard layered architecture under `backend/src/main/java/com/example/app/`:

- **controller/** — REST endpoints: `ChatController`, `MemoryController`, `ModelConfigController`, `UserSettingController`, `ImageController`
- **service/** — Business logic: `ChatService` (sync), `StreamingService` (SSE), `ChatWorkflowService` (orchestrates short-term + long-term memory + prompt assembly)
- **service/impl/** — `MemoryExtractorImpl` (LLM-based extraction), `MemoryRecallerImpl` (semantic recall)
- **client/** — External AI providers: `OllamaClient`, `OpenAICompatibleClient`, `HttpStreamingTemplate`
- **entity/** — JPA entities: `Conversation`, `Message`, `LongTermMemory`, `ModelConfig`, `UserSetting`
- **repository/** — Spring Data JPA repositories
- **memory/** — `ShortTermMemory` (L1 heap + L2 Redis cache), `VectorStoreWrapper` (Redis-based vector index with cosine similarity)
- **config/** — CORS (`WebConfig`), Redis, async thread pools, streaming timeout, Resilience4j circuit breaker

**Key design points:**
- Database: MySQL (HikariCP connection pool), with H2 available as runtime fallback
- Redis: Used for short-term memory L2 cache and vector embeddings storage
- Memory system: Short-term (context window, dual-layer cache) + Long-term (persisted with vector recall). Auto-extracts memories after N messages with confidence/importance thresholds.
- Resilience4j: Retry + circuit breaker on Ollama calls
- SSE streaming: `SseEmitter` with timeout config, auto memory extraction after stream completes

### Frontend (React 19 + TypeScript + Vite)

**No client-side router** — view switching is state-driven. No UI component library — custom components with Tailwind CSS + CSS variables for theming.

**Component tree (top-down):**

```
main.tsx
  ErrorProvider → ThemeProvider → IconProvider
    App
      UserProvider → ChatProvider → ModalProvider
        AppContent
          ├── Sidebar (left, collapsible, grouped by date)
          ├── Header (model selector, theme toggle, settings button)
          ├── ChatArea (message list, markdown rendering, code highlighting)
          ├── InputArea (text input, image upload, send/stop, status bar)
          ├── UserSettings (tabs: profile/preferences/privacy/api-keys/models/memory)
          ├── NoteTodoPanel (right drawer, markdown editor, todos)
          └── Modal / ToastContainer
```

**State management — React Context + useReducer:**

| Context | State | Persistence |
|---------|-------|-------------|
| `ChatContext` | conversations, messages, streaming state, current model | localStorage (conversations) |
| `ThemeContext` | `'dark' \| 'light'` | localStorage |
| `UserContext` | profile, preferences, API keys | fetched from backend |
| `ModalContext` | modal visibility flags | none |
| `ErrorContext` | toast notifications array | none |

`ChatContext` (`src/context/ChatContext.tsx`) is the core — it uses `useReducer` with a `stateRef` (useRef) pattern for accessing current state inside streaming callbacks without stale closures.

**API layer** (`src/api/client.ts`):
- Custom `fetch`-based client with JWT auth interceptor (`localStorage.getItem('kchat_token')`), exponential backoff retry (2 retries by default), timeout via AbortController
- `requestSSE()` — parses SSE `event:message` / `event:done` streams for chat streaming
- `requestStream()` — NDJSON line-by-line parser for stream endpoints
- All endpoints require `userId` query param (defaults to `'default'`)
- API modules: `chat.ts`, `models.ts`, `memory.ts`, `user.ts`, `note-todo.ts`

**Styling:**
- Tailwind CSS 3.4 with custom theme classes (`theme-bg-primary`, `theme-text-primary`, `card-float-solid`, etc.)
- CSS custom properties for light/dark theme switching defined in `src/index.css` (~1600 lines)
- Custom effects: `ElectricBorder` (animated neon border), `SummarizeGlow` (glow animation on conversation items)

### Cross-cutting conventions

- **No comments by default** — only add comments for non-obvious constraints, invariants, or workarounds
- **User ID**: All API calls pass `userId` query param, defaulting to `'default'`
- **Token auth**: JWT stored in localStorage as `kchat_token`, auto-attached by API client interceptor
- **i18n**: UI labels are predominantly Chinese, no i18n framework — hardcoded strings

### Key spec documents (in `docs/`)

- `operations-guide.md` — Full setup, startup, troubleshooting guide
- `backend-architecture.md` / `frontend-architecture.md` — Detailed architecture docs
- `LONG_TERM_MEMORY_DESIGN.md` — Memory system design rationale
- `UI_DESIGN_EVALUATION.md` — UI design decisions and evaluation
- `frontend-refactor-plan.md` — Planned refactoring tasks
