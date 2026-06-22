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

## Design System

The canonical design spec lives in `DESIGN.md` (visual) and `PRODUCT.md` (strategy) at the project root. Read both before modifying any visual surface.

### Color tokens — two-layer architecture

**Layer 1: shadcn/ui convention** (`index.css :root` / `.dark`):
```css
--primary: #1e9df1;        /* Twitter blue — the sole accent */
--background: #ffffff;     /* light; #000000 in dark */
--foreground: #0f1419;     /* light; #e7e9ea in dark */
--muted: #E5E5E6;          /* light; #181818 in dark */
--border: #e1eaef;         /* light; #242628 in dark */
--destructive: #f4212e;
--ring: #1da1f2;           /* focus ring */
```

**Layer 2: KChat compatibility aliases** (map legacy names → reference tokens):
```css
--brand-primary: var(--primary);
--bg-primary: var(--background);
--text-primary: var(--foreground);
--text-muted: var(--muted-foreground);
/* etc. */
```

Both layers coexist. New code should use reference token names (`var(--primary)`). Existing code using `var(--brand-primary)` continues to work because aliases point to the same values.

### Runtime theme injection (critical to understand)

`ThemeContext.tsx` calls `getThemeColors()` from `theme/types.ts` which reads hardcoded color values from `darkTheme`/`lightTheme` objects, then applies them via `root.style.setProperty()`. These inline styles **override** any CSS `:root {}` declarations. When debugging color issues, always check `theme/types.ts` first — it's the runtime source of truth that can silently override CSS variables.

### Typography

Unified CSS custom property scale in `index.css`. No hardcoded pixel font sizes in any component.

| Token | Mobile (<768px) | Desktop (≥768px) |
|-------|-----------------|------------------|
| `--font-display` | 1.5rem (24px) | 2rem (32px) |
| `--font-h1` – `--font-h4` | 1.375–1rem | 1.75–1rem |
| `--font-body` | 0.875rem (14px) | 0.9375rem (15px) |
| `--font-secondary` | 0.8125rem (13px) | 0.875rem (14px) |
| `--font-caption` | 0.6875rem (11px) | 0.75rem (12px) |
| `--font-code` | 0.8125rem (13px) | 0.875rem (14px) |

Line heights are unitless: `--leading-display: 1.2`, `--leading-heading: 1.3`, `--leading-body: 1.7`, `--leading-caption: 1.5`, `--leading-code: 1.5`.

Tailwind `fontSize` keys (`xs`–`7xl`) are preserved for backward compatibility but all reference CSS variables (e.g., `text-base` → `var(--font-body)`). Components respond to the 768px breakpoint automatically.

**Font weight rule:** Only 400 (Regular) and 600 (Semibold) are allowed. No 300, 500, 700, 900.

**Font stacks:**
- Body: `'Open Sans', -apple-system, BlinkMacSystemFont, ...` (Google Font, ~30KB, `display: swap`)
- Mono: `Menlo, 'JetBrains Mono', 'SF Mono', 'Fira Code', ...`
- Serif: `Georgia, 'Times New Roman', serif`

### Design bans (enforced)

Per `DESIGN.md` Do's and Don'ts:
- No gradient text (`background-clip: text`)
- No side-stripe borders (`border-left` > 1px as colored accent on cards)
- Glassmorphism only on `.card-float` (input area) and `.scroll-btn-glass` — nowhere else
- No uppercase tracking-wider eyebrow labels
- No bounce/spring easing outside error-card animations

### Design tooling

`.impeccable/design.json` is a machine-readable sidecar of DESIGN.md with component HTML/CSS snippets for the live preview panel. Regenerate with `/impeccable document` when DESIGN.md changes.

## Cross-cutting conventions

- **No comments by default** — only add comments for non-obvious constraints, invariants, or workarounds
- **User ID**: All API calls pass `userId` query param, defaulting to `'default'`
- **Token auth**: JWT stored in localStorage as `kchat_token`, auto-attached by API client interceptor
- **i18n**: UI labels are predominantly Chinese, no i18n framework — hardcoded strings

## Key spec documents

- `PRODUCT.md` — Product strategy, users, brand personality, design principles, anti-references
- `DESIGN.md` — Visual design system: color tokens, typography scale, elevation, component patterns
- `docs/operations-guide.md` — Full setup, startup, troubleshooting guide
- `docs/backend-architecture.md` / `docs/frontend-architecture.md` — Detailed architecture docs
- `docs/LONG_TERM_MEMORY_DESIGN.md` — Memory system design rationale
- `docs/frontend-refactor-plan.md` — Planned refactoring tasks
