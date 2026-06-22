# Typography Audit Report

Generated: 2026-06-22
Scope: `frontend/src/` — all `.tsx`, `.ts`, `.css` files

---

## Summary

**Severity key:** 🔴 Critical — broken or missing styles | 🟠 Major — inconsistent or conflicting | 🟡 Minor — messy but functional

| Category | Finding count |
|----------|--------------|
| 🔴 Critical | 2 |
| 🟠 Major | 5 |
| 🟡 Minor | 6 |

---

## Three Competing Typography Systems

The project has three independent typography sources that partially overlap but are not reconciled. No component consistently uses one system over another.

### System A: Tailwind Config (`tailwind.config.js`)

| Key | Size | Line Height |
|-----|------|-------------|
| `xs` | 10px | 1.4 |
| `sm` | 12px | 1.5 |
| `base` | 14px | 1.5 |
| `lg` | 15px | 1.5 |
| `xl` | 16px | 1.5 |
| `2xl` | 18px | 1.5 |
| `3xl` | 20px | 1.4 |
| `4xl` | 24px | 1.4 |
| `5xl` | 28px | 1.4 |
| `6xl` | 32px | 1.3 |
| `7xl` | 36px | 1.3 |

All line heights are unitless. No letter-spacing is defined.

### System B: CSS Custom Properties + Markdown Styles (`index.css`)

| Token | Value |
|-------|-------|
| `--line-height-h1` | 36px |
| `--line-height-h2` | 32px |
| `--line-height-h3` | 28px |
| `--line-height-title` | 26px |
| `--line-height-secondary` | 22px |
| `--line-height-caption` | 18px |

Markdown headings defined inline with exact pixel values and letter-spacing:
- `h1`: 28px / 36px / 600 / -0.01em
- `h2`: 24px / 32px / 600 / -0.01em
- `h3`: 20px / 28px / 600
- `h4`: 18px / 26px / 600
- `h5`: 16px / — / 600
- `h6`: 15px / — / 600
- `body`: 15px / 1.7 / 400

### System C: Theme Tokens + Typography API (`theme/tokens.ts` + `theme/typography.ts`)

| Role | Size | Line Height | Weight |
|------|------|-------------|--------|
| `display` | 32px | 40px | 700 |
| `h1` | 28px | 36px | 600 |
| `h2` | 24px | 32px | 600 |
| `h3` | 20px | 28px | 600 |
| `title` | 18px | 26px | 600 |
| `bodyL` | 16px | 30px | 400 |
| `bodyM` | 15px | 28px | 400 |
| `secondary` | 14px | 24px | 400 |
| `caption` | 12px | 18px | 400 |
| `tiny` | 11px | 16px | 400 |

Plus 11 component-specific typography tokens (logo, tagline, groupTitle, conversationName, currentTitle, modelName, statusTag, aiMessage, userMessage, codeBlock, inputText, placeholder, helperText).

---

## 🔴 Critical

### C1 — Orphaned CSS Class References (6 classes, used in 25+ locations)

The following CSS utility classes are referenced in TSX files but **have no definition anywhere** in the source. They produce no CSS output and fall through to browser defaults.

| Class | Used in | Files |
|-------|---------|-------|
| `font-secondary` | 14 locations | MessageBubble, TypingIndicator, Header, ErrorCard, Modal, NoteTodoPanel, Sidebar, ProfileInfo, UserSettings, ConversationItem |
| `font-conversation-name` | 5 locations | Header, Sidebar, ConversationItem |
| `font-logo` | 1 location | Sidebar/index.tsx |
| `font-weight-medium` | 4 locations | TypingIndicator, InputArea, ProfileInfo |
| `font-weight-semibold` | 3 locations | ErrorCard, InputArea, ConversationItem |
| `font-weight-bold` | 1 location | (referenced in TokenBadge but not found in search) |

**Impact:** Any element using these classes renders with the browser default font (typically Times New Roman) and weight instead of the intended system sans. This affects:
- All sidebar conversation names (`font-conversation-name`)
- All typing indicators (`font-secondary`, `font-weight-medium`)
- All header model names (`font-secondary`)
- All profile form labels (`font-secondary`, `font-weight-medium`)
- All error card descriptions (`font-secondary`)
- All modal messages (`font-secondary`)
- All note/todo panel search inputs (`font-secondary`)
- Sidebar logo title (`font-logo`)

### C2 — Referenced CSS Variable Does Not Exist

