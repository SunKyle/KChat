import { tokens } from './tokens'

export type TypographyKey = keyof typeof tokens.font.typography
export type ComponentTypographyKey = keyof typeof tokens.font.components

export const typography = {
  display: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.display.fontSize,
    lineHeight: tokens.font.typography.display.lineHeight,
    fontWeight: tokens.font.typography.display.fontWeight,
  }),
  h1: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.h1.fontSize,
    lineHeight: tokens.font.typography.h1.lineHeight,
    fontWeight: tokens.font.typography.h1.fontWeight,
  }),
  h2: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.h2.fontSize,
    lineHeight: tokens.font.typography.h2.lineHeight,
    fontWeight: tokens.font.typography.h2.fontWeight,
  }),
  h3: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.h3.fontSize,
    lineHeight: tokens.font.typography.h3.lineHeight,
    fontWeight: tokens.font.typography.h3.fontWeight,
  }),
  title: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.title.fontSize,
    lineHeight: tokens.font.typography.title.lineHeight,
    fontWeight: tokens.font.typography.title.fontWeight,
  }),
  bodyL: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.bodyL.fontSize,
    lineHeight: tokens.font.typography.bodyL.lineHeight,
    fontWeight: tokens.font.typography.bodyL.fontWeight,
  }),
  bodyM: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.bodyM.fontSize,
    lineHeight: tokens.font.typography.bodyM.lineHeight,
    fontWeight: tokens.font.typography.bodyM.fontWeight,
  }),
  secondary: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.secondary.fontSize,
    lineHeight: tokens.font.typography.secondary.lineHeight,
    fontWeight: tokens.font.typography.secondary.fontWeight,
  }),
  caption: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.caption.fontSize,
    lineHeight: tokens.font.typography.caption.lineHeight,
    fontWeight: tokens.font.typography.caption.fontWeight,
  }),
  tiny: (): React.CSSProperties => ({
    fontSize: tokens.font.typography.tiny.fontSize,
    lineHeight: tokens.font.typography.tiny.lineHeight,
    fontWeight: tokens.font.typography.tiny.fontWeight,
  }),
}

export const componentTypography = {
  logo: (): React.CSSProperties => ({
    fontSize: tokens.font.components.logo.fontSize,
    fontWeight: tokens.font.components.logo.fontWeight,
  }),
  tagline: (): React.CSSProperties => ({
    fontSize: tokens.font.components.tagline.fontSize,
    fontWeight: tokens.font.components.tagline.fontWeight,
    letterSpacing: tokens.font.components.tagline.letterSpacing,
  }),
  groupTitle: (): React.CSSProperties => ({
    fontSize: tokens.font.components.groupTitle.fontSize,
    fontWeight: tokens.font.components.groupTitle.fontWeight,
  }),
  conversationName: (): React.CSSProperties => ({
    fontSize: tokens.font.components.conversationName.fontSize,
    fontWeight: tokens.font.components.conversationName.fontWeight,
  }),
  currentTitle: (): React.CSSProperties => ({
    fontSize: tokens.font.components.currentTitle.fontSize,
    fontWeight: tokens.font.components.currentTitle.fontWeight,
  }),
  modelName: (): React.CSSProperties => ({
    fontSize: tokens.font.components.modelName.fontSize,
    fontWeight: tokens.font.components.modelName.fontWeight,
  }),
  statusTag: (): React.CSSProperties => ({
    fontSize: tokens.font.components.statusTag.fontSize,
    fontWeight: tokens.font.components.statusTag.fontWeight,
  }),
  aiMessage: (): React.CSSProperties => ({
    fontSize: tokens.font.components.aiMessage.fontSize,
    lineHeight: tokens.font.components.aiMessage.lineHeight,
    fontWeight: tokens.font.components.aiMessage.fontWeight,
  }),
  userMessage: (): React.CSSProperties => ({
    fontSize: tokens.font.components.userMessage.fontSize,
    lineHeight: tokens.font.components.userMessage.lineHeight,
    fontWeight: tokens.font.components.userMessage.fontWeight,
  }),
  codeBlock: (): React.CSSProperties => ({
    fontSize: tokens.font.components.codeBlock.fontSize,
    lineHeight: tokens.font.components.codeBlock.lineHeight,
    fontWeight: tokens.font.components.codeBlock.fontWeight,
    fontFamily: tokens.font.components.codeBlock.fontFamily,
  }),
  inputText: (): React.CSSProperties => ({
    fontSize: tokens.font.components.inputText.fontSize,
    lineHeight: tokens.font.components.inputText.lineHeight,
    fontWeight: tokens.font.components.inputText.fontWeight,
  }),
  placeholder: (): React.CSSProperties => ({
    fontSize: tokens.font.components.placeholder.fontSize,
    lineHeight: tokens.font.components.placeholder.lineHeight,
    fontWeight: tokens.font.components.placeholder.fontWeight,
  }),
  helperText: (): React.CSSProperties => ({
    fontSize: tokens.font.components.helperText.fontSize,
    lineHeight: tokens.font.components.helperText.lineHeight,
    fontWeight: tokens.font.components.helperText.fontWeight,
  }),
}

export const fontFamilies = {
  sans: tokens.font.family.sans,
  mono: tokens.font.family.mono,
}

export const fontWeights = {
  normal: tokens.font.weights.normal,
  semibold: tokens.font.weights.semibold,
}
