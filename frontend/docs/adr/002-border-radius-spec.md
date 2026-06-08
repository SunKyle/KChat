# Border Radius Specification

## Overview

This document defines the border radius design system for KChat, establishing consistent rounding across all UI components.

## Design Principles

1. **Consistency**: Use consistent radii for similar component types
2. **Hierarchy**: Larger radii for larger elements, smaller radii for smaller elements
3. **Accessibility**: Ensure rounded corners don't compromise usability

## Radius Scale

| Size | Value | Usage | Examples |
|------|-------|-------|----------|
| `rounded-xs` | 4px | Tiny elements, indicators | Status dots, small badges |
| `rounded-sm` | 6px | Small interactive elements | Small buttons, text inputs |
| `rounded-md` | 8px | Medium interactive elements | Standard buttons, dropdowns |
| `rounded-lg` | 12px | Cards, containers | Regular cards, input groups |
| `rounded-xl` | 16px | Large containers | Modal dialogs, panels |
| `rounded-2xl` | 20px | Extra large containers | Welcome screens, hero sections |
| `rounded-full` | 9999px | Circular elements | Avatars, pill buttons |

## Component Guidelines

### Buttons
- Small buttons: `rounded-md`
- Standard buttons: `rounded-lg`
- Pill buttons: `rounded-full`

### Cards
- Regular cards: `rounded-lg`
- Elevated cards: `rounded-xl`
- Modal cards: `rounded-xl`

### Inputs
- Text inputs: `rounded-lg`
- Search fields: `rounded-lg`
- Textareas: `rounded-lg`

### Avatars & Icons
- User avatars: `rounded-full`
- Icon buttons: `rounded-md`
- Status indicators: `rounded-full`

### Containers
- Sidebar: `rounded-xl`
- Header: `rounded-xl`
- Input area: `rounded-xl`

## Implementation

Use CSS variables and utility classes defined in `src/index.css`:

```css
--radius-sm: 6px;
--radius-md: 8px;
--radius-lg: 12px;
--radius-xl: 16px;
--radius-2xl: 20px;
--radius-full: 9999px;
```

## Migration Notes

- Replace Tailwind `rounded-*` classes with CSS utility classes
- Ensure all components follow the guidelines above
- Review and update any hardcoded border-radius values

## Review Checklist

- [ ] All buttons use consistent rounding
- [ ] All cards use consistent rounding
- [ ] All inputs use consistent rounding
- [ ] No hardcoded pixel values
- [ ] Circular elements use `rounded-full`