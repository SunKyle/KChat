# KChat Prompt 构建系统优化 - 任务清单

## [x] Task 1: 创建输入安全校验组件
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 创建 `InputValidator` 组件，实现输入长度限制（最大4096字符）
  - 实现危险字符过滤（`{{`, `}}`, `<script>`, `;` 等）
  - 集成到消息处理流程中
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `programmatic` TR-1.1: 输入包含 `{{inject}}` 时被正确过滤
  - `programmatic` TR-1.2: 超长输入（>4096字符）被截断并返回错误提示
  - `programmatic` TR-1.3: 正常输入不受影响

## [x] Task 2: 创建敏感信息脱敏组件
- **Priority**: P0
- **Depends On**: Task 1
- **Description**: 
  - 创建 `SensitiveFilter` 组件
  - 实现手机号、身份证号、邮箱、银行卡号识别和脱敏
  - 在日志输出前进行脱敏处理
- **Acceptance Criteria Addressed**: AC-2
- **Test Requirements**:
  - `programmatic` TR-2.1: 手机号 13800138000 被替换为 ***
  - `programmatic` TR-2.2: 邮箱 test@example.com 被替换为 ***
  - `programmatic` TR-2.3: 身份证号 110101199001011234 被替换为 ***

## [x] Task 3: 重构 PromptAssembler 实现智能截断
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 修改 `PromptAssembler.truncateToTokenLimit()` 方法
  - 实现智能截断策略：优先保留 SystemMessage 和当前用户输入
  - 按时间倒序保留历史消息，直到达到 Token 限制
- **Acceptance Criteria Addressed**: AC-3
- **Test Requirements**:
  - `programmatic` TR-3.1: 超出 Token 限制时 SystemMessage 始终保留
  - `programmatic` TR-3.2: 当前用户输入始终保留
  - `programmatic` TR-3.3: 历史消息按时间倒序保留

## [x] Task 4: 创建 TokenEstimator 接口及实现
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 创建 `TokenEstimator` 接口
  - 实现基于 tiktoken 的精确 Token 计算
  - 实现简单字符数估算作为降级方案
- **Acceptance Criteria Addressed**: AC-5
- **Test Requirements**:
  - `programmatic` TR-4.1: Token 计算误差 < 5%
  - `programmatic` TR-4.2: 支持多种编码类型（cl100k_base, gpt2）
  - `programmatic` TR-4.3: 降级方案在 tiktoken 不可用时正常工作

## [x] Task 5: 创建 PromptTemplate 实体和 Repository
- **Priority**: P1
- **Depends On**: None
- **Description**: 
  - 创建 `PromptTemplate` 实体类
  - 创建 `PromptTemplateRepository` 数据访问层
  - 添加数据库迁移脚本
- **Acceptance Criteria Addressed**: AC-4
- **Test Requirements**:
  - `programmatic` TR-5.1: 模板正确保存到数据库
  - `programmatic` TR-5.2: 版本号自动递增
  - `programmatic` TR-5.3: 禁用的模板不被使用

## [x] Task 6: 实现 PromptTemplateService
- **Priority**: P1
- **Depends On**: Task 5
- **Description**: 
  - 创建 `PromptTemplateService` 服务层
  - 实现模板 CRUD 操作
  - 实现模板版本管理和缓存
- **Acceptance Criteria Addressed**: AC-4
- **Test Requirements**:
  - `programmatic` TR-6.1: 创建模板返回正确 ID 和版本
  - `programmatic` TR-6.2: 更新模板版本号递增
  - `programmatic` TR-6.3: 删除模板后无法查询

## [x] Task 7: 实现 PromptTemplateController
- **Priority**: P1
- **Depends On**: Task 6
- **Description**: 
  - 创建 `PromptTemplateController` REST 控制器
  - 实现模板管理 API（CRUD）
  - 添加接口文档注释
- **Acceptance Criteria Addressed**: AC-4
- **Test Requirements**:
  - `programmatic` TR-7.1: POST /api/prompt-templates 创建模板成功
  - `programmatic` TR-7.2: GET /api/prompt-templates 返回所有模板
  - `programmatic` TR-7.3: GET /api/prompt-templates/active 返回启用的模板

