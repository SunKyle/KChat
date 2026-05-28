import { useState, useEffect } from 'react'
import { Plus, Edit2, Trash2, Save, X, Database, Copy } from 'lucide-react'
import { api } from '../../utils/api'
import type { ModelConfig, ProviderType } from '../../types'
import { useChat } from '../../context/ChatContext'
import { PROVIDERS } from '../../types'

export function ModelSettings() {
  const { refreshModels } = useChat()
  const [configs, setConfigs] = useState<ModelConfig[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [showAddModal, setShowAddModal] = useState(false)
  const [editingConfig, setEditingConfig] = useState<ModelConfig | null>(null)
  const [selectedProvider, setSelectedProvider] = useState<ProviderType>('OPENAI')
  const [copyMessage, setCopyMessage] = useState<string | null>(null)
  const [updatingId, setUpdatingId] = useState<number | null>(null)

  const [formData, setFormData] = useState({
    name: '',
    modelId: '',
    baseUrl: '',
    apiKey: '',
    type: 'OPENAI' as ProviderType,
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
    const defaultProvider = PROVIDERS[0]
    setFormData({
      name: '',
      modelId: '',
      baseUrl: defaultProvider.defaultBaseUrl || '',
      apiKey: '',
      type: defaultProvider.type,
      enabled: true,
    })
    setSelectedProvider(defaultProvider.type)
    setShowAddModal(true)
  }

  const handleOpenEditModal = (config: ModelConfig) => {
    setEditingConfig(config)
    setFormData({
      name: config.name,
      modelId: config.modelId,
      baseUrl: config.baseUrl,
      apiKey: '',
      type: config.type,
      enabled: config.enabled,
    })
    setSelectedProvider(config.type)
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
      await refreshModels()
    } catch (error) {
      console.error('Failed to save model config:', error)
      const errorMessage =
        error instanceof Error ? error.message : '保存失败，请检查输入'
      alert(errorMessage)
    }
  }

  const handleToggleEnabled = async (config: ModelConfig) => {
    setUpdatingId(config.id)
    try {
      const newEnabled = !config.enabled
      await api.modelConfigs.update(config.id, {
        ...config,
        enabled: newEnabled,
      })
      setConfigs(prev =>
        prev.map(c =>
          c.id === config.id ? { ...c, enabled: newEnabled } : c
        )
      )
      await refreshModels()
    } catch (error) {
      console.error('Failed to toggle model:', error)
      alert('操作失败，请重试')
    } finally {
      setUpdatingId(null)
    }
  }

  const handleDelete = async (id: number, name: string) => {
    if (!confirm(`确定要删除 "${name}" 吗？`)) return
    try {
      await api.modelConfigs.delete(id)
      loadConfigs()
      await refreshModels()
    } catch (error) {
      console.error('Failed to delete model config:', error)
      alert('删除失败')
    }
  }

  const handleProviderChange = (type: ProviderType) => {
    setSelectedProvider(type)
    setFormData((prev) => {
      const provider = PROVIDERS.find((p) => p.type === type)
      return {
        ...prev,
        type,
        baseUrl: provider?.defaultBaseUrl || prev.baseUrl,
      }
    })
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

  const handleCopy = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text)
      setCopyMessage(`已复制 ${label}`)
      setTimeout(() => setCopyMessage(null), 2000)
    } catch (error) {
      console.error('Failed to copy:', error)
    }
  }

  const groupConfigsByProvider = () => {
    const grouped: Record<string, ModelConfig[]> = {}
    PROVIDERS.forEach((provider) => {
      grouped[provider.type] = []
    })
    configs.forEach((config) => {
      const targetType = config.type === 'OPENAI_COMPATIBLE' ? 'OPENAI' : config.type
      if (grouped[targetType]) {
        grouped[targetType].push(config)
      }
    })
    return grouped
  }

  const getProviderInfo = (type: ProviderType) => {
    let actualType = type === 'OPENAI_COMPATIBLE' ? 'OPENAI' : type
    return PROVIDERS.find(p => p.type === actualType) || PROVIDERS[PROVIDERS.length - 1]
  }

  const groupedConfigs = groupConfigsByProvider()

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-xl font-semibold text-[#E5E7EB]">模型配置</h2>
          <p className="text-slate-500 text-sm mt-1">管理你的 AI 模型</p>
        </div>
        <button
          onClick={handleOpenAddModal}
          className="flex items-center gap-2 px-4 py-2 bg-sky-500 hover:bg-sky-400 text-white rounded-lg transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span className="text-sm font-medium">添加模型</span>
        </button>
      </div>

      {copyMessage && (
        <div className="fixed top-4 right-4 bg-green-500 text-white px-3 py-1.5 rounded-lg text-sm shadow-lg animate-fade-in z-50">
          {copyMessage}
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <div className="w-8 h-8 border-3 border-sky-500 border-t-transparent rounded-full animate-spin"></div>
        </div>
      ) : configs.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <div className="w-14 h-14 rounded-full bg-white/[0.03] flex items-center justify-center mb-4">
            <Database className="w-7 h-7 text-slate-600" />
          </div>
          <h3 className="text-base font-medium text-[#E5E7EB] mb-1">暂无模型配置</h3>
          <p className="text-slate-500 text-sm mb-5">添加你的第一个 AI 模型</p>
          <button
            onClick={handleOpenAddModal}
            className="px-4 py-2 bg-white/5 hover:bg-white/10 text-slate-300 rounded-lg transition-colors text-sm"
          >
            开始添加
          </button>
        </div>
      ) : (
        <div className="space-y-6">
          {PROVIDERS.map((provider) => {
            const providerConfigs = groupedConfigs[provider.type]
            if (providerConfigs.length === 0) return null

            return (
              <div key={provider.type}>
                <div className="flex items-center gap-2.5 mb-3">
                  <div className={`w-7 h-7 rounded-lg ${provider.color} flex items-center justify-center text-white`}>
                    <span className="text-sm">{provider.icon}</span>
                  </div>
                  <h3 className="text-sm font-medium text-slate-300">
                    {provider.displayName}
                  </h3>
                  <span className="text-xs text-slate-600 bg-white/3 px-2 py-0.5 rounded-full">
                    {providerConfigs.length}
                  </span>
                </div>
                <div className="space-y-2">
                  {providerConfigs.map((config) => {
                    const configProvider = getProviderInfo(config.type)
                    return (
                      <div
                        key={config.id}
                        className="group flex items-center justify-between p-4 bg-white/[0.02] rounded-xl border border-white/5 hover:border-white/10 transition-colors"
                      >
                        <div className="min-w-0 flex-1">
                          <h4 className="text-sm font-medium text-[#E5E7EB] truncate">
                            {config.name}
                          </h4>
                          <div className="flex items-center gap-2 mt-0.5">
                            <p className="text-xs text-slate-500 font-mono truncate max-w-[250px]">
                              {config.modelId}
                            </p>
                            <button
                              onClick={() => handleCopy(config.modelId, '模型 ID')}
                              className="p-0.5 text-slate-600 hover:text-slate-400 rounded transition-colors"
                              title="复制模型 ID"
                            >
                              <Copy className="w-3 h-3" />
                            </button>
                          </div>
                        </div>
                        
                        <div className="flex items-center gap-3">
                          <button
                            onClick={() => handleToggleEnabled(config)}
                            disabled={updatingId === config.id}
                            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-sky-500/30 ${
                              config.enabled ? 'bg-emerald-500' : 'bg-slate-700'
                            } ${updatingId === config.id ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                          >
                            <span
                              className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                                config.enabled ? 'translate-x-6' : 'translate-x-1'
                              } ${updatingId === config.id ? 'animate-pulse' : ''}`}
                            />
                          </button>
                          <button
                            onClick={() => handleOpenEditModal(config)}
                            className="p-1.5 text-slate-600 hover:text-sky-400 hover:bg-white/5 rounded-lg transition-colors"
                            title="编辑"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDelete(config.id, config.name)}
                            className="p-1.5 text-slate-600 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-colors"
                            title="删除"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showAddModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-[#1E293B] rounded-xl w-full max-w-lg border border-white/10">
            <div className="flex items-center justify-between p-4 border-b border-white/5">
              <h3 className="text-base font-semibold text-[#E5E7EB]">
                {editingConfig ? '编辑模型' : '添加模型'}
              </h3>
              <button
                onClick={() => setShowAddModal(false)}
                className="p-1.5 text-slate-500 hover:text-slate-300 hover:bg-white/5 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-4 space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-2">
                  服务商
                </label>
                <div className="grid grid-cols-3 gap-2">
                  {PROVIDERS.map((provider) => (
                  <button
                    key={provider.type}
                    onClick={() => handleProviderChange(provider.type)}
                    className={`flex items-center justify-start gap-2 p-2.5 rounded-lg border transition-all ${
                      selectedProvider === provider.type
                        ? 'border-sky-500 bg-sky-500/10'
                        : 'border-white/5 hover:border-white/10 hover:bg-white/[0.02]'
                    }`}
                  >
                    <span
                      className={`w-6 h-6 rounded ${provider.color} flex items-center justify-center text-white text-xs flex-shrink-0`}
                    >
                      {provider.icon}
                    </span>
                    <span className="text-xs text-slate-400">
                      {provider.displayName}
                    </span>
                  </button>
                ))}
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  名称
                </label>
                <input
                  type="text"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  className="w-full px-3 py-2 bg-white/5 border border-white/5 rounded-lg text-[#E5E7EB] text-sm focus:outline-none focus:border-sky-500/50 transition-colors"
                  placeholder="我的模型"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  模型 ID
                </label>
                <input
                  type="text"
                  name="modelId"
                  value={formData.modelId}
                  onChange={handleChange}
                  className="w-full px-3 py-2 bg-white/5 border border-white/5 rounded-lg text-[#E5E7EB] text-sm font-mono focus:outline-none focus:border-sky-500/50 transition-colors"
                  placeholder="gpt-3.5-turbo"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  API 地址
                </label>
                <input
                  type="text"
                  name="baseUrl"
                  value={formData.baseUrl}
                  onChange={handleChange}
                  className="w-full px-3 py-2 bg-white/5 border border-white/5 rounded-lg text-[#E5E7EB] text-sm font-mono focus:outline-none focus:border-sky-500/50 transition-colors"
                  placeholder="https://api.openai.com"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  API Key
                </label>
                <input
                  type="password"
                  name="apiKey"
                  value={formData.apiKey}
                  onChange={handleChange}
                  className="w-full px-3 py-2 bg-white/5 border border-white/5 rounded-lg text-[#E5E7EB] text-sm font-mono focus:outline-none focus:border-sky-500/50 transition-colors"
                  placeholder="sk-..."
                />
                {editingConfig && (
                  <p className="text-xs text-slate-600 mt-1.5">
                    留空保持原 API Key
                  </p>
                )}
              </div>

              <div className="flex items-center gap-2.5">
                <input
                  type="checkbox"
                  name="enabled"
                  id="enabled"
                  checked={formData.enabled}
                  onChange={handleChange}
                  className="w-4 h-4 rounded border-white/20 bg-white/5 text-sky-500 focus:ring-sky-500/50"
                />
                <label htmlFor="enabled" className="text-sm text-slate-400">
                  启用此模型
                </label>
              </div>
            </div>

            <div className="flex justify-end gap-2 p-4 border-t border-white/5">
              <button
                onClick={() => setShowAddModal(false)}
                className="px-4 py-2 text-slate-500 hover:text-slate-300 hover:bg-white/5 rounded-lg transition-colors text-sm"
              >
                取消
              </button>
              <button
                onClick={handleSave}
                className="px-4 py-2 bg-sky-500 hover:bg-sky-400 text-white rounded-lg transition-colors text-sm font-medium"
              >
                {editingConfig ? '更新' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
