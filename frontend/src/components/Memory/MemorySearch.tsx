import { Search } from 'lucide-react';
import { useEffect, useRef } from 'react';

interface MemorySearchProps {
  value: string;
  onChange: (value: string) => void;
  onSearch: (query: string) => void;
}

export default function MemorySearch({ value, onChange, onSearch }: MemorySearchProps) {
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    if (value.trim()) {
      debounceRef.current = setTimeout(() => {
        onSearch(value);
      }, 300);
    }

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, [value, onSearch]);

  const handleClear = () => {
    onChange('');
    onSearch('');
  };

  return (
    <div className="relative flex-1 max-w-md">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="搜索记忆（支持语义搜索）..."
        className="w-full pl-10 pr-10 py-2 bg-slate-800 text-white border border-white/10 rounded-lg focus:outline-none focus:border-blue-500 placeholder-slate-500"
      />
      {value && (
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