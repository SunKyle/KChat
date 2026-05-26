import { useState, useEffect } from 'react'
import { Plus, Edit2, Trash2, Save, X, Check } from 'lucide-react'
import { api } from '../../utils/api'
import type { ModelConfig } from '../../types'

export function ModelSettings() {
  const [configs, setConfigs] = useState<ModelConfig[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [showAddModal, setShowAddModal] = useState(false)
  const [editingConfig, setEditingConfig] = useState<ModelConfig | null>(null)
  const [formData, setFormData] = useState({
    name: '',
    modelId: '',
    baseUrl: '',
    apiKey: '',
    enabled: true,
  })

  useEffect(() => {
    loadConfigs()
  }, [])

  const loadConfigs = async () => {
    setIsLoading(true)
    try {
      const data = await api.modelConfigs.list()
      setConfigs(data)
    } catch (error) {
      console.error('Failed to load model configs:', error)
    } finally {
      setIsLoading(false)
    }
  }

  const handleOpenAddModal = () => {
    setEditingConfig(null)
    setFormData({
      name: '',
      modelId: '',
      baseUrl: '',
      apiKey: '',
      enabled: true,
    })
    setShowAddModal(true)
  }

  const handleOpenEditModal = (config: ModelConfig) => {
    setEditingConfig(config)
    setFormData({
      name: config.name,
      modelId: config.modelId,
      baseUrl: config.baseUrl,
      apiKey: '',
      enabled: config.enabled,
    })
    setShowAddModal(true)
  }

  const handleSave = async () => {
    try {
      if (editingConfig) {
        await api.modelConfigs.update(editingConfig.id, formData)
      } else {
        await api.modelConfigs.create(formData)
      }
      setShowAddModal(false)
      loadConfigs()
    } catch (error) {
      console.error('Failed to save model config:', error)
      const errorMessage =
        error instanceof Error ? error.message : '保存失败，请检查输入'
      alert(errorMessage)
    }
  }

  const handleDelete = async (id: number, name: string) => {
    if (!confirm(`确定要删除 "${name}" 吗？`)) return
    try {
      await api.modelConfigs.delete(id)
      loadConfigs()
    } catch (error) {
      console.error('Failed to delete model config:', error)
      alert('删除失败')
    }
  }

  const handleChange = (
    e: React.ChangeEvent<
      HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
    >,
  ) => {
    const { name, value, type } = e.target
    setFormData((prev) => ({
      ...prev,
      [name]:
        type === 'checkbox' ? (e.target as HTMLInputElement).checked : value,
    }))
  }

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-[#E5E7EB]">自定义模型配置</h2>
        <button
          onClick={handleOpenAddModal}
          className="flex items-center gap-2 px-4 py-2 bg-sky-500 hover:bg-sky-400 text-white rounded-lg transition-colors"
        >
          <Plus className="w-4 h-4" />
          添加模型
        </button>
      </div>

      {isLoading ? (
        <div className="text-center text-slate-500 py-8">加载中...</div>
      ) : configs.length === 0 ? (
        <div className="text-center text-slate-500 py-8">
          暂无自定义模型配置，点击上方按钮添加
        </div>
      ) : (
        <div className="space-y-4">
          {configs.map((config) => (
            <div
              key={config.id}
              className="p-4 bg-white/[0.03] rounded-xl border border-white/10 hover:border-white/20 transition-colors"
            >
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-sky-500/20 flex items-center justify-center">
                    <Check className="w-5 h-5 text-sky-400" />
                  </div>
                  <div>
                    <h3 className="font-medium text-[#E5E7EB]">
                      {config.name}
                    </h3>
                    <p className="text-sm text-slate-500">{config.modelId}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => handleOpenEditModal(config)}
                    className="p-2 text-slate-500 hover:text-slate-300 hover:bg-white/5 rounded-lg transition-colors"
                    title="编辑"
                  >
                    <Edit2 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(config.id, config.name)}
                    className="p-2 text-slate-500 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-colors"
                    title="删除"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
              <div className="text-sm text-slate-500">
                <p className="truncate">{config.baseUrl}</p>
              </div>
              <div className="mt-2 flex items-center gap-2">
                <span
                  className={`px-2 py-1 rounded-full text-xs ${
                    config.enabled
                      ? 'bg-green-500/20 text-green-400'
                      : 'bg-slate-500/20 text-slate-400'
                  }`}
                >
                  {config.enabled ? '已启用' : '已禁用'}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 模态框 */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-[#1E293B] rounded-xl w-full max-w-md border border-white/10">
            <div className="flex items-center justify-between p-4 border-b border-white/10">
              <h3 className="text-lg font-semibold text-[#E5E7EB]">
                {editingConfig ? '编辑模型' : '添加模型'}
              </h3>
              <button
                onClick={() => setShowAddModal(false)}
                className="p-2 text-slate-500 hover:text-slate-300 hover:bg-white/5 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-4 space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-400 mb-1">
                  显示名称
                </label>
                <input
                  type="text"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-[#E5E7EB] focus:outline-none focus:border-sky-500/50"
                  placeholder="如：OpenAI"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-400 mb-1">
                  模型 ID
                </label>
                <input
                  type="text"
                  name="modelId"
                  value={formData.modelId}
                  onChange={handleChange}
                  className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-[#E5E7EB] focus:outline-none focus:border-sky-500/50"
                  placeholder="如：gpt-3.5-turbo"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-400 mb-1">
                  API 地址
                </label>
                <input
                  type="text"
                  name="baseUrl"
                  value={formData.baseUrl}
                  onChange={handleChange}
                  className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-[#E5E7EB] focus:outline-none focus:border-sky-500/50"
                  placeholder="如：https://api.openai.com"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-400 mb-1">
                  API Key
                </label>
                <input
                  type="password"
                  name="apiKey"
                  value={formData.apiKey}
                  onChange={handleChange}
                  className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-[#E5E7EB] focus:outline-none focus:border-sky-500/50"
                  placeholder="sk-..."
                />
                {editingConfig && (
                  <p className="text-xs text-slate-500 mt-1">
                    留空则保持原 API Key
                  </p>
                )}
              </div>

              <div className="flex items-center gap-3">
                <input
                  type="checkbox"
                  name="enabled"
                  id="enabled"
                  checked={formData.enabled}
                  onChange={handleChange}
                  className="w-4 h-4 rounded border-white/20 bg-white/5 text-sky-500 focus:ring-sky-500/50"
                />
                <label htmlFor="enabled" className="text-sm text-slate-400">
                  启用
                </label>
              </div>
            </div>

            <div className="flex justify-end gap-3 p-4 border-t border-white/10">
              <button
                onClick={() => setShowAddModal(false)}
                className="px-4 py-2 text-slate-400 hover:text-slate-300 hover:bg-white/5 rounded-lg transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleSave}
                className="flex items-center gap-2 px-4 py-2 bg-sky-500 hover:bg-sky-400 text-white rounded-lg transition-colors"
              >
                <Save className="w-4 h-4" />
                保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
