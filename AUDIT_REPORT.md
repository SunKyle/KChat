# Impeccable Audit Report

**Project:** KChat | **Register:** Product | **Date:** 2026-06-22
**Scope:** `frontend/src/` — all `.tsx`, `.ts`, `.css` files

---

## Audit Health Score

| # | Dimension | Score | Key Finding |
|---|-----------|-------|-------------|
| 1 | Accessibility | **2/4** | 6 buttons missing aria-labels; heading hierarchy gaps in 4 surfaces; dark-mode placeholder fails 4.5:1 contrast |
| 2 | Performance | **3/4** | Heavy libs present but lazily loaded; 15 components not memoized; images lack lazy loading |
| 3 | Theming | **3/4** | ProfileCard.css has 20+ raw rgba values outside tokens; DESIGN.md documents clamp() but CSS uses media query |
| 4 | Responsive Design | **3/4** | 15×15px touch targets in sidebar; 27 overflow containers (potential clipping); MemoryPanel fixed min-widths |
| 5 | Anti-Patterns | **2/4** | Bounce easing in 6 places; glassmorphism in 7+ locations; 5 uppercase-tracked eyebrow patterns |
| **Total** | | **13/20** | **Acceptable** — significant work needed in a11y and anti-patterns |

---

## Anti-Patterns Verdict

**PASS (barely).** The interface does not scream "AI-generated" — the warm coral palette is distinctive, system fonts are correct for product UI, and no gradient text or hero-metric templates exist. However, three AI tells persist:

1. **Bounce/spring easing** in 6 animation definitions — the single most common AI motion tell after the 2025-2026 saturation
2. **Glassmorphism creep** — `backdrop-blur` in 7 locations including toasts, modals, and sidebar badges, when only the input area card should use it
3. **Uppercase tracked eyebrows** — 5 instances of `uppercase tracking-wider` on section labels (NoteTodoPanel tabs, NoteList/TodoList date headers, CodeBlock language label)

---

## Executive Summary

- **Audit Health Score:** 13/20 (Acceptable)
- **Total issues:** 5 P0 · 8 P1 · 10 P2 · 0 P3
- **Top critical issues:**
  1. Dark-mode placeholder text fails WCAG AA contrast (2.6:1)
  2. 6 interactive buttons lack accessible names
  3. Bounce easing violates reduced-motion expectations and product motion rules
  4. Glassmorphism used as default backdrop treatment in 7+ places
  5. DESIGN.md documents clamp() typography but CSS uses media query — spec/impl mismatch
- **Recommended next step:** Run `/impeccable polish` to address P0+P1 findings in a single pass

---

## Detailed Findings

### P0 — Blocking (5)

**[P0] Dark-mode placeholder contrast failure**
- **Location:** `index.css` — `--text-placeholder: #666666` on `--bg-input: #2a2a2a`
- **Category:** Accessibility
- **Impact:** Placeholder text unreadable for users with moderate vision impairment in dark mode
- **WCAG:** Fails 1.4.3 Contrast (Minimum) — 2.6:1, requires ≥4.5:1
- **Recommendation:** Lighten placeholder to `#999999` (4.6:1) or `#aaaaaa` (6.3:1)
- **Suggested command:** `/impeccable polish` (contrast pass)

