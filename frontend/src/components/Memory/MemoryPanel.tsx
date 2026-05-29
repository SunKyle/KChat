import { useState, useEffect } from 'react';
import { Search, Plus, Filter, Trash2 } from 'lucide-react';
import { memoryApi } from '../../utils/memoryApi';
import { MEMORY_TYPES } from '../../types';
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

  const userId = 'default';

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

  const handleSubmit = (memory: Memory | Omit<Memory, 'id' | 'createdAt'>) => {
    memoryApi.create({
      userId: 'default',
      content: (memory as Memory).content,
      type: (memory as Memory).type,
      importance: (memory as Memory).importance,
      isRule: (memory as Memory).isRule,
    }).then(newMemory => {
      if ('id' in memory) {
        setMemories(memories.map(m => m.id === (memory as Memory).id ? newMemory : m));
      } else {
        setMemories([newMemory, ...memories]);
      }
      setShowForm(false);
      setEditingMemory(null);
    });
  };

  const handleEdit = (memory: Memory) => {
    setEditingMemory(memory);
    setShowForm(true);
  };

  const filteredMemories = selectedType === 'ALL'
    ? memories
    : memories.filter(m => m.type === selectedType);

  const isSelectAll = selectedMemories.length === filteredMemories.length && filteredMemories.length > 0;

  return (
    <div className="h-full flex flex-col bg-slate-900">
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

      {showForm && (
        <MemoryForm
          memory={editingMemory}
          onSubmit={handleSubmit}
          onCancel={() => { setShowForm(false); setEditingMemory(null); }}
        />
      )}
    </div>
  );
}