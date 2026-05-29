# KChat 后端项目重构计划

## 一、问题概述

基于系统分析，当前项目存在以下核心问题：

| 问题类型 | 具体问题 | 风险等级 |
|----------|----------|----------|
| 职责混乱 | `MemoryService`已废弃但仍被引用 | 高 |
| 代码重复 | `LongTermMemoryManager`与`LongTermMemoryService`职责重叠 | 高 |
| 冗余层级 | `LongTermMemoryFacadeService`仅做转发，无实际业务逻辑 | 高 |
| 类过大 | `StreamingService`、`OllamaClient`、`LongTermMemoryService`代码行数超过200行 | 中 |
| 耦合严重 | `StreamingService`依赖7个组件 | 高 |

---

## 二、修复目标

1. **消除代码重复**：合并`LongTermMemoryManager`和`LongTermMemoryService`
2. **清理废弃代码**：删除`MemoryService`，更新引用
3. **简化层级**：评估并移除`LongTermMemoryFacadeService`
4. **降低耦合**：优化Service之间的依赖关系

---

## 三、实现步骤

### 步骤1：合并长期记忆管理（P0）

**目标**：将`LongTermMemoryManager`的职责整合到`LongTermMemoryService`

**操作**：
1. 读取`LongTermMemoryManager.java`和`LongTermMemoryService.java`
2. 将`LongTermMemoryManager`的`store()`、`retrieve()`方法迁移到`LongTermMemoryService`
3. 删除`LongTermMemoryManager.java`
4. 更新所有引用`LongTermMemoryManager`的文件

**涉及文件**：
- `memory/LongTermMemoryManager.java`（删除）
- `service/LongTermMemoryService.java`（修改）
- `service/LongTermMemoryFacadeService.java`（修改）

### 步骤2：清理废弃的MemoryService（P0）

**目标**：移除`@Deprecated`标记的`MemoryService`

**操作**：
1. 查找所有引用`MemoryService`的地方
2. 评估调用者需求，替换为对应的具体服务
3. 删除`MemoryService.java`

**涉及文件**：
- `service/MemoryService.java`（删除）

### 步骤3：简化Facade层（P0）

**目标**：删除`LongTermMemoryFacadeService`，直接调用`LongTermMemoryService`

**操作**：
1. 查找所有引用`LongTermMemoryFacadeService`的地方
2. 将引用替换为`LongTermMemoryService`
3. 删除`LongTermMemoryFacadeService.java`

**涉及文件**：
- `service/LongTermMemoryFacadeService.java`（删除）
- `service/ChatWorkflowService.java`（修改）
- `service/MemoryService.java`（修改，已在步骤2处理）

### 步骤4：优化StreamingService（P1）

**目标**：拆分过大的`StreamingService`，降低耦合

**操作**：
1. 将图像生成逻辑提取到`ImageService`
2. 将通用的SSE处理逻辑提取为工具类或基类

**涉及文件**：
- `service/StreamingService.java`（修改）
- `service/ImageService.java`（增强）

### 步骤5：更新测试用例（P1）

**目标**：确保重构后测试用例仍然通过

**操作**：
1. 更新引用已删除类的测试文件
2. 运行测试验证修改

**涉及文件**：
- `test/service/LongTermMemoryServiceTest.java`（修改）
- `test/service/MemoryRecallerTest.java`（检查）
- `test/service/MemoryServiceTest.java`（可能需要删除或修改）

---

## 四、依赖关系分析

```
当前依赖链：
ChatController → ChatService → ChatWorkflowService → LongTermMemoryFacadeService → LongTermMemoryService
                                                          ↓
                                                    LongTermMemoryManager

重构后依赖链：
ChatController → ChatService → ChatWorkflowService → LongTermMemoryService
```

---

## 五、风险处理

| 风险 | 处理策略 |
|------|----------|
| 引用遗漏 | 使用全局搜索确认所有引用 |
| 测试失败 | 重构前运行测试，重构后再次运行 |
| 回滚方案 | 保留原始代码备份，必要时可回退 |

---

## 六、验证标准

1. 所有编译通过
2. 所有测试用例通过
3. 无废弃代码警告
4. 无未使用的导入

---

## 七、预估影响范围

- **高影响**：记忆相关的所有功能
- **中影响**：聊天流程、流式响应
- **低影响**：配置类、工具类