[CodeBlock.tsx:51](frontend/src/components/chat/ChatArea/CodeBlock.tsx#L51) references `var(--font-code-base)`:

```tsx
fontSize: 'var(--font-code-base)',
```

`--font-code-base` is not defined in `index.css` or any other CSS file. The element falls back to the browser default font size.

---

## 🟠 Major

### M1 — Body Text Defined at Two Different Sizes

| Source | Size | Line Height | Where Applied |
|--------|------|-------------|---------------|
| Tailwind `base` | **14px** | 1.5 | All `text-base` elements |
| CSS `body` / `.markdown-body` | **15px** | 1.7 | Global body, markdown content |
| Token `bodyM` | **15px** | 28px (1.87) | Typography API calls |

Tailwind's `text-base` (14px) is the framework's default for body text, but the CSS body and markdown body both set 15px. Any component using `text-base` gets 14px — 1px smaller than the intended body size. This is a one-step mismatch in the scale origin.

**Recommendation:** Align Tailwind `base` to 15px to match the CSS body, or stop using `text-base` for body text.

### M2 — Font Weight Proliferation (4 weights in use, 2 in DESIGN.md spec)

DESIGN.md specifies "only 400 and 600." Actual usage across the codebase:

| Weight | Tailwind class | Usage count | DESIGN.md status |
|--------|---------------|-------------|------------------|
| 400 | `font-normal` (default) | Global default | ✅ Allowed |
| 500 | `font-medium` | **50+** locations | ❌ Prohibited |
| 600 | `font-semibold` | **30+** locations | ✅ Allowed |
| 700 | `font-bold` | 6 locations | ❌ Prohibited |

`font-medium` (500) is the most-used explicit weight after 400. It appears on buttons, labels, settings headings, badges, and navigation items. `font-bold` (700) appears on Markdown headings (h1–h3), `<strong>`, and the theme token `display` role.

Either the DESIGN.md rule is wrong, or every `font-medium` and `font-bold` usage is a violation.

### M3 — Markdown Renderer Duplication with Different Styles

Two independent Markdown renderers apply different type scales to the same heading levels:

| Element | MarkdownRenderer.tsx | FullscreenMarkdownEditor.tsx |
|---------|---------------------|------------------------------|
| H1 | `text-2xl` (18px) + `font-bold` (700) | `text-2xl` (18px) + `font-bold` (700) |
| H2 | `text-xl` (16px) + `font-bold` (700) | `text-xl` (16px) + `font-semibold` (600) |
| H3 | `text-lg` (15px) + `font-bold` (700) | `text-lg` (15px) + `font-semibold` (600) |
| H4 | (not styled) | `text-base` (14px) + `font-semibold` (600) |

The Tailwind size classes (`text-2xl` = 18px, `text-xl` = 16px, `text-lg` = 15px) are significantly smaller than the CSS markdown styles (h1=28px, h2=24px, h3=20px, h4=18px). The Tailwind-based MarkdownRenderer headings are **10px smaller at h1** than the CSS-based ones. Both renderers also use `font-bold` (700) which conflicts with the CSS markdown styles using 600 weight.

Additionally, the CSS markdown styles in `index.css` define h1–h6 with pixel sizes, line-heights, and letter-spacing — but the MarkdownRenderer component overrides these with Tailwind utility classes.

### M4 — Line Height Unit Conflict

The codebase mixes two line-height systems:

| System | Example | Where |
|--------|---------|-------|
| **Unitless** (multiplier) | `1.7`, `1.5`, `1.4` | Tailwind config, CSS body, `.markdown-body`, most components |
| **Pixel** (absolute) | `36px`, `32px`, `28px`, `26px`, `22px`, `18px`, `20px` | CSS variables `--line-height-*`, theme tokens, `.markdown-body pre` |

Absolute pixel line heights do not scale with font size — a 36px line height on a 28px heading yields a 1.29 ratio, but if the heading size ever changes, the line height won't adjust. The CSS variables are used both in markdown headings (per-size appropriate) and in generic contexts like `.tooltip-content` (where 18px is hard-coded for 12px text — a 1.5 ratio).

### M5 — AI Message vs User Message at Different Sizes

| Source | AI Message | User Message |
|--------|-----------|--------------|
| Theme tokens | 16px / 30px line-height | 15px / 28px line-height |
| CSS `.markdown-body` | 15px / 1.7 (25.5px) | (not separately styled) |

The token system gives AI messages a 1px larger font than user messages. But the CSS markdown body (which renders AI messages) uses 15px. The 16px AI message token is not consistently applied.

---

## 🟡 Minor

### m1 — Unused Typography API

[theme/typography.ts](frontend/src/theme/typography.ts) exports a complete typed typography API with `typography.h1()`, `componentTypography.aiMessage()`, etc. Each returns a `React.CSSProperties` object. These functions are **not called anywhere** in the codebase — all components use Tailwind classes or CSS variables directly.

### m2 — Tiny Font Size (10px — Below Readability Threshold)

Tailwind `text-xs` = 10px with 1.4 line height. The CSS standard minimum for readable body text is 12px. `text-xs` appears in only one location:
- [CodeBlock.tsx:27](frontend/src/components/chat/ChatArea/CodeBlock.tsx#L27): language label

At 10px, this label is below WCAG's minimum readable size and may be illegible on high-DPI screens.

### m3 — `letter-spacing: 0.02em` on Table Headers Only

`.markdown-body th` applies `letter-spacing: 0.02em` — the only place in the entire codebase with positive tracking. No other element uses this spacing treatment, making table headers visually disconnected from the rest of the typography system.

### m4 — Gradient Text on Logo (Violates DESIGN.md)

[sidebar/index.tsx:206](frontend/src/components/sidebar/index.tsx#L206):
```html
<h1 class='font-logo bg-gradient-to-r from-[var(--brand-primary)] via-[var(--accent-primary)] to-[var(--accent-purple)] bg-clip-text text-transparent'>
```

This is `background-clip: text` combined with a gradient — explicitly prohibited by DESIGN.md's "No Decoration Rule." Additionally, `font-logo` is undefined (see C1).

### m5 — Inconsistent `leading-*` Usage

| Class | Ratio | Used In |
|-------|-------|---------|
| `leading-relaxed` | 1.625 | 8 locations (message body, error cards, modals, note body, memory items, markdown editor) |
| `leading-snug` | 1.375 | 3 locations (note/todo detail titles) |
| `leading-tight` | 1.25 | 4 locations (note list titles, todo list titles, sidebar conversation names) |
| `leading-none` | 1 | 2 locations (sidebar logo, sidebar status badges) |

No clear hierarchy governs when to use which. `leading-relaxed` (1.625) is the most-used but falls between CSS body `1.7` and Tailwind `1.5`. `leading-snug` (1.375) and `leading-tight` (1.25) are used interchangeably for similar heading contexts.

### m6 — Token Colors Stale in `theme/tokens.ts`

[theme/tokens.ts](frontend/src/theme/tokens.ts) defines brand colors as `#0EA5E9` (sky blue) — the original color before the warm brown and the current warm coral. The tokens file has not been updated through either color migration. The typography tokens reference a stale color system.

---

## Readability Assessment

| Check | Status | Detail |
|-------|--------|--------|
| Body text ≥ 12px | ✅ Pass | 15px globally |
| Body line-height ≥ 1.5 | ✅ Pass | 1.7 on body, 1.5 in Tailwind |
| Max line length 65–75ch | ⚠️ Unknown | No `max-width` in characters; content area is 800px which at 15px ≈ 53ch — below the minimum |
| Heading hierarchy distinguishable | ⚠️ Mixed | CSS markdown has 6 clear levels; MarkdownRenderer only has 3 (h1=18px, h2=16px, h3=15px), nearly indistinguishable |
| Contrast ≥ 4.5:1 on body text | ✅ Pass | `#202020` on `#f9f9f9` ≈ 13:1 |
| Font loading without layout shift | ✅ Pass | System font stack, no web fonts |
| `prefers-reduced-motion` respected | ✅ Pass | All animations collapse to 0.01ms |
| Smallest interactive text ≥ 12px | ⚠️ Partial | CodeBlock label at 10px (see m2); badges at 12px ✅ |
| No all-caps body text | ✅ Pass | Uppercase is restricted to badges and section kickers |

---

## Action Priority

1. **Define the missing CSS classes** (`font-secondary`, `font-conversation-name`, `font-logo`, `font-weight-medium`, `font-weight-semibold`) — these are actively broken across 25+ elements. Define them in `index.css` or remove them and use standard Tailwind equivalents.
2. **Define `--font-code-base`** or remove the reference in CodeBlock.tsx.
3. **Align body size** — make Tailwind `base` = 15px, or stop using `text-base` for body text.
4. **Resolve weight specification** — either update DESIGN.md to allow 500 and 700, or refactor all `font-medium` → `font-normal` and `font-bold` → `font-semibold`.
5. **Unify MarkdownRenderer heading sizes** with the CSS markdown spec (28/24/20/18/16/15 instead of 18/16/15).
6. **Convert absolute line heights to unitless** — replace pixel values in CSS variables with ratios.
7. **Clean up** — delete or use the typography API; update stale token colors; remove or justify the gradient text logo.
