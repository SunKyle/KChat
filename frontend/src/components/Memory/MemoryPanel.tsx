import { useState, useEffect } from 'react';
import { Plus, Filter, Trash2, BookOpen, FileText, CheckCircle, Heart, Lightbulb, Star, Edit2, Search } from 'lucide-react';
import { memoryApi } from '../../utils/memoryApi';
import { MEMORY_TYPES } from '../../types';
import type { Memory, MemoryType } from '../../types';

interface MemoryPanelProps {
  onClose?: () => void;
}

const typeIcons: Record<string, React.ComponentType<{ size?: number; className?: string }>> = {
  KNOWLEDGE: BookOpen,
  RULE: FileText,
  FACT: CheckCircle,
  PREFERENCE: Heart,
  EXPERIENCE: Lightbulb,
};

export function MemoryPanel({ onClose }: MemoryPanelProps) {
  const [memories, setMemories] = useState<Memory[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedType, setSelectedType] = useState<MemoryType | 'ALL'>('ALL');
  const [showForm, setShowForm] = useState(false);
  const [editingMemory, setEditingMemory] = useState<Memory | null>(null);
  const [selectedMemories, setSelectedMemories] = useState<number[]>([]);
  const [debounceRef, setDebounceRef] = useState<ReturnType<typeof setTimeout> | null>(null);

  const userId = 'default';

  useEffect(() => {
    loadMemories();
    return () => {
      if (debounceRef) clearTimeout(debounceRef);
    };
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

  const handleSearchChange = (value: string) => {
    setSearchQuery(value);
    if (debounceRef) clearTimeout(debounceRef);
    const timer = setTimeout(() => handleSearch(value), 300);
    setDebounceRef(timer);
  };

  const handleDelete = async (id: number) => {
    try {
      await memoryApi.delete(id);
      setMemories(memories.filter(m => m.id !== id));
      setSelectedMemories(selectedMemories.filter(i => i !== id));
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

  const getTypeInfo = (type: string) => MEMORY_TYPES.find(t => t.type === type) || MEMORY_TYPES[0];
  const getTypeIcon = (type: string) => typeIcons[type] || BookOpen;

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('zh-CN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="space-y-6">
      {/* 搜索和操作栏 */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            placeholder="搜索记忆内容..."
            className="w-full pl-10 pr-4 py-2.5 bg-slate-800/50 border border-white/10 rounded-xl text-white placeholder-slate-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/50 transition-all"
          />
        </div>
        <div className="relative">
          <Filter className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <select
            value={selectedType}
            onChange={(e) => setSelectedType(e.target.value as MemoryType | 'ALL')}
            className="pl-10 pr-8 py-2.5 bg-slate-800/50 border border-white/10 rounded-xl text-white focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/50 transition-all appearance-none cursor-pointer"
          >
            <option value="ALL">全部类型</option>
            {MEMORY_TYPES.map(t => (
              <option key={t.type} value={t.type}>{t.label}</option>
            ))}
          </select>
        </div>
        <button
          onClick={() => { setEditingMemory(null); setShowForm(true); }}
          className="flex items-center gap-2 px-5 py-2.5 bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white rounded-xl transition-all shadow-lg shadow-blue-600/20 hover:shadow-blue-600/40"
        >
          <Plus className="w-5 h-5" />
          <span className="font-medium">添加记忆</span>
        </button>
      </div>

      {/* 批量操作栏 */}
      {selectedMemories.length > 0 && (
        <div className="flex items-center gap-4 px-4 py-3 bg-slate-800/30 border border-white/5 rounded-xl">
          <label className="flex items-center gap-2 cursor-pointer">
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
              className="w-4 h-4 rounded border-white/20 bg-slate-700 text-blue-500 focus:ring-blue-500/50"
            />
            <span className="text-slate-300 text-sm font-medium">
              已选择 {selectedMemories.length} 项
            </span>
          </label>
          <button
            onClick={handleBatchDelete}
            className="flex items-center gap-2 px-4 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-all text-sm font-medium"
          >
            <Trash2 className="w-4 h-4" />
            批量删除
          </button>
        </div>
      )}

      {/* 记忆列表 */}
      <div className="space-y-3">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="relative">
              <div className="w-10 h-10 border-4 border-blue-500/20 rounded-full animate-spin"></div>
              <div className="absolute inset-0 w-10 h-10 border-4 border-blue-500 border-t-transparent rounded-full animate-spin" style={{ animationDuration: '0.5s' }}></div>
            </div>
          </div>
        ) : filteredMemories.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-slate-500">
            <div className="w-16 h-16 rounded-full bg-slate-800/50 flex items-center justify-center mb-4">
              <Search className="w-8 h-8 opacity-50" />
            </div>
            <h3 className="text-lg font-medium text-slate-400 mb-1">暂无记忆内容</h3>
            <p className="text-sm mb-6">添加一些记忆，让 AI 更好地了解你</p>
            <button
              onClick={() => { setEditingMemory(null); setShowForm(true); }}
              className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl transition-all font-medium"
            >
              添加第一条记忆
            </button>
          </div>
        ) : (
          filteredMemories.map(memory => {
            const typeInfo = getTypeInfo(memory.type);
            const TypeIcon = getTypeIcon(memory.type);
            const isSelected = selectedMemories.includes(memory.id);

            return (
              <div
                key={memory.id}
                className={`group relative p-5 rounded-xl border transition-all cursor-pointer ${
                  isSelected
                    ? 'border-blue-500 bg-blue-500/10 shadow-lg shadow-blue-500/10'
                    : 'border-white/10 bg-slate-800/30 hover:bg-slate-800/60 hover:border-white/20'
                }`}
                onClick={() => setSelectedMemories(prev =>
                  prev.includes(memory.id) ? prev.filter(i => i !== memory.id) : [...prev, memory.id]
                )}
              >
                {/* 选择框 */}
                <div className="absolute left-4 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity">
                  <input
                    type="checkbox"
                    checked={isSelected}
                    onChange={(e) => e.stopPropagation()}
                    className="w-4 h-4 rounded border-white/20 bg-slate-700 text-blue-500 focus:ring-blue-500/50"
                  />
                </div>

                <div className="flex items-start gap-4" style={{ paddingLeft: selectedMemories.length > 0 ? '24px' : '0' }}>
                  {/* 类型标签 */}
                  <div className={`flex-shrink-0 w-10 h-10 rounded-xl ${typeInfo.color} flex items-center justify-center shadow-lg`}>
                    <TypeIcon className="w-5 h-5 text-white" />
                  </div>

                  {/* 内容区域 */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-2">
                      <span className={`px-2.5 py-1 text-xs font-medium rounded-lg ${typeInfo.color} text-white`}>
                        {typeInfo.label}
                      </span>
                      {memory.isRule && (
                        <span className="flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-lg bg-red-500/20 text-red-400">
                          <FileText className="w-3 h-3" />
                          规则
                        </span>
                      )}
                      {memory.score !== undefined && (
                        <span className="text-xs text-slate-500">
                          相似度 {(memory.score * 100).toFixed(1)}%
                        </span>
                      )}
                    </div>
                    <p className="text-white text-sm leading-relaxed line-clamp-3">
                      {memory.content}
                    </p>
                    <div className="flex items-center justify-between mt-3">
                      <div className="flex items-center gap-1">
                        {Array.from({ length: 5 }).map((_, i) => (
                          <Star
                            key={i}
                            className={`w-3.5 h-3.5 ${i < memory.importance ? 'text-yellow-400 fill-yellow-400' : 'text-slate-600'}`}
                          />
                        ))}
                      </div>
                      <span className="text-xs text-slate-500">
                        {formatDate(memory.createdAt)}
                      </span>
                    </div>
                  </div>

                  {/* 操作按钮 */}
                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={(e) => { e.stopPropagation(); handleEdit(memory); }}
                      className="p-2 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white transition-colors"
                      title="编辑"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDelete(memory.id); }}
                      className="p-2 hover:bg-red-500/20 rounded-lg text-slate-400 hover:text-red-400 transition-colors"
                      title="删除"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* 添加/编辑表单弹窗 */}
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4" onClick={() => { setShowForm(false); setEditingMemory(null); }}>
          <div className="w-full max-w-lg bg-[#1E293B] rounded-2xl shadow-2xl border border-white/10 overflow-hidden" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-5 border-b border-white/10">
              <h3 className="text-lg font-semibold text-white flex items-center gap-2">
                <span className="w-1 h-5 bg-blue-500 rounded-full" />
                {editingMemory ? '编辑记忆' : '添加记忆'}
              </h3>
              <button onClick={() => { setShowForm(false); setEditingMemory(null); }} className="p-2 text-slate-400 hover:text-white hover:bg-white/5 rounded-lg transition-colors">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="p-5 space-y-5">
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">内容</label>
                <textarea
                  value={editingMemory?.content || ''}
                  onChange={(e) => {
                    if (editingMemory) {
                      setEditingMemory({ ...editingMemory, content: e.target.value });
                    }
                  }}
                  placeholder="输入记忆内容..."
                  className="w-full h-32 px-4 py-3 bg-slate-800/50 text-white border border-white/10 rounded-xl focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/50 resize-none placeholder-slate-500"
                  maxLength={500}
                />
                <p className="text-xs text-slate-500 mt-1 text-right">
                  {(editingMemory?.content || '').length}/500
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">类型</label>
                <div className="flex flex-wrap gap-2">
                  {MEMORY_TYPES.map(t => {
                    const TypeIcon = getTypeIcon(t.type);
                    return (
                      <button
                        key={t.type}
                        onClick={() => {
                          if (editingMemory) {
                            setEditingMemory({ ...editingMemory, type: t.type as MemoryType });
                          }
                        }}
                        className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                          (!editingMemory || editingMemory.type === t.type)
                            ? `${t.color} text-white`
                            : 'bg-slate-800/50 text-slate-300 hover:bg-slate-800'
                        }`}
                      >
                        <TypeIcon className="w-4 h-4" />
                        {t.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">重要性</label>
                <div className="flex items-center gap-1">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <button
                      key={i}
                      onClick={() => {
                        if (editingMemory) {
                          setEditingMemory({ ...editingMemory, importance: i + 1 });
                        }
                      }}
                      className="p-2 hover:bg-white/5 rounded-lg transition-colors"
                    >
                      <Star
                        className={`w-5 h-5 ${i < (editingMemory?.importance || 3) ? 'text-yellow-400 fill-yellow-400' : 'text-slate-600'}`}
                      />
                    </button>
                  ))}
                  <span className="ml-3 text-sm text-slate-400">
                    {(editingMemory?.importance || 3)} 星
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <input
                  type="checkbox"
                  id="isRule"
                  checked={editingMemory?.isRule || false}
                  onChange={(e) => {
                    if (editingMemory) {
                      setEditingMemory({ ...editingMemory, isRule: e.target.checked });
                    }
                  }}
                  className="w-4 h-4 rounded border-white/20 bg-slate-700 text-blue-500 focus:ring-blue-500/50"
                />
                <label htmlFor="isRule" className="flex items-center gap-2 text-sm text-slate-300 cursor-pointer">
                  <FileText className="w-4 h-4 text-red-400" />
                  标记为规则
                  <span className="text-xs text-slate-500">(AI 将优先遵循此记忆)</span>
                </label>
              </div>
            </div>

            <div className="flex justify-end gap-3 p-5 border-t border-white/10">
              <button
                onClick={() => { setShowForm(false); setEditingMemory(null); }}
                className="px-5 py-2.5 text-slate-300 hover:text-white hover:bg-white/5 rounded-xl transition-all font-medium"
              >
                取消
              </button>
              <button
                onClick={() => {
                  if (editingMemory) {
                    handleSubmit(editingMemory);
                  }
                }}
                disabled={!editingMemory?.content.trim()}
                className={`px-5 py-2.5 rounded-xl transition-all font-medium ${
                  editingMemory?.content.trim()
                    ? 'bg-blue-600 hover:bg-blue-700 text-white'
                    : 'bg-slate-700 text-slate-500 cursor-not-allowed'
                }`}
              >
                {editingMemory ? '保存修改' : '创建记忆'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}