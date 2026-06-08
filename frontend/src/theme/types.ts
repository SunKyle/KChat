export type ThemeName = 'dark' | 'light'

export interface ThemeConfig {
  name: ThemeName
  label: string
  colors: {
    bg: {
      primary: string
      sidebar: string
      card: string
      hover: string
      input: string
      overlay: string
    }
    text: {
      primary: string
      secondary: string
      muted: string
      placeholder: string
    }
    border: {
      primary: string
      secondary: string
    }
    brand: {
      primary: string
      success: string
      danger: string
      warning: string
      info: string
    }
    accent: {
      sky: string
      emerald: string
      amber: string
      rose: string
      purple: string
      orange: string
    }
  }
}

export const darkTheme: ThemeConfig = {
  name: 'dark',
  label: '深色主题',
  colors: {
    bg: {
      primary: '#0f172a',
      sidebar: '#2d3a4f',
      card: '#1e293b',
      hover: '#334155',
      input: 'rgba(255, 255, 255, 0.2)',
      overlay: 'rgba(0, 0, 0, 0.5)',
    },
    text: {
      primary: '#e5e7eb',
      secondary: '#94a3b8',
      muted: '#64748b',
      placeholder: '#64748b',
    },
    border: {
      primary: 'rgba(255, 255, 255, 0.1)',
      secondary: 'rgba(255, 255, 255, 0.05)',
    },
    brand: {
      primary: '#0ea5e9',
      success: '#10b981',
      danger: '#ef4444',
      warning: '#f59e0b',
      info: '#3b82f6',
    },
    accent: {
      sky: '#38bdf8',
      emerald: '#34d399',
      amber: '#fbbf24',
      rose: '#fb7185',
      purple: '#a78bfa',
      orange: '#fb923c',
    },
  },
}

export const lightTheme: ThemeConfig = {
  name: 'light',
  label: '明亮主题',
  colors: {
    bg: {
      primary: '#ffffff',
      sidebar: '#f1f5f9',
      card: '#f8fafc',
      hover: '#e2e8f0',
      input: 'rgba(0, 0, 0, 0.05)',
      overlay: 'rgba(0, 0, 0, 0.5)',
    },
    text: {
      primary: '#0f172a',
      secondary: '#475569',
      muted: '#64748b',
      placeholder: '#94a3b8',
    },
    border: {
      primary: 'rgba(0, 0, 0, 0.12)',
      secondary: 'rgba(0, 0, 0, 0.06)',
    },
    brand: {
      primary: '#0ea5e9',
      success: '#10b981',
      danger: '#ef4444',
      warning: '#f59e0b',
      info: '#3b82f6',
    },
    accent: {
      sky: '#0ea5e9',
      emerald: '#10b981',
      amber: '#f59e0b',
      rose: '#ef4444',
      purple: '#8b5cf6',
      orange: '#f97316',
    },
  },
}

export const themes: Record<ThemeName, ThemeConfig> = {
  dark: darkTheme,
  light: lightTheme,
}

export const getThemeColors = (theme: ThemeName): Record<string, string> => {
  const config = themes[theme]
  const colors: Record<string, string> = {}

  Object.entries(config.colors).forEach(([category, values]) => {
    Object.entries(values).forEach(([key, value]) => {
      colors[`--${category}-${key}`] = value
    })
  })

  return colors
}
