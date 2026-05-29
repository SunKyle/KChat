# 前端长记忆与规则管理功能方案

## 一、功能概述

### 1.1 需求背景
基于后端已有的记忆管理 API，设计前端界面实现：
- **长记忆管理**：查看、创建、编辑、删除用户的长期记忆
- **规则管理**：将特定记忆标记为"规则"，用于指导 AI 行为
- **语义搜索**：通过自然语言搜索相关记忆

### 1.2 设计原则
- 清晰的分类展示（按记忆类型分组）
- 支持语义搜索和关键词搜索
- 直观的规则标记和管理
- 与现有聊天界面无缝集成

---

## 二、功能模块设计

### 2.1 模块结构

```
src/
├── components/
│   ├── Memory/
│   │   ├── MemoryPanel.tsx        # 记忆管理主面板
│   │   ├── MemoryList.tsx         # 记忆列表
│   │   ├── MemoryItem.tsx         # 单个记忆项
│   │   ├── MemoryForm.tsx         # 创建/编辑记忆表单
│   │   └── MemorySearch.tsx       # 搜索组件
│   └── Settings/
│       └── MemorySettings.tsx     # 记忆相关设置
├── utils/
│   └── memoryApi.ts               # 记忆 API 封装
└── types/
    └── memory.ts                  # 记忆相关类型定义
```

### 2.2 核心功能

#### 2.2.1 记忆管理面板
- 展示用户所有记忆，按类型分组
- 支持搜索和筛选
- 批量操作（批量删除、批量标记为规则）

#### 2.2.2 记忆列表
- 卡片式展示记忆内容
- 显示类型标签、重要性、创建时间
- 支持编辑和删除操作

#### 2.2.3 记忆表单
- 创建新记忆或编辑现有记忆
- 选择记忆类型
- 设置重要性级别（1-5星）
- 标记为规则

#### 2.2.4 语义搜索
- 输入自然语言查询
- 返回相关记忆（带相似度评分）
- 支持按类型过滤搜索结果

---

## 三、数据模型设计

### 3.1 类型定义

```typescript
// src/types/memory.ts

export interface Memory {
  id: number;
  userId: string;
  content: string;
  type: MemoryType;
  importance: number; // 1-5
  createdAt: string;
  score?: number; // 语义相似度评分
  isRule?: boolean; // 是否为规则
}

export type MemoryType = 
  | 'KNOWLEDGE'      // 知识库
  | 'RULE'           // 规则
  | 'FACT'           // 事实
  | 'PREFERENCE'     // 偏好
  | 'EXPERIENCE';    // 经验

export interface MemoryRecallRequest {
  userId: string;
  query: string;
  topK?: number;
  types?: MemoryType[];
}

export interface MemoryRecallResponse {
  memories: Memory[];
  count: number;
}

export interface MemoryTypeInfo {
  type: MemoryType;
  label: string;
  color: string;
  icon: string;
}

export const MEMORY_TYPES: MemoryTypeInfo[] = [
  { type: 'KNOWLEDGE', label: '知识库', color: 'bg-blue-500', icon: '📚' },
  { type: 'RULE', label: '规则', color: 'bg-red-500', icon: '📋' },
  { type: 'FACT', label: '事实', color: 'bg-green-500', icon: '✅' },
  { type: 'PREFERENCE', label: '偏好', color: 'bg-purple-500', icon: '❤️' },
  { type: 'EXPERIENCE', label: '经验', color: 'bg-orange-500', icon: '💡' },
];
```

### 3.2 API 封装

