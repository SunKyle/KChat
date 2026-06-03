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

export default tseslint.config(
  { ignores: ['dist'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'custom-rules': {
        rules: { 'no-hardcoded-fontsize': noHardcodedFontSize },
      },
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      'custom-rules/no-hardcoded-fontsize': 'error',
    },
  },
)
