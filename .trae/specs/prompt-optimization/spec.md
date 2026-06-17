# KChat Prompt 构建系统优化 - 产品需求文档

## Overview
- **Summary**: 对 KChat 后端 Prompt 构建系统进行全面优化，包括安全加固、智能截断、模板配置化、Token 精确计算和监控指标等核心功能升级。
- **Purpose**: 解决当前系统存在的安全风险（Prompt 注入、敏感信息泄露）、功能缺陷（Token 截断策略不完善）和可维护性问题（模板硬编码、缺乏监控）。
- **Target Users**: KChat 后端开发团队、运维团队、安全团队

## Goals
- [ ] 消除 Prompt 注入攻击风险
- [ ] 实现智能 Token 截断（保留关键信息）
- [ ] 支持 Prompt 模板配置化管理
- [ ] 实现精确 Token 计算
- [ ] 建立完善的监控指标体系

## Non-Goals (Out of Scope)
- [ ] 前端界面改造
- [ ] 用户认证系统升级
- [ ] 数据库迁移工具开发
- [ ] 多租户架构改造

## Background & Context
当前 Prompt 构建系统存在以下主要问题：
1. **安全问题**：用户输入直接拼接到 Prompt 中，存在注入攻击风险
2. **功能缺陷**：Token 截断策略不完善，可能丢失关键的 SystemMessage
3. **可维护性差**：Prompt 模板硬编码，无法动态调整
4. **性能问题**：Token 估算精度低，基于简单字符数估算
5. **缺乏监控**：没有关键指标的监控和日志记录

## Functional Requirements
- **FR-1**: 实现输入安全过滤，防止 Prompt 注入攻击
- **FR-2**: 实现敏感信息自动脱敏（手机号、身份证号、邮箱、银行卡号）
- **FR-3**: 实现智能 Token 截断，优先保留 SystemMessage 和当前用户输入
- **FR-4**: 支持 Prompt 模板配置化管理（增删改查、版本控制）
- **FR-5**: 实现精确 Token 计算，支持多模型适配
- **FR-6**: 建立 Prompt 构建监控指标体系
- **FR-7**: 支持多语言响应指令注入

## Non-Functional Requirements
- **NFR-1**: 用户输入处理延迟 < 50ms
- **NFR-2**: Token 截断准确率 > 95%
- **NFR-3**: 敏感信息识别准确率 > 99%
- **NFR-4**: 系统可用性 > 99.9%
- **NFR-5**: 日志脱敏率 100%

## Constraints
- **Technical**: Java 21, Spring Boot 3.2.x, Maven
- **Business**: 需要兼容现有 API 接口，不影响前端调用
- **Dependencies**: 引入 tiktoken-java 进行精确 Token 计算

## Assumptions
- [ ] 数据库连接正常可用
- [ ] 配置中心（如有）可正常访问
- [ ] 现有代码结构保持稳定

## Acceptance Criteria

### AC-1: 输入安全过滤
- **Given**: 用户输入包含潜在注入字符（如 `{{`, `}}`, `<script>`）
- **When**: 调用消息发送接口
- **Then**: 系统自动过滤危险字符，返回安全的处理结果
- **Verification**: `programmatic`

### AC-2: 敏感信息脱敏
- **Given**: 用户输入包含手机号（13800138000）、身份证号或邮箱
- **When**: 调用消息发送接口
- **Then**: 敏感信息被替换为 `***`，日志中不包含原始敏感数据
- **Verification**: `programmatic`

### AC-3: 智能 Token 截断
- **Given**: 对话历史超过 Token 限制（8192）
- **When**: 构建 Prompt 时触发截断
- **Then**: SystemMessage 和当前用户输入始终保留，只截断早期历史消息
- **Verification**: `programmatic`

### AC-4: Prompt 模板管理
- **Given**: 管理员调用模板管理 API
- **When**: 执行创建/更新/删除操作
- **Then**: 模板正确保存到数据库，版本号自动递增
- **Verification**: `programmatic`

### AC-5: 精确 Token 计算
- **Given**: 输入一段文本
- **When**: 调用 Token 估算接口
- **Then**: 返回的 Token 数量与实际模型计算误差 < 5%
- **Verification**: `programmatic`

### AC-6: 监控指标收集
- **Given**: 系统运行中处理用户请求
- **When**: 完成 Prompt 构建
- **Then**: 自动记录 Token 数量、构建耗时、截断状态等指标
- **Verification**: `programmatic`

### AC-7: 多语言支持
- **Given**: 用户设置语言偏好为 "ja"（日语）
- **When**: 发送消息
- **Then**: System Prompt 中注入 "请使用日本語回复。"
- **Verification**: `programmatic`

## Open Questions
- [ ] 是否需要支持模板的灰度发布？
- [ ] Token 限制是否需要根据不同模型动态调整？
- [ ] 是否需要添加用户级别的记忆隔离？