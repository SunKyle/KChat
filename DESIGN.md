---
name: KChat
description: A multi-model AI chat workstation — minimal, modern, fluid.
colors:
  warm-coral:
    - "#f8ede8"
    - "#f0dbd2"
    - "#e0b6a5"
    - "#cf9179"
    - "#c17250"
    - "#a45d40"
    - "#8b4d35"
    - "#6f3d2a"
    - "#532d1f"
  warm-coral-light:
    - "#f0b8a0"
  neutral-bg:
    - "#f9f9f9"
    - "#fbfbfb"
    - "#fcfcfc"
    - "#efefef"
    - "#e8e8e8"
  neutral-ink:
    - "#202020"
    - "#4a4a4a"
    - "#555555"
  neutral-border:
    - "#d8d8d8"
    - "#ebebeb"
    - "rgba(0, 0, 0, 0.08)"
  semantic-green:
    - "#10b981"
    - "#34d399"
  semantic-red:
    - "#e54d2e"
  semantic-amber:
    - "#e8a838"
    - "#fbbf24"
  semantic-blue:
    - "#3b82f6"
    - "#60a5fa"
  semantic-purple:
    - "#8b5cf6"
typography:
  display:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "24px / 32px"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.01em"
  h1:
    fontSize: "22px / 28px"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "-0.01em"
  h2:
    fontSize: "20px / 24px"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "-0.01em"
  h3:
    fontSize: "18px / 20px"
    fontWeight: 600
    lineHeight: 1.3
  h4:
    fontSize: "16px (fixed)"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "14px / 15px"
    fontWeight: 400
    lineHeight: 1.7
    letterSpacing: "0"
  secondary:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "13px / 14px"
    fontWeight: 400
    lineHeight: 1.7
  caption:
    fontSize: "11px / 12px"
    fontWeight: 400
    lineHeight: 1.5
  mono:
    fontFamily: "'JetBrains Mono', 'SF Mono', 'Fira Code', Monaco, 'Consolas', 'Liberation Mono', monospace"
    fontSize: "13px / 14px"
    fontWeight: 400
    lineHeight: 1.5
rounded:
  sm: "6px"
  md: "8px"
  lg: "12px"
  xl: "16px"
  2xl: "20px"
  full: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  2xl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.warm-coral[5]}"
    textColor: "#ffffff"
    rounded: "{rounded.md}"
    padding: "8px 16px"
    typography: "{typography.label}"
  button-primary-hover:
    backgroundColor: "{colors.warm-coral[6]}"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.neutral-ink[1]}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
  button-ghost-hover:
    backgroundColor: "{colors.neutral-bg[3]}"
    textColor: "{colors.neutral-ink[0]}"
  input-field:
    backgroundColor: "{colors.neutral-bg[4]}"
    textColor: "{colors.neutral-ink[0]}"
    rounded: "{rounded.md}"
    padding: "8px 12px"
  card-float:
    backgroundColor: "rgba(255, 255, 255, 0.85)"
    rounded: "{rounded.xl}"
  badge:
    backgroundColor: "{colors.neutral-bg[3]}"
    textColor: "{colors.neutral-ink[1]}"
    rounded: "{rounded.full}"
    padding: "2px 8px"
---

# Design System: KChat

## 1. Overview

**Creative North Star: "The Fluid Canvas"**

KChat's visual system treats the screen as a fluid canvas — surfaces breathe, transitions flow, and content glides into place. Nothing snaps or jars. The interface is a calm, warm-lit workspace where the AI conversation is the sole protagonist, and every UI element either supports the conversation or steps aside.

This is a product design system — design serves the tool, not the other way around. The aesthetic is minimal without being cold, modern without chasing trends, and lively through motion and subtle warmth rather than through decoration. The brand color is a warm coral that carries just enough personality to feel intentional without shouting.

This system explicitly rejects: flashy gradients, dense information layouts, decorative borders, glassmorphism as a default, oversized typography, and any treatment that makes the tool feel like a marketing page. If an element doesn't help the user chat, think, or navigate, it doesn't belong.

