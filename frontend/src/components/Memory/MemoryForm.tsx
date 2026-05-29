import { useState, useEffect } from 'react';
import { X, Star, AlertCircle } from 'lucide-react';
import { MEMORY_TYPES } from '../../types';
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

        <div className="p-4 space-y-4">
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