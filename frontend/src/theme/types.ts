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
    }
  }
}

export const darkTheme: ThemeConfig = {
  name: 'dark',
  label: '深色主题',
  colors: {
    bg: {
      primary: '#0f172a',
      sidebar: '#111827',
      card: '#1e293b',
      hover: '#334155',
      input: 'rgba(255, 255, 255, 0.03)',
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
    },
  },
}

export const lightTheme: ThemeConfig = {
  name: 'light',
  label: '明亮主题',
  colors: {
    bg: {
      primary: '#ffffff',
      sidebar: '#f8fafc',
      card: '#f1f5f9',
      hover: '#e2e8f0',
      input: 'rgba(0, 0, 0, 0.03)',
    },
    text: {
      primary: '#1e293b',
      secondary: '#64748b',
      muted: '#94a3b8',
      placeholder: '#94a3b8',
    },
    border: {
      primary: 'rgba(0, 0, 0, 0.1)',
      secondary: 'rgba(0, 0, 0, 0.05)',
    },
    brand: {
      primary: '#0ea5e9',
      success: '#10b981',
      danger: '#ef4444',
      warning: '#f59e0b',
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