**Key Characteristics:**
- Warm coral accent carried sparingly — ≤10% of any screen surface
- System fonts for zero-latency rendering, JetBrains Mono for code
- Flat surfaces at rest with subtle shadow lift on hover/focus
- Motion that feels like breathing, not bouncing — ease-out quart curves, ≤300ms
- Light and dark themes with semantic token parity
- WCAG AA contrast throughout (body ≥4.5:1 against background)

## 2. Colors

The palette is built on a single warm coral accent against a cool-neutral gray background family. The warmth comes from the coral, not from tinted backgrounds — the neutrals are near-zero chroma, letting the accent carry all the personality.

The coral operates as a two-tier system: a lighter identity shade for decorative and ambient use, and a deeper accessible shade for interactive elements that must meet WCAG AA contrast against white.

### Primary
- **Coral Identity** (#c17250, oklch(58% 0.13 33)): The brand's visual signature. Used for decorative borders, tinted backgrounds (code blocks, status indicators), and ambient brand presence. Not used for text or interactive elements — its lightness is its charm, but also its accessibility limitation.
- **Coral Interactive** (#a45d40): The workhorse. Used for primary buttons, links, focus rings, and any text-on-light-background where 4.5:1 contrast is required. Darker than the identity shade but within the same hue family — feels like the same color, just applied with purpose.
- **Coral Light** (#f0b8a0): The dark-mode brand accent. A lifted, airy version that reads as warm rather than heavy against dark backgrounds (11:1 contrast on #111111).

### Neutral
- **Near-White** (#f9f9f9): Primary page background in light mode. A true off-white with negligible chroma — explicitly not cream/sand/parchment.
- **Sidebar White** (#fbfbfb): Sidebar and panel backgrounds, slightly lighter than primary.
- **Card White** (#fcfcfc): Elevated card surfaces.
- **Soft Gray** (#efefef): Hover states, selected items, subtle surface distinction.
- **Input Gray** (#e8e8e8): Input fields, code blocks, blockquote backgrounds.
- **Ink** (#202020): Primary body text. Near-black with enough softness to not feel harsh.
- **Muted Ink** (#4a4a4a, #555555): Secondary and tertiary text, placeholder text.
- **Border** (#d8d8d8, #ebebeb): Structural dividers, card borders, input strokes.

### Semantic
- **Emerald** (#10b981): Success, connected status, new-reply indicator.
- **Red** (#e54d2e): Errors, destructive actions, danger states.
- **Amber** (#e8a838): Warnings, thinking/processing states.
- **Blue** (#3b82f6): Info, streaming output indicator, focus glow.
- **Purple** (#8b5cf6): Accent variation for tools, secondary interactive elements.

### Named Rules
**The One Voice Rule.** The warm coral accent is used on ≤10% of any given screen. It appears on primary buttons, focus rings, links, and one or two key states. If you see coral on more than three elements at once, something is wrong.

**The No Tint Rule.** Neutral backgrounds carry near-zero chroma. Warmth belongs to the accent and to typographic rhythm, not to the body background. No cream, no sand, no parchment, no warm-gray ramp.

## 3. Typography

**Display Font:** System UI stack (-apple-system, BlinkMacSystemFont, SF Pro Display, Segoe UI, Roboto, Helvetica Neue, Arial, sans-serif)
**Mono Font:** JetBrains Mono (with SF Mono, Fira Code, Monaco, Consolas, Liberation Mono fallbacks)

**Character:** The system font stack is chosen for instant rendering and native OS feel — no web font latency, no FOUT. JetBrains Mono provides a crisp, distinctive code voice that pairs cleanly with the system sans. All sizes are defined as CSS custom properties with a fixed `rem` scale (product-appropriate, per the register). A single `@media (min-width: 768px)` breakpoint steps sizes up for tablet/desktop — no fluid `clamp()`, no per-component breakpoints. No pixel values are hardcoded in components.

### Hierarchy (mobile / desktop)
- **Display** (600, 24px / 32px, 1.2, -0.01em): Sidebar logo. The largest type on screen — used exactly once.
- **H1** (600, 22px / 28px, 1.3, -0.01em): Markdown H1 in chat messages. With bottom border.
- **H2** (600, 20px / 24px, 1.3, -0.01em): Markdown H2, settings page titles. With bottom border.
- **H3** (600, 18px / 20px, 1.3): Markdown H3, modal titles, panel section headers.
- **H4** (600, 16px, 1.3): Markdown H4, card titles, form section labels. Fixed size (no responsive scaling needed).
- **Body** (400, 14px / 15px, 1.7): Primary reading text in chat messages, settings, notes. Max line length 65–75ch.
- **Secondary** (400, 13px / 14px, 1.7): Labels, descriptions, sidebar items, button text, input fields.
- **Caption** (400, 11px / 12px, 1.5): Timestamps, badges, tooltips, keyboard shortcuts.
- **Mono** (400, 13px / 14px, 1.5): Code blocks and inline code. JetBrains Mono stack.

### Tailwind Compatibility
The Tailwind `fontSize` keys (`xs` through `7xl`) are preserved for backward compatibility but now reference CSS variables. `text-base` resolves to `var(--font-body)`, `text-sm` to `var(--font-secondary)`, etc. All sizes respond to the viewport breakpoint automatically — no component changes required to get responsive typography.

### Named Rules
**The No Decoration Rule.** Text is always solid, single-color. No gradient text (`background-clip: text`), no outline text, no glow-blur text shadows. Emphasis comes from weight and size, never from decorative treatment.

**The Single Weight Rule.** Only two weights appear on screen at once: Regular (400) and Semibold (600). No light (300), no medium (500), no bold (700), no black (900). Restraint in weight hierarchy enforces clarity.

## 4. Elevation

KChat uses a subtle-lift elevation model. All surfaces are flat at rest — no ambient shadow, no permanent raised cards. Depth is a response to state: hover triggers a 1px lift with a diffuse shadow; focus triggers a colored ring. The input area is the only surface that carries a permanent floating card treatment, anchoring the bottom of the chat as the primary action zone.

### Shadow Vocabulary
- **ambient-low** (`0 1px 3px rgba(0,0,0,0.06), 0 4px 12px rgba(0,0,0,0.03)`): Default state for floating cards and dropdowns.
- **ambient-high** (`0 2px 8px rgba(0,0,0,0.1), 0 8px 24px rgba(0,0,0,0.05)`): Hover state for floating cards and interactive surfaces.
- **elevated** (`0 2px 8px rgba(0,0,0,0.12), 0 8px 24px rgba(0,0,0,0.06)`): Modals, dialogs, tooltips. Clearly separated from the page.
- **brand-glow** (`0 2px 8px rgba(193,114,80,0.3)`): Primary button hover — a warm halo that reinforces the coral accent.

### Named Rules
**The Flat-By-Default Rule.** Surfaces carry zero shadow at rest. Shadows appear only as a response to state: hover, focus, or elevation (modal/dropdown). A resting card with a permanent shadow is a design smell in this system.
**The 1px Lift Rule.** Hover lift is exactly 1px (`translateY(-1px)`). More than that feels floaty; less than that feels broken. Combined with the shadow transition, it creates a tactile "response" without calling attention to itself.

## 5. Components

### Buttons
- **Shape:** Rounded-rectangle with 8px corner radius (`--radius-md`). No pill shapes for primary actions.
- **Primary:** Coral background (#a45d40) with white text (#fff). Padding 8px 16px. Font `var(--font-secondary)` / 600 weight. Transition: background-color, transform, box-shadow — all 150ms ease-out.
- **Hover:** Brightness 110% + warm coral glow shadow. No transform on hover (shadow alone conveys the state).
- **Active:** Scale 0.97 — a subtle press-down that feels tactile.
- **Disabled:** Opacity 0.5, no hover effects, cursor not-allowed.
- **Ghost:** Transparent background, muted text. Hover: soft gray background (#efefef) + primary ink text. Active: same 0.97 scale press.

### Inputs
- **Style:** Soft gray background (#e8e8e8), 1px border (#d8d8d8), 8px radius. Font `var(--font-secondary)`.
- **Focus:** Border shifts to warm coral, accompanied by a 3px coral-tinted glow ring (`0 0 0 3px rgba(193,114,80,0.1)`). No outline.
- **Placeholder:** Muted ink (#555555) — must meet 4.5:1 contrast against the input background.
- **Disabled:** Opacity 0.6.

### Cards
- **Corner Style:** 16px radius (`--radius-xl`) for floating cards, 12px (`--radius-lg`) for inset cards.
- **Floating Card:** Semi-transparent white background (85% opacity), 1px light border, backdrop-filter blur 20px. Low ambient shadow at rest, elevated shadow on hover.
- **Solid Card:** Opaque sidebar-white background, 1px border, same shadow strategy. Used when transparency is inappropriate (nested surfaces, settings panels).
- **Border:** Always 1px solid `--border-secondary`. No side-stripe borders, no colored edges.

### Badges
- **Style:** Inline-flex, 2px 8px padding, full-round radius. Font 12px / 500 weight.
- **Color:** Soft gray background (#efefef) with muted text. Semantic color variants exist for status (green for connected, amber for processing).

### Icon Buttons
- **Shape:** 44×44px minimum touch target, 6px internal padding, 8px radius.
- **Default:** Muted ink color, transparent background.
- **Hover:** Soft gray background, secondary ink color.
- **Active:** Scale 0.92 — a more pronounced press than standard buttons.

### Navigation (Sidebar)
- **Style:** Left rail, collapsible, grouped by date. System sans, 14px body, regular weight.
- **Default:** Muted text, transparent background.
- **Hover:** Soft gray background highlight.
- **Active:** Coral-tinted subtle background with warm coral text.
- **Groups:** Date headers with muted 12px label treatment. Expandable/collapsible with chevron indicators.

### Dropdowns
- **Container:** Opaque white (#fff) in light mode, 12px radius, ambient-low shadow, 1px border.
- **Items:** 8px 12px padding, 14px font. Hover: 4% black overlay background.
- **Escape:** Rendered outside overflow containers via fixed positioning or portal pattern.

## 6. Do's and Don'ts

### Do:
- **Do** use the warm coral accent on ≤10% of any screen. One primary button, one focus ring, one active indicator.
- **Do** use system fonts for UI, JetBrains Mono for code — no web font loading.
- **Do** limit weight usage to 400 (body) and 600 (emphasis). No light, no bold, no black.
- **Do** keep shadows dormant at rest and active on hover/focus. The flat-by-default rule applies everywhere.
- **Do** use 44×44px minimum touch targets for interactive icons — the icon button pattern, not raw icons.
- **Do** ensure body text hits ≥4.5:1 contrast against its background in both themes.
- **Do** respect `prefers-reduced-motion: reduce` — all animations collapse to instant transitions.
- **Do** use `text-wrap: balance` on headings, `text-wrap: pretty` on long-form prose.

### Don't:
- **Don't** use gradient text, decorative text shadows, or `background-clip: text` anywhere.
- **Don't** use glassmorphism as the default card treatment. Blurred glass is for the input area only — the exception, not the rule.
- **Don't** use side-stripe borders (`border-left` > 1px as a colored accent). Use a full border, background tint, or leading indicator instead.
- **Don't** nest cards inside cards. One elevation level per surface.
- **Don't** use arbitrary z-index values. Use the semantic scale: dropdown (10) → sticky (20) → modal-backdrop (30) → modal (40) → toast (50) → tooltip (60).
- **Don't** ship tiny uppercase tracked eyebrows above every section. If a section needs a label, use a simple 12px muted heading.
- **Don't** use identical card grids with icon+heading+text repeated endlessly. Vary the layout rhythm.
- **Don't** allow text to overflow its container at any breakpoint. Test heading copy at tablet/mobile widths.
- **Don't** add features, colors, or treatments that weren't asked for. Minimal means minimal.
