export const CATEGORY_STYLES: Record<string, { bg: string; text: string; border: string }> = {
  工作: {
    bg: 'bg-[var(--brand-info)]/10',
    text: 'text-[var(--brand-info)]',
    border: 'border-[var(--brand-info)]/20',
  },
  学习: {
    bg: 'bg-[var(--brand-success)]/10',
    text: 'text-[var(--brand-success)]',
    border: 'border-[var(--brand-success)]/20',
  },
  生活: {
    bg: 'bg-[var(--accent-rose)]/10',
    text: 'text-[var(--accent-rose)]',
    border: 'border-[var(--accent-rose)]/20',
  },
  默认: {
    bg: 'bg-[var(--bg-hover)]',
    text: 'text-[var(--text-secondary)]',
    border: 'border-[var(--bg-hover)]',
  },
}

export function getCategoryStyles(category: string) {
  return CATEGORY_STYLES[category] || CATEGORY_STYLES['默认']
}
