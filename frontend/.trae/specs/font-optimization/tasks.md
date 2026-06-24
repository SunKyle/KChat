# KChat 字体体系优化 - 实现计划

## [x] Task 1: 更新字体引入配置
- **Priority**: high
- **Depends On**: None
- **Description**: 
  - 更新 src/index.css 中的 Google Fonts @import 语句
  - 添加 JetBrains Mono 字体（400, 500 字重）
  - 为 Open Sans 添加 500、700 字重
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `programmatic` TR-1.1: 检查 index.css 第 1 行 @import 包含 JetBrains Mono
  - `programmatic` TR-1.2: 检查 Open Sans 包含 400,500,600,700 字重

## [x] Task 2: 修正字重 CSS 类定义
- **Priority**: high
- **Depends On**: None
- **Description**: 
  - 修改 .font-weight-medium 为 font-weight: 500
  - 修改 .font-weight-bold 为 font-weight: 700
  - 保持 .font-weight-semibold 为 font-weight: 600
- **Acceptance Criteria Addressed**: AC-2
- **Test Requirements**:
  - `programmatic` TR-2.1: .font-weight-medium 应为 500
  - `programmatic` TR-2.2: .font-weight-semibold 应为 600
  - `programmatic` TR-2.3: .font-weight-bold 应为 700

## [x] Task 3: 添加字体预加载配置
- **Priority**: medium
- **Depends On**: None
- **Description**: 
  - 在 index.html 中添加 fonts.googleapis.com 的 preconnect 链接
  - 添加 fonts.gstatic.com 的 preconnect 链接（带 crossorigin 属性）
- **Acceptance Criteria Addressed**: AC-3
- **Test Requirements**:
  - `programmatic` TR-3.1: index.html 包含 fonts.googleapis.com 的 preconnect
  - `programmatic` TR-3.2: index.html 包含 fonts.gstatic.com 的 preconnect 且带 crossorigin

## [x] Task 4: 修复硬编码字号问题
- **Priority**: medium
- **Depends On**: None
- **Description**: 
  - 查找 NoteTodoPanel.tsx 中的 text-[10px] 硬编码
  - 替换为合适的设计令牌类名（如 font-caption 或 text-xs）
- **Acceptance Criteria Addressed**: AC-4
- **Test Requirements**:
  - `human-judgment` TR-4.1: 检查 NoteTodoPanel.tsx 无硬编码字号
  - `human-judgment` TR-4.2: 检查其他组件是否存在类似硬编码

## [ ] Task 5: 验证优化效果
- **Priority**: high
- **Depends On**: Task 1, Task 2, Task 3, Task 4
- **Description**: 
  - 运行项目构建确保无错误
  - 启动开发服务器验证字体加载正常
  - 检查页面布局和样式无异常
- **Acceptance Criteria Addressed**: 所有 AC
- **Test Requirements**:
  - `programmatic` TR-5.1: npm run build 无错误
  - `human-judgment` TR-5.2: 页面字体渲染正常
  - `human-judgment` TR-5.3: 代码块字体显示正确