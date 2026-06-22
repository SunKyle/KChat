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
      primary: '#000000',
      sidebar: '#17181c',
      card: '#17181c',
      hover: '#181818',
      input: '#22303c',
      overlay: 'rgba(0, 0, 0, 0.6)',
    },
    text: {
      primary: '#e7e9ea',
      secondary: '#72767a',
      muted: '#72767a',
      placeholder: '#72767a',
    },
    border: {
      primary: '#242628',
      secondary: '#38444d',
    },
    brand: {
      primary: '#1c9cf0',
      success: '#00b87a',
      danger: '#f4212e',
      warning: '#f7b928',
      info: '#1c9cf0',
    },
    accent: {
      sky: '#1c9cf0',
      emerald: '#00b87a',
      amber: '#f7b928',
      rose: '#f4212e',
      purple: '#a78bfa',
      orange: '#f7b928',
    },
  },
}

export const lightTheme: ThemeConfig = {
  name: 'light',
  label: '明亮主题',
  colors: {
    bg: {
      primary: '#ffffff',
      sidebar: '#f7f8f8',
      card: '#f7f8f8',
      hover: '#E5E5E6',
      input: '#f7f9fa',
      overlay: 'rgba(0, 0, 0, 0.4)',
    },
    text: {
      primary: '#0f1419',
      secondary: '#536471',
      muted: '#536471',
      placeholder: '#536471',
    },
    border: {
      primary: '#e1eaef',
      secondary: '#eff3f4',
    },
    brand: {
      primary: '#1e9df1',
      success: '#00b87a',
      danger: '#f4212e',
      warning: '#f7b928',
      info: '#1e9df1',
    },
    accent: {
      sky: '#1e9df1',
      emerald: '#00b87a',
      amber: '#f7b928',
      rose: '#f4212e',
      purple: '#8b5cf6',
      orange: '#f7b928',
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
