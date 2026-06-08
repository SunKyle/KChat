import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

const noHardcodedFontSize = {
  create(context) {
    return {
      Property(node) {
        if (
          node.key &&
          node.key.type === 'Identifier' &&
          node.key.name === 'fontSize' &&
          node.value &&
          node.value.type === 'Literal' &&
          typeof node.value.value === 'string'
        ) {
          const value = node.value.value
          if (/^\d+(px|rem|em)$/.test(value) && !value.startsWith('var(--')) {
            context.report({
              node,
              message: `禁止硬编码字体大小，请使用CSS变量（如 var(--font-body-base)）或Tailwind类名`,
            })
          }
        }
      },
      Literal(node) {
        if (
          typeof node.value === 'string' &&
          /text-\[\d+(px|rem|em)\]/.test(node.value)
        ) {
          context.report({
            node,
            message: `禁止使用硬编码字体大小的Tailwind类名（如 text-[14px]），请使用预定义的text-sm/base/lg等`,
          })
        }
      },
    }
  },
}

const noHardcodedColor = {
  create(context) {
    const colorPropertyNames = [
      'color',
      'background',
      'backgroundColor',
      'borderColor',
      'border',
      'outlineColor',
      'boxShadow',
      'textShadow',
      'fill',
      'stroke',
    ]

    return {
      Property(node) {
        if (
          node.key &&
          node.key.type === 'Identifier' &&
          colorPropertyNames.includes(node.key.name) &&
          node.value &&
          node.value.type === 'Literal' &&
          typeof node.value.value === 'string'
        ) {
          const value = node.value.value
          const colorRegex = /(#[0-9A-Fa-f]{3,8}|rgb\([^)]+\)|rgba\([^)]+\))/
          if (colorRegex.test(value) && !value.startsWith('var(--')) {
            context.report({
              node,
              message: `禁止硬编码颜色值，请使用CSS变量（如 var(--bg-card)）或Tailwind类名`,
            })
          }
        }
      },
      Literal(node) {
        if (typeof node.value === 'string') {
          const hardcodedColorClass =
            /(bg|text|border|fill|stroke)-\[(#[0-9A-Fa-f]{3,8}|rgb\([^)]+\)|rgba\([^)]+\))\]/
          if (hardcodedColorClass.test(node.value)) {
            context.report({
              node,
              message: `禁止使用硬编码颜色的Tailwind类名（如 bg-[#fff]），请使用预定义的主题色`,
            })
          }
        }
      },
    }
  },
}

export default tseslint.config(
  { ignores: ['dist', 'node_modules', '.git'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.strict],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'custom-rules': {
        rules: {
          'no-hardcoded-fontsize': noHardcodedFontSize,
          'no-hardcoded-color': noHardcodedColor,
        },
      },
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_' },
      ],
      '@typescript-eslint/no-dynamic-delete': 'off',
      '@typescript-eslint/no-non-null-assertion': 'warn',
      'no-console': ['warn', { allow: ['warn', 'error', 'log'] }],
      'react-hooks/exhaustive-deps': 'warn',
      'custom-rules/no-hardcoded-fontsize': 'error',
      'custom-rules/no-hardcoded-color': 'error',
    },
  },
  // 为配置文件设置特殊规则
  {
    files: ['src/theme/tokens.ts'],
    rules: {
      'custom-rules/no-hardcoded-fontsize': 'off',
    },
  },
)
