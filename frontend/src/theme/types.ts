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
      primary: '#111111',
      sidebar: '#18181b',
      card: '#191919',
      hover: '#222222',
      input: '#2a2a2a',
      overlay: 'rgba(0, 0, 0, 0.6)',
    },
    text: {
      primary: '#eeeeee',
      secondary: '#b4b4b4',
      muted: '#888888',
      placeholder: '#666666',
    },
    border: {
      primary: '#27272a',
      secondary: '#201e18',
    },
    brand: {
      primary: '#f0b8a0',
      success: '#34d399',
      danger: '#e54d2e',
      warning: '#fbbf24',
      info: '#60a5fa',
    },
    accent: {
      sky: '#f0b8a0',
      emerald: '#34d399',
      amber: '#e8a838',
      rose: '#e54d2e',
      purple: '#a78bfa',
      orange: '#f0b8a0',
    },
  },
}

export const lightTheme: ThemeConfig = {
  name: 'light',
  label: '明亮主题',
  colors: {
    bg: {
      primary: '#f9f9f9',
      sidebar: '#fbfbfb',
      card: '#fcfcfc',
      hover: '#efefef',
      input: '#e8e8e8',
      overlay: 'rgba(0, 0, 0, 0.4)',
    },
    text: {
      primary: '#202020',
      secondary: '#4a4a4a',
      muted: '#555555',
      placeholder: '#555555',
    },
    border: {
      primary: '#d8d8d8',
      secondary: '#ebebeb',
    },
    brand: {
      primary: '#a45d40',
      success: '#10b981',
      danger: '#e54d2e',
      warning: '#e8a838',
      info: '#3b82f6',
    },
    accent: {
      sky: '#a45d40',
      emerald: '#10b981',
      amber: '#df9b3a',
      rose: '#e54d2e',
      purple: '#8b5cf6',
      orange: '#b87050',
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