## [x] Task 8: 创建监控指标实体和服务
- **Priority**: P1
- **Depends On**: None
- **Description**: 
  - 创建 `PromptMetrics` 实体类
  - 创建 `PromptMetricsService` 服务层
  - 实现指标收集和统计功能
- **Acceptance Criteria Addressed**: AC-6
- **Test Requirements**:
  - `programmatic` TR-8.1: Prompt 构建完成后自动记录指标
  - `programmatic` TR-8.2: 统计接口返回正确的汇总数据
  - `programmatic` TR-8.3: 截断状态正确记录

## [ ] Task 9: 集成 Prometheus 监控
- **Priority**: P2
- **Depends On**: Task 8
- **Description**: 
  - 添加 Spring Boot Actuator 依赖
  - 配置 Prometheus 指标导出
  - 实现自定义监控指标
- **Acceptance Criteria Addressed**: AC-6
- **Test Requirements**:
  - `programmatic` TR-9.1: /actuator/prometheus 返回指标数据
  - `programmatic` TR-9.2: 包含 prompt_build_duration_seconds 指标
  - `programmatic` TR-9.3: 包含 prompt_token_count 指标

## [x] Task 10: 更新 OllamaClient 集成安全过滤
- **Priority**: P0
- **Depends On**: Task 1, Task 2
- **Description**: 
  - 在 `OllamaClient.buildPrompt()` 中集成安全过滤
  - 确保所有用户输入经过校验和脱敏
  - 添加日志脱敏处理
- **Acceptance Criteria Addressed**: AC-1, AC-2
- **Test Requirements**:
  - `programmatic` TR-10.1: 危险字符在发送到模型前被过滤
  - `programmatic` TR-10.2: 敏感信息在日志中被脱敏
  - `programmatic` TR-10.3: 正常消息不受影响

## [ ] Task 11: 添加单元测试
- **Priority**: P1
- **Depends On**: Task 1-10
- **Description**: 
  - 为所有新组件编写单元测试
  - 覆盖边界条件和异常场景
  - 确保测试覆盖率 > 80%
- **Acceptance Criteria Addressed**: 所有 AC
- **Test Requirements**:
  - `programmatic` TR-11.1: 单元测试全部通过
  - `programmatic` TR-11.2: 代码覆盖率 > 80%

## [ ] Task 12: 添加集成测试
- **Priority**: P1
- **Depends On**: Task 1-10
- **Description**: 
  - 编写端到端集成测试
  - 测试完整的 Prompt 构建流程
  - 验证安全过滤、Token 截断、模板加载等功能
- **Acceptance Criteria Addressed**: 所有 AC
- **Test Requirements**:
  - `programmatic` TR-12.1: 集成测试全部通过
  - `programmatic` TR-12.2: 完整流程测试覆盖

## [x] Task 13: 更新配置文件
- **Priority**: P1
- **Depends On**: Task 4, Task 6, Task 9
- **Description**: 
  - 更新 application.yml 添加新配置项
  - 配置 Token 限制、模板缓存、监控开关等
- **Acceptance Criteria Addressed**: 所有 AC
- **Test Requirements**:
  - `programmatic` TR-13.1: 配置项正确加载
  - `programmatic` TR-13.2: 默认配置值生效

## [ ] Task 14: 更新 API 文档
- **Priority**: P2
- **Depends On**: Task 7
- **Description**: 
  - 更新 API 文档，添加模板管理接口说明
  - 添加监控指标接口说明
- **Acceptance Criteria Addressed**: AC-4, AC-6
- **Test Requirements**:
  - `human-judgment` TR-14.1: 文档完整清晰
  - `human-judgment` TR-14.2: 接口示例正确

## [ ] Task 15: 更新架构文档
- **Priority**: P2
- **Depends On**: 所有任务
- **Description**: 
  - 更新后端架构文档
  - 说明新组件的职责和交互关系
- **Acceptance Criteria Addressed**: 所有 AC
- **Test Requirements**:
  - `human-judgment` TR-15.1: 架构图清晰准确
  - `human-judgment` TR-15.2: 组件职责说明清晰