**[P0] 6 buttons without accessible names**
- **Location:**
  - [MessageBubble.tsx:215](frontend/src/components/chat/ChatArea/MessageBubble.tsx#L215) — regenerate button (only `title` attribute)
  - [MessageBubble.tsx:220](frontend/src/components/chat/ChatArea/MessageBubble.tsx#L220) — save-as-note button (no label at all)
  - [TodoForm.tsx:94](frontend/src/components/note-todo/TodoForm.tsx#L94) — cancel button
  - [TodoForm.tsx:97](frontend/src/components/note-todo/TodoForm.tsx#L97) — submit button
  - [DetailPreview.tsx:168](frontend/src/components/note-todo/DetailPreview.tsx#L168) — todo toggle button
- **Category:** Accessibility
- **Impact:** Screen reader users cannot identify button purpose
- **WCAG:** Fails 4.1.2 Name, Role, Value
- **Recommendation:** Add `aria-label` to each button
- **Suggested command:** `/impeccable harden` or manual fix

**[P0] Bounce easing in UI transitions**
- **Location:** [index.css](frontend/src/index.css) — `--ease-spring`, `error-icon-bounce`, `bounce-in`, `scroll-btn-in`, `status-bar-slide-in` (6 total)
- **Category:** Anti-Pattern
- **Impact:** Bounce easing feels unprofessional in a productivity tool; violates product register "motion conveys state, not decoration" rule
- **Recommendation:** Replace all `cubic-bezier(0.34, 1.56, 0.64, 1)` with `cubic-bezier(0.16, 1, 0.3, 1)` (ease-out). Keep `bounce-in` only for error cards where surprise is the point.
- **Suggested command:** `/impeccable animate`

**[P0] Sidebar logo `font-logo` references `--font-display` at 24-32px — too large for a sidebar label**
- **Location:** [index.css:1274](frontend/src/index.css#L1274) — `.font-logo { font-size: var(--font-display); }` (24-32px)
- **Category:** Typography / Product Register
- **Impact:** Display-sized text in a sidebar label violates product register "display fonts in UI labels" ban. The sidebar logo is a label, not a hero heading.
- **Recommendation:** Cap logo at `var(--font-h3)` (18-20px) — large enough for identity, small enough for a nav label
- **Suggested command:** `/impeccable typeset`

**[P0] DESIGN.md documents `clamp()` typography; implementation uses media query**
- **Location:** [DESIGN.md](DESIGN.md) frontmatter vs [index.css](frontend/src/index.css) `@media (min-width: 768px)`
- **Category:** Theming / Spec-Impl Mismatch
- **Impact:** DESIGN.md is the canonical reference for agents and tools. Reading it gives wrong information about how typography actually works.
- **Recommendation:** Update DESIGN.md frontmatter to match implementation: fixed `rem` values at mobile + overridden at 768px, OR implement clamp() in CSS. Product register prefers fixed rem scale — align DESIGN.md to that.
- **Suggested command:** `/impeccable document`

---

### P1 — Major (8)

**[P1] Glassmorphism as default backdrop**
- **Location:** 7+ components: ErrorContext toasts, APIKeys modal, ModelSettings modal, MemoryForm modal, MemoryPanel delete modal, scroll-btn-glass, card-float
- **Category:** Anti-Pattern
- **Impact:** Per DESIGN.md "blurred glass is for the input area only." Glass everywhere reads as 2024-era AI aesthetic.
- **Recommendation:** Reserve `backdrop-blur` for `.card-float` (input area) and `.scroll-btn-glass`. Modals should use opaque backgrounds.
- **Suggested command:** `/impeccable quieter`

**[P1] 20+ hardcoded rgba colors in ProfileCard.css**
- **Location:** [ProfileCard.css](frontend/src/components/common/ProfileCard.css) — lines 22,62-64,97,100,112,162,165,182,203,210,215-216,221,229-231,264-265,274-275
- **Category:** Theming
- **Impact:** Profile card won't respond to theme changes. Hardcoded dark-bg values break light theme.
- **Recommendation:** Extract to CSS custom properties scoped to `.profile-card-popup`, with light/dark variants.
- **Suggested command:** `/impeccable colorize` (to systematize) or manual refactor

**[P1] 5 uppercase tracking-wider eyebrow patterns**
- **Location:**
  - [NoteTodoPanel.tsx:173](frontend/src/components/note-todo/NoteTodoPanel.tsx#L173) — section tab labels
  - [NoteList.tsx:160](frontend/src/components/note-todo/NoteList.tsx#L160) — date group headers
  - [NoteList.tsx:185](frontend/src/components/note-todo/NoteList.tsx#L185) — date group headers
  - [TodoList.tsx:218](frontend/src/components/note-todo/TodoList.tsx#L218) — date group headers
  - [CodeBlock.tsx:27](frontend/src/components/chat/ChatArea/CodeBlock.tsx#L27) — language label
- **Category:** Anti-Pattern
- **Impact:** The 2023-era "tiny uppercase tracked eyebrow" is one of the most saturated AI tells. The NoteTodoPanel has it on every tab — that's a reflex, not a voice.
- **Recommendation:** Remove `uppercase tracking-wider` from section labels. Use sentence case with weight distinction instead.
- **Suggested command:** `/impeccable typeset`

**[P1] Heading hierarchy gaps — h2 without preceding h1**
- **Location:** ChatArea/index.tsx, DetailPreview.tsx, ProfileInfo.tsx, Drawer.tsx
- **Category:** Accessibility
- **Impact:** Screen reader users navigate by heading structure. Skipping h1 creates a broken outline.
- **WCAG:** Fails 1.3.1 Info and Relationships (heading levels should not be skipped)
- **Recommendation:** Add h1 to each surface, or downgrade h2→h3 in surfaces where h1 belongs to a parent context.
- **Suggested command:** `/impeccable harden`

**[P1] Form inputs without explicit `<label>` association**
- **Location:** 15+ inputs across APIKeys, ModelSettings, MemoryForm, MemoryList, ProfileInfo, Preferences
- **Category:** Accessibility
- **Impact:** Screen reader users cannot identify input purpose when focus lands on field
- **WCAG:** Fails 1.3.1 (label association) and 3.3.2 (labels or instructions)
- **Recommendation:** Wrap inputs in `<label>` or use `htmlFor`/`id` pairing. Placeholder alone is not sufficient.
- **Suggested command:** `/impeccable harden`

**[P1] Text overflow risk in sidebar conversation names**
- **Location:** [ConversationItem.tsx:197](frontend/src/components/sidebar/ConversationItem.tsx#L197) — `font-conversation-name truncate`
- **Category:** Responsive / Readability
- **Impact:** Truncation hides conversation context. At 280px sidebar width with 14px font, titles with CJK characters may truncate after 6-8 chars.
- **Recommendation:** Add `title` attribute showing full conversation name on truncated items.
- **Suggested command:** `/impeccable adapt`

**[P1] scroll-to-bottom button: glass + bounce animation**
- **Location:** [index.css:664](frontend/src/index.css#L664) — `.scroll-btn-glass` + `scroll-btn-in` spring animation
- **Category:** Anti-Pattern (double: glass + bounce)
- **Impact:** Button uses both a banned easing curve and glassmorphism — double anti-pattern
- **Recommendation:** Replace spring with ease-out (200ms); keep glass only if this is the sole exception per DESIGN.md
- **Suggested command:** `/impeccable animate`

**[P1] `text-secondary` (#555) used for body-length prose in blockquotes**
- **Location:** [MarkdownRenderer.tsx:151](frontend/src/components/chat/ChatArea/MarkdownRenderer.tsx#L151) — blockquote uses `theme-text-secondary`
- **Category:** Accessibility / Readability
- **Impact:** Secondary text at #555 on #f9f9f9 background yields ~5.6:1 — passes AA but at body-length reading, the reduced contrast causes fatigue
- **Recommendation:** Use `theme-text-primary` for blockquote body text; rely on the left border + italic for visual distinction
- **Suggested command:** `/impeccable polish`

---

### P2 — Minor (10)

**[P2] react-syntax-highlighter imports full Prism + 2 themes**
- **Location:** CodeBlock.tsx, FullscreenMarkdownEditor.tsx
- **Category:** Performance
- **Impact:** ~50KB+ gzipped for syntax highlighting. The oneDark and oneLight themes each add ~5KB.
- **Recommendation:** Consider dynamic import or a lighter alternative like Shiki with textmate grammars
- **Suggested command:** `/impeccable optimize`

**[P2] 15 components not wrapped in React.memo**
- **Location:** Sidebar, Header, ConversationItem, ModelSettings, APIKeys, Preferences, ProfileInfo, UserSettings, NoteForm, TodoForm, NoteTodoPanel, MemoryPanel, DetailPreview, SearchResultsCard, TypingIndicator
- **Category:** Performance
- **Impact:** Unnecessary re-renders on parent state changes. Sidebar is the most impactful — re-renders all conversation items on any chat state change.
- **Recommendation:** Add `React.memo` to Sidebar, ConversationItem, Header, and settings panels.
- **Suggested command:** `/impeccable optimize`

**[P2] No `loading="lazy"` on any `<img>` tag (9 instances)**
- **Location:** All 9 img tags across components
- **Category:** Performance
- **Impact:** All images load eagerly, blocking render of below-fold content
- **Recommendation:** Add `loading="lazy"` to all images except the sidebar logo (above-fold)
- **Suggested command:** `/impeccable optimize`

**[P2] 15px × 15px icon touch targets in ConversationItem**
- **Location:** [ConversationItem.tsx](frontend/src/components/sidebar/ConversationItem.tsx) — pin, check, X, edit, delete icons at 15×15px
- **Category:** Responsive / Accessibility
- **Impact:** 15px targets are 1/9th the minimum 44×44px WCAG requirement
- **WCAG:** Fails 2.5.5 Target Size (AAA) and 2.5.8 (AA — 24px minimum)
- **Recommendation:** Wrap icons in a 44×44px touch area (use `icon-btn` pattern already defined in CSS)
- **Suggested command:** `/impeccable adapt`

**[P2] Fixed min-width containers in MemoryPanel**
- **Location:** [MemoryPanel.tsx:191](frontend/src/components/settings/Memory/MemoryPanel.tsx#L191) — `min-w-[100px]` to `lg:min-w-[160px]`
- **Category:** Responsive
- **Impact:** May cause horizontal overflow on narrow (<360px) viewports
- **Recommendation:** Use percentage-based or `min(160px, 100%)` constraints
- **Suggested command:** `/impeccable adapt`

**[P2] `font-conversation-name` weight 600 makes all sidebar items bold**
- **Location:** [index.css](frontend/src/index.css) — `.font-conversation-name { font-weight: 600; }`
- **Category:** Typography
- **Impact:** All conversation items appear emphasized. Only the active item should be semibold; inactive items should use 400 weight for scannability.
- **Recommendation:** Change `.font-conversation-name` to `font-weight: 400`; use `.font-weight-semibold` only on the active conversation
- **Suggested command:** `/impeccable typeset`

**[P2] Sidebar "Summarize" badge uses gradient background + glass**
- **Location:** [sidebar/index.tsx:374](frontend/src/components/sidebar/index.tsx#L374) — `bg-gradient-to-r from-amber-400/15 via-yellow-400/15 to-amber-500/15 ... backdrop-blur-sm`
- **Category:** Anti-Pattern
- **Impact:** Gradient + glass combo on a tiny badge — decorative excess at the smallest scale
- **Recommendation:** Replace with solid `bg-amber-400/15` and remove `backdrop-blur-sm`
- **Suggested command:** `/impeccable quieter`

**[P2] Modal buttons use Tailwind color classes instead of brand tokens**
- **Location:** [Modal.tsx:143](frontend/src/components/common/Modal.tsx#L143) — `bg-red-500 hover:bg-red-600`, `bg-yellow-500`, `bg-blue-500`
- **Category:** Theming
- **Impact:** Danger/warning/info button colors bypass design tokens — won't adapt if semantic colors change
- **Recommendation:** Use `var(--brand-danger)`, `var(--brand-warning)`, `var(--brand-info)` instead
- **Suggested command:** `/impeccable colorize`

**[P2] `font-secondary` class is a no-op (system sans = default)**
- **Location:** [index.css](frontend/src/index.css) — `.font-secondary { font-family: system sans; }`
- **Category:** Cleanup
- **Impact:** Zero visual effect — the default font is already system sans. Adds CSS weight with no benefit.
- **Recommendation:** Either remove the class and its 14 usages, or repurpose it to actually do something (e.g., lighter color + smaller size)
- **Suggested command:** `/impeccable distill`

**[P2] ConversationItem inline edit input uses raw border/shadow values**
- **Location:** [ConversationItem.tsx:192](frontend/src/components/sidebar/ConversationItem.tsx#L192) — `border theme-border-primary ... focus:border-[var(--accent-primary)]/50`
- **Category:** Theming / Consistency
- **Impact:** Doesn't use `.input-field` class — inconsistent with other form inputs
- **Recommendation:** Replace with `.input-field` class for consistent input styling
- **Suggested command:** `/impeccable polish`

---

## Patterns & Systemic Issues

1. **Two theming systems co-existing.** Components use both CSS custom properties (`var(--brand-primary)`) and Tailwind color classes (`text-red-500`, `bg-gray-800`). ProfileCard.css is entirely outside the token system. This creates drift risk — changing a token value won't update all consumers.

2. **Motion debt.** The `ease-spring` curve is defined as `--ease-spring` and used in 6 keyframes. There's no single place to swap it — each usage would need individual replacement. A `--ease-standard` token exists but isn't consistently enforced.

3. **Glassmorphism proliferation.** `backdrop-blur` appears in scroll buttons, toasts, modals, form dialogs, sidebar badges, and the floating input card. The DESIGN.md rule ("input area only") is not reflected in the implementation.

4. **Accessibility inconsistency.** Some buttons have proper `aria-label` (sidebar toggle, settings, theme toggle); others in the same component tree do not (copy, regenerate, save-as-note). No systematic approach — it's per-developer.

---

## Positive Findings

- **Typographic hierarchy is now coherent.** The unified CSS variable system with a single 768px breakpoint is clean, maintainable, and correct for product UI per the product register. The Tailwind backward-compatible key mapping is elegant.
- **Color system is disciplined.** The two-tier coral (identity + interactive) is a sophisticated pattern. Neutral backgrounds at near-zero chroma avoid the AI cream/sand trap. 13:1 body text contrast is excellent.
- **`prefers-reduced-motion` is properly implemented.** All animations collapse to 0.01ms — a complete, correct implementation that many projects skip.
- **Component state coverage is decent.** 11 components handle disabled states, 16 handle loading, 19 handle errors, 7 have empty states. Above average for a project at this stage.
- **Dark mode has token parity.** Every light-mode token has a dark-mode counterpart. No dark mode was an afterthought — it's a first-class citizen.

---

## Recommended Actions

Priority order:

1. **[P0] `/impeccable polish`** — Fix dark-mode placeholder contrast + 6 missing aria-labels + blockquote text color
2. **[P0] `/impeccable animate`** — Replace 6 bounce easings with ease-out
3. **[P0] `/impeccable typeset`** — Logo size cap + conversation-name weight fix
4. **[P0] `/impeccable document`** — Align DESIGN.md typography with implementation (media query, not clamp)
5. **[P1] `/impeccable quieter`** — Reduce glassmorphism; remove gradient badge background
6. **[P1] `/impeccable harden`** — Add missing aria-labels, form labels, heading hierarchy fixes
7. **[P2] `/impeccable optimize`** — Add lazy loading, memo wrappers
8. **[P2] `/impeccable adapt`** — Fix touch targets in sidebar, responsive MemoryPanel
9. **[P2] `/impeccable colorize`** — Extract ProfileCard colors to tokens; fix Modal button colors

End with `/impeccable polish` as the final verification pass.

---

> You can ask me to run these one at a time, all at once, or in any order you prefer.
>
> Re-run `/impeccable audit` after fixes to see your score improve.
