/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    fontFamily: {
      sans: ['-apple-system', 'BlinkMacSystemFont', 'SF Pro Display', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
      mono: ['SF Mono', 'Fira Code', 'Monaco', 'Consolas', 'Liberation Mono', 'monospace'],
    },
    fontSize: {
      xs: ['11px', { lineHeight: '1.4' }],
      sm: ['12px', { lineHeight: '1.5' }],
      base: ['14px', { lineHeight: '1.5' }],
      lg: ['15px', { lineHeight: '1.5' }],
      xl: ['16px', { lineHeight: '1.5' }],
      '2xl': ['18px', { lineHeight: '1.5' }],
      '3xl': ['20px', { lineHeight: '1.4' }],
      '4xl': ['24px', { lineHeight: '1.4' }],
    },
    extend: {
      colors: {
        primary: {
          50: '#f0f9ff',
          100: '#e0f2fe',
          200: '#bae6fd',
          300: '#7dd3fc',
          400: '#38bdf8',
          500: '#0ea5e9',
          600: '#0284c7',
          700: '#0369a1',
          800: '#075985',
          900: '#0c4a6e',
        },
        dark: {
          50: '#f8fafc',
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
          600: '#475569',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
          950: '#020617',
        }
      },
      lineHeight: {
        tight: '1.4',
        normal: '1.5',
        relaxed: '1.6',
      },
    },
  },
  plugins: [],
}
