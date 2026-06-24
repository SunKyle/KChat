# KChat 字体体系优化 - 产品需求文档

## Overview
- **Summary**: 对 KChat 前端项目的字体体系进行全面优化，包括完善字体引入、修正字重定义、添加字体预加载机制以及修复硬编码字号问题。
- **Purpose**: 解决当前字体体系存在的视觉一致性问题、性能问题和代码规范问题，提升用户体验和代码可维护性。
- **Target Users**: 所有 KChat 用户及开发维护人员

## Goals
- 完善字体引入，确保 JetBrains Mono 代码字体正确加载
- 修正字重命名不一致问题，符合行业标准
- 添加字体预加载，优化首屏性能
- 修复硬编码字号，统一使用设计令牌

## Non-Goals (Out of Scope)
- 不改变现有设计风格和视觉体验
- 不增加新的字体家族
- 不修改字体大小的响应式断点

## Background & Context
项目当前使用 Google Fonts CDN 引入字体，但存在以下问题：
1. JetBrains Mono 字体被引用但未实际引入
2. 字重类名与实际值不匹配（medium/bold 均为 600）
3. 缺少字体预加载机制
4. 存在硬编码字号（如 text-[10px]）

## Functional Requirements
- **FR-1**: 更新 Google Fonts 引入，添加 JetBrains Mono 字体
- **FR-2**: 修正字重 CSS 类定义，使其符合行业标准
- **FR-3**: 在 index.html 中添加字体预加载配置
- **FR-4**: 修复硬编码字号，替换为设计令牌

## Non-Functional Requirements
- **NFR-1**: 优化后页面首屏加载时间不增加
- **NFR-2**: 代码变更不影响现有功能和布局
- **NFR-3**: 保持与现有设计系统的兼容性

## Constraints
- **Technical**: 基于 React + Tailwind CSS 框架
- **Business**: 不影响现有用户体验
- **Dependencies**: 依赖 Google Fonts CDN

## Assumptions
- 用户浏览器支持现代 CSS 特性
- Google Fonts 服务正常可用

## Acceptance Criteria

### AC-1: 字体引入完善
- **Given**: 当前项目使用 Google Fonts CDN
- **When**: 优化后检查 index.css 的 @import 语句
- **Then**: 应包含 Open Sans、Righteous 和 JetBrains Mono 三种字体
- **Verification**: `programmatic`

### AC-2: 字重定义修正
- **Given**: 当前存在 .font-weight-medium、.font-weight-semibold、.font-weight-bold 类
- **When**: 优化后检查这些类的定义
- **Then**: medium 应为 500，semibold 应为 600，bold 应为 700
- **Verification**: `programmatic`

### AC-3: 字体预加载添加
- **Given**: 当前 index.html 无字体预加载配置
- **When**: 优化后检查 index.html
- **Then**: 应包含 fonts.googleapis.com 和 fonts.gstatic.com 的 preconnect 链接
- **Verification**: `programmatic`

### AC-4: 硬编码字号修复
- **Given**: NoteTodoPanel 中存在 text-[10px] 硬编码
- **When**: 优化后检查相关组件
- **Then**: 所有硬编码字号应替换为对应的设计令牌
- **Verification**: `human-judgment`

## Open Questions
- [ ] 是否需要添加 font-display: swap 优化字体加载体验
- [ ] 是否需要为其他关键字体添加 preload 链接