```typescript
// src/utils/memoryApi.ts

import type { Memory, MemoryRecallRequest, MemoryRecallResponse } from '../types';

const BASE_URL = 'http://localhost:8080/api';

export const memoryApi = {
  // 获取用户所有记忆
  getAll: async (userId: string): Promise<Memory[]> => {
    const response = await fetch(`${BASE_URL}/memories?userId=${userId}`);
    if (!response.ok) throw new Error('获取记忆失败');
    return response.json();
  },

  // 按类型获取记忆
  getByType: async (userId: string, type: string): Promise<Memory[]> => {
    const response = await fetch(`${BASE_URL}/memories/type/${type}?userId=${userId}`);
    if (!response.ok) throw new Error('获取记忆失败');
    return response.json();
  },

  // 获取单个记忆
  getById: async (id: number): Promise<Memory> => {
    const response = await fetch(`${BASE_URL}/memories/${id}`);
    if (!response.ok) throw new Error('获取记忆失败');
    return response.json();
  },

  // 创建记忆
  create: async (memory: Omit<Memory, 'id' | 'createdAt'>): Promise<Memory> => {
    const response = await fetch(`${BASE_URL}/memories`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(memory),
    });
    if (!response.ok) throw new Error('创建记忆失败');
    return response.json();
  },

  // 批量创建记忆
  createBatch: async (memories: Omit<Memory, 'id' | 'createdAt'>[]): Promise<Memory[]> => {
    const response = await fetch(`${BASE_URL}/memories/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(memories),
    });
    if (!response.ok) throw new Error('批量创建记忆失败');
    return response.json();
  },

  // 语义召回
  recall: async (request: MemoryRecallRequest): Promise<MemoryRecallResponse> => {
    const response = await fetch(`${BASE_URL}/memories/recall`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    if (!response.ok) throw new Error('召回失败');
    return response.json();
  },

  // 删除记忆
  delete: async (id: number): Promise<void> => {
    const response = await fetch(`${BASE_URL}/memories/${id}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error('删除失败');
  },

  // 删除用户所有记忆
  deleteByUserId: async (userId: string): Promise<void> => {
    const response = await fetch(`${BASE_URL}/memories/user/${userId}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error('删除失败');
  },

  // 获取所有记忆类型
  getTypes: async (): Promise<string[]> => {
    const response = await fetch(`${BASE_URL}/memories/types`);
    if (!response.ok) throw new Error('获取类型失败');
    return response.json();
  },
};
```

---

## 四、UI 组件设计

### 4.1 MemoryPanel 主面板

```typescript
// src/components/Memory/MemoryPanel.tsx

import { useState, useEffect } from 'react';
import { Search, Plus, Filter, Trash2 } from 'lucide-react';
import { memoryApi, MEMORY_TYPES } from '../../utils';
import type { Memory, MemoryType } from '../../types';
import MemoryList from './MemoryList';
import MemoryForm from './MemoryForm';
import MemorySearch from './MemorySearch';

export function MemoryPanel() {
  const [memories, setMemories] = useState<Memory[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedType, setSelectedType] = useState<MemoryType | 'ALL'>('ALL');
  const [showForm, setShowForm] = useState(false);
  const [editingMemory, setEditingMemory] = useState<Memory | null>(null);
  const [selectedMemories, setSelectedMemories] = useState<number[]>([]);

  const userId = 'default'; // 当前用户 ID

  useEffect(() => {
    loadMemories();
  }, []);

  const loadMemories = async () => {
    setLoading(true);
    try {
      const data = await memoryApi.getAll(userId);
      setMemories(data);
    } catch (error) {
      console.error('加载记忆失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async (query: string) => {
    if (!query.trim()) {
      loadMemories();
      return;
    }
    setLoading(true);
    try {
      const result = await memoryApi.recall({ userId, query, topK: 20 });
      setMemories(result.memories);
    } catch (error) {
      console.error('搜索失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await memoryApi.delete(id);
      setMemories(memories.filter(m => m.id !== id));
    } catch (error) {
      console.error('删除失败:', error);
    }
  };

  const handleBatchDelete = async () => {
    for (const id of selectedMemories) {
      await memoryApi.delete(id);
    }
    setMemories(memories.filter(m => !selectedMemories.includes(m.id)));
    setSelectedMemories([]);
  };

  const handleCreate = (memory: Omit<Memory, 'id' | 'createdAt'>) => {
    memoryApi.create(memory).then(newMemory => {
      setMemories([newMemory, ...memories]);
      setShowForm(false);
    });
  };

  const handleEdit = (memory: Memory) => {
    setEditingMemory(memory);
    setShowForm(true);
  };

  const handleUpdate = (memory: Memory) => {
    memoryApi.create({
      userId: memory.userId,
      content: memory.content,
      type: memory.type,
      importance: memory.importance,
      isRule: memory.isRule,
    }).then(updated => {
      setMemories(memories.map(m => m.id === memory.id ? updated : m));
      setShowForm(false);
      setEditingMemory(null);
    });
  };

  const filteredMemories = selectedType === 'ALL'
    ? memories
    : memories.filter(m => m.type === selectedType);

  const isSelectAll = selectedMemories.length === filteredMemories.length && filteredMemories.length > 0;

  return (
    <div className="h-full flex flex-col bg-slate-900">
      {/* 头部 */}
      <div className="p-4 border-b border-white/10">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-semibold text-white">记忆管理</h2>
          <button
            onClick={() => { setEditingMemory(null); setShowForm(true); }}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors"
          >
            <Plus className="w-4 h-4" />
            添加记忆
          </button>
        </div>

        {/* 搜索和筛选 */}
        <div className="flex gap-4">
          <MemorySearch value={searchQuery} onChange={setSearchQuery} onSearch={handleSearch} />
          <div className="relative">
            <Filter className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
            <select
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value as MemoryType | 'ALL')}
              className="pl-10 pr-8 py-2 bg-slate-800 text-white border border-white/10 rounded-lg focus:outline-none focus:border-blue-500"
            >
              <option value="ALL">全部类型</option>
              {MEMORY_TYPES.map(t => (
                <option key={t.type} value={t.type}>{t.label}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* 批量操作栏 */}
      {selectedMemories.length > 0 && (
        <div className="px-4 py-3 bg-slate-800/50 border-b border-white/10 flex items-center gap-4">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={isSelectAll}
              onChange={(e) => {
                if (e.target.checked) {
                  setSelectedMemories(filteredMemories.map(m => m.id));
                } else {
                  setSelectedMemories([]);
                }
              }}
              className="rounded border-white/20 bg-slate-700"
            />
            <span className="text-slate-300 text-sm">
              已选择 {selectedMemories.length} 项
            </span>
          </label>
          <button
            onClick={handleBatchDelete}
            className="flex items-center gap-2 px-3 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors text-sm"
          >
            <Trash2 className="w-4 h-4" />
            批量删除
          </button>
        </div>
      )}

      {/* 记忆列表 */}
      <div className="flex-1 overflow-y-auto p-4">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
          </div>
        ) : filteredMemories.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-slate-500">
            <Search className="w-12 h-12 mb-4 opacity-50" />
            <p>暂无记忆内容</p>
            <button
              onClick={() => { setEditingMemory(null); setShowForm(true); }}
              className="mt-4 text-blue-500 hover:text-blue-400"
            >
              添加第一条记忆
            </button>
          </div>
        ) : (
          <MemoryList
            memories={filteredMemories}
            selectedMemories={selectedMemories}
            onSelect={(id) => {
              setSelectedMemories(prev =>
                prev.includes(id) ? prev.filter(i => i !== id) : [...prev, id]
              );
            }}
            onEdit={handleEdit}
            onDelete={handleDelete}
          />
        )}
      </div>

      {/* 创建/编辑表单弹窗 */}
      {showForm && (
        <MemoryForm
          memory={editingMemory}
          onSubmit={editingMemory ? handleUpdate : handleCreate}
          onCancel={() => { setShowForm(false); setEditingMemory(null); }}
        />
      )}
    </div>
  );
}
```

### 4.2 MemoryList 列表组件

```typescript
// src/components/Memory/MemoryList.tsx

import { Edit2, Trash2, Star, AlertCircle } from 'lucide-react';
import { MEMORY_TYPES } from '../../utils';
import type { Memory } from '../../types';

interface MemoryListProps {
  memories: Memory[];
  selectedMemories: number[];
  onSelect: (id: number) => void;
  onEdit: (memory: Memory) => void;
  onDelete: (id: number) => void;
}

export default function MemoryList({ memories, selectedMemories, onSelect, onEdit, onDelete }: MemoryListProps) {
  const getTypeInfo = (type: string) => 
    MEMORY_TYPES.find(t => t.type === type) || MEMORY_TYPES[0];

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  return (
    <div className="space-y-3">
      {memories.map(memory => {
        const typeInfo = getTypeInfo(memory.type);
        const isSelected = selectedMemories.includes(memory.id);

        return (
          <div
            key={memory.id}
            className={`p-4 rounded-lg border transition-all cursor-pointer group ${
              isSelected 
                ? 'border-blue-500 bg-blue-500/10' 
                : 'border-white/10 bg-slate-800/50 hover:bg-slate-800'
            }`}
            onClick={() => onSelect(memory.id)}
          >
            <div className="flex items-start gap-3">
              {/* 选择框 */}
              <input
                type="checkbox"
                checked={isSelected}
                onChange={(e) => { e.stopPropagation(); onSelect(memory.id); }}
                className="mt-1 rounded border-white/20 bg-slate-700"
              />

              {/* 内容 */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className={`px-2 py-0.5 text-xs rounded-full ${typeInfo.color} text-white`}>
                    {typeInfo.icon} {typeInfo.label}
                  </span>
                  {memory.isRule && (
                    <span className="flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-red-500/20 text-red-400">
                      <AlertCircle className="w-3 h-3" />
                      规则
                    </span>
                  )}
                  {memory.score !== undefined && (
                    <span className="text-xs text-slate-500">
                      相似度: {(memory.score * 100).toFixed(1)}%
                    </span>
                  )}
                </div>
                <p className="text-white text-sm leading-relaxed line-clamp-3">
                  {memory.content}
                </p>
                <div className="flex items-center gap-4 mt-2 text-xs text-slate-500">
                  <span>{formatDate(memory.createdAt)}</span>
                  <span className="flex items-center gap-1">
                    {Array.from({ length: 5 }).map((_, i) => (
                      <Star
                        key={i}
                        className={`w-3 h-3 ${
                          i < memory.importance ? 'text-yellow-400 fill-yellow-400' : 'text-slate-600'
                        }`}
                      />
                    ))}
                  </span>
                </div>
              </div>

              {/* 操作按钮 */}
              <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <button
                  onClick={(e) => { e.stopPropagation(); onEdit(memory); }}
                  className="p-2 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white transition-colors"
                  title="编辑"
                >
                  <Edit2 className="w-4 h-4" />
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); onDelete(memory.id); }}
                  className="p-2 hover:bg-red-500/20 rounded-lg text-slate-400 hover:text-red-400 transition-colors"
                  title="删除"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
```

### 4.3 MemoryForm 表单组件

```typescript
// src/components/Memory/MemoryForm.tsx

import { useState, useEffect } from 'react';
import { X, Star, AlertCircle } from 'lucide-react';
import { MEMORY_TYPES } from '../../utils';
import type { Memory, MemoryType } from '../../types';

interface MemoryFormProps {
  memory: Memory | null;
  onSubmit: (memory: Memory | Omit<Memory, 'id' | 'createdAt'>) => void;
  onCancel: () => void;
}

export default function MemoryForm({ memory, onSubmit, onCancel }: MemoryFormProps) {
  const [content, setContent] = useState('');
  const [type, setType] = useState<MemoryType>('KNOWLEDGE');
  const [importance, setImportance] = useState(3);
  const [isRule, setIsRule] = useState(false);

  useEffect(() => {
    if (memory) {
      setContent(memory.content);
      setType(memory.type as MemoryType);
      setImportance(memory.importance || 3);
      setIsRule(memory.isRule || false);
    } else {
      setContent('');
      setType('KNOWLEDGE');
      setImportance(3);
      setIsRule(false);
    }
  }, [memory]);

  const handleSubmit = () => {
    if (!content.trim()) return;

    if (memory) {
      onSubmit({
        ...memory,
        content: content.trim(),
        type,
        importance,
        isRule,
      });
    } else {
      onSubmit({
        userId: 'default',
        content: content.trim(),
        type,
        importance,
        isRule,
      });
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="w-full max-w-lg bg-slate-900 rounded-xl border border-white/10 shadow-2xl">
        {/* 头部 */}
        <div className="flex items-center justify-between p-4 border-b border-white/10">
          <h3 className="text-lg font-semibold text-white">
            {memory ? '编辑记忆' : '添加记忆'}
          </h3>
          <button
            onClick={onCancel}
            className="p-1 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* 表单内容 */}
        <div className="p-4 space-y-4">
          {/* 内容 */}
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">
              内容 <span className="text-red-400">*</span>
            </label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="输入记忆内容..."
              className="w-full h-32 px-4 py-3 bg-slate-800 text-white border border-white/10 rounded-lg focus:outline-none focus:border-blue-500 resize-none"
              maxLength={500}
            />
            <p className="text-xs text-slate-500 mt-1 text-right">
              {content.length}/500
            </p>
          </div>

          {/* 类型选择 */}
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">
              类型
            </label>
            <div className="flex flex-wrap gap-2">
              {MEMORY_TYPES.map(t => (
                <button
                  key={t.type}
                  onClick={() => setType(t.type)}
                  className={`flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm transition-all ${
                    type === t.type
                      ? `${t.color} text-white`
                      : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                  }`}
                >
                  {t.icon} {t.label}
                </button>
              ))}
            </div>
          </div>

          {/* 重要性 */}
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">
              重要性
            </label>
            <div className="flex items-center gap-1">
              {Array.from({ length: 5 }).map((_, i) => (
                <button
                  key={i}
                  onClick={() => setImportance(i + 1)}
                  className="p-2 hover:bg-white/10 rounded-lg transition-colors"
                >
                  <Star
                    className={`w-6 h-6 ${
                      i < importance ? 'text-yellow-400 fill-yellow-400' : 'text-slate-600'
                    }`}
                  />
                </button>
              ))}
              <span className="ml-2 text-sm text-slate-400">
                {importance} 星
              </span>
            </div>
          </div>

          {/* 标记为规则 */}
          <div className="flex items-center gap-3">
            <input
              type="checkbox"
              id="isRule"
              checked={isRule}
              onChange={(e) => setIsRule(e.target.checked)}
              className="w-4 h-4 rounded border-white/20 bg-slate-700"
            />
            <label htmlFor="isRule" className="flex items-center gap-2 text-sm text-slate-300">
              <AlertCircle className="w-4 h-4 text-red-400" />
              标记为规则
              <span className="text-xs text-slate-500">(AI 将优先遵循此记忆)</span>
            </label>
          </div>
        </div>

        {/* 底部操作 */}
        <div className="flex justify-end gap-3 p-4 border-t border-white/10">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-slate-300 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
          >
            取消
          </button>
          <button
            onClick={handleSubmit}
            disabled={!content.trim()}
            className={`px-4 py-2 rounded-lg transition-colors ${
              content.trim()
                ? 'bg-blue-600 hover:bg-blue-700 text-white'
                : 'bg-slate-700 text-slate-500 cursor-not-allowed'
            }`}
          >
            {memory ? '保存修改' : '创建记忆'}
          </button>
        </div>
      </div>
    </div>
  );
}
```

### 4.4 MemorySearch 搜索组件

```typescript
// src/components/Memory/MemorySearch.tsx

import { Search } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';

interface MemorySearchProps {
  value: string;
  onChange: (value: string) => void;
  onSearch: (query: string) => void;
}

export default function MemorySearch({ value, onChange, onSearch }: MemorySearchProps) {
  const [localValue, setLocalValue] = useState(value);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    setLocalValue(value);
  }, [value]);

  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    if (localValue.trim()) {
      debounceRef.current = setTimeout(() => {
        onChange(localValue);
        onSearch(localValue);
      }, 300);
    } else {
      onChange('');
      onSearch('');
    }

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, [localValue, onChange, onSearch]);

  const handleClear = () => {
    setLocalValue('');
  };

  return (
    <div className="relative flex-1 max-w-md">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
      <input
        type="text"
        value={localValue}
        onChange={(e) => setLocalValue(e.target.value)}
        placeholder="搜索记忆（支持语义搜索）..."
        className="w-full pl-10 pr-10 py-2 bg-slate-800 text-white border border-white/10 rounded-lg focus:outline-none focus:border-blue-500 placeholder-slate-500"
      />
      {localValue && (
        <button
          onClick={handleClear}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-white"
        >
          ✕
        </button>
      )}
    </div>
  );
}
```

---

## 五、页面集成

### 5.1 侧边栏集成

在侧边栏添加"记忆管理"入口：

```typescript
// src/components/Sidebar/index.tsx

// 在侧边栏菜单中添加
<SidebarItem
  icon={<BookOpen className="w-5 h-5" />}
  label="记忆管理"
  active={activeView === 'memory'}
  onClick={() => setActiveView('memory')}
/>
```

### 5.2 主内容区域

```typescript
// src/App.tsx

import { MemoryPanel } from './components/Memory/MemoryPanel';

// 在主内容区域根据 activeView 渲染
{activeView === 'memory' && <MemoryPanel />}
```

---

## 六、交互流程

### 6.1 创建记忆流程
1. 用户点击"添加记忆"按钮
2. 弹出表单弹窗
3. 填写内容、选择类型、设置重要性
4. 可选：标记为规则
5. 点击"创建记忆"
6. 记忆添加成功，列表刷新

### 6.2 语义搜索流程
1. 用户在搜索框输入查询词
2. 自动触发语义召回 API
3. 显示相关记忆（带相似度评分）
4. 支持按类型筛选

### 6.3 规则应用流程
1. 用户将记忆标记为规则
2. 聊天时后端自动召回相关规则
3. AI 优先遵循规则内容进行回复

---

## 七、样式设计

### 7.1 颜色方案
- **主背景**: `bg-slate-900`
- **卡片背景**: `bg-slate-800/50`
- **边框**: `border-white/10`
- **选中状态**: `border-blue-500 bg-blue-500/10`
- **按钮**: `bg-blue-600 hover:bg-blue-700`

### 7.2 记忆类型颜色
| 类型 | 颜色 | 图标 |
|------|------|------|
| 知识库 | `bg-blue-500` | 📚 |
| 规则 | `bg-red-500` | 📋 |
| 事实 | `bg-green-500` | ✅ |
| 偏好 | `bg-purple-500` | ❤️ |
| 经验 | `bg-orange-500` | 💡 |

---

## 八、API 调用示例

### 8.1 创建记忆

```javascript
const memory = await memoryApi.create({
  userId: 'default',
  content: '用户喜欢使用简洁的回复方式',
  type: 'PREFERENCE',
  importance: 4,
  isRule: false,
});
```

### 8.2 语义召回

```javascript
const result = await memoryApi.recall({
  userId: 'default',
  query: '用户有什么偏好？',
  topK: 5,
  types: ['PREFERENCE', 'RULE'],
});
```

---

## 九、安全与性能考量

### 9.1 安全
- 用户 ID 通过上下文传递，避免 URL 篡改
- 后端验证用户权限
- 输入内容长度限制（500字符）

### 9.2 性能
- 搜索使用防抖（300ms）
- 列表支持虚拟滚动（大量数据时）
- 语义召回限制返回数量（topK=20）

---

## 十、扩展计划

### 10.1 短期目标（1-2周）
- 基础记忆管理功能
- 语义搜索
- 规则标记

### 10.2 中期目标（1个月）
- 记忆导入/导出
- 记忆分类管理
- 记忆版本历史

### 10.3 长期目标（3个月）
- 智能记忆提取（从对话自动提取）
- 记忆关联图谱
- 多模态记忆支持（图片、文档）