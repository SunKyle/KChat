import { useState, useEffect } from 'react'
import { Plus, Edit2, Trash2, X, Brain, Database, Copy } from 'lucide-react'
import { modelConfigs } from '../../api'
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
  const [updatingId, setUpdatingId] = useState<string | number | null>(null)

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
      const data = await modelConfigs.list()
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
        await modelConfigs.update(editingConfig.id, formData)
      } else {
        await modelConfigs.create(formData)
      }
      setShowAddModal(false)
      loadConfigs()
      await refreshModels()
    } catch (error) {
      console.error('Failed to save model config:', error)
      const errorMessage = error instanceof Error ? error.message : '保存失败，请检查输入'
      alert(errorMessage)
    }
  }

  const handleToggleEnabled = async (config: ModelConfig) => {
    setUpdatingId(config.id)
    try {
      const newEnabled = !config.enabled
      await modelConfigs.update(config.id, {
        ...config,
        enabled: newEnabled,
      })
      setConfigs((prev) =>
        prev.map((c) => (c.id === config.id ? { ...c, enabled: newEnabled } : c))
      )
      await refreshModels()
    } catch (error) {
      console.error('Failed to toggle model:', error)
      alert('操作失败，请重试')
    } finally {
      setUpdatingId(null)
    }
  }

  const handleDelete = async (id: string | number, name: string) => {
    if (!confirm(`确定要删除 "${name}" 吗？`)) return
    try {
      await modelConfigs.delete(id)
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
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value, type } = e.target
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? (e.target as HTMLInputElement).checked : value,
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

  const groupedConfigs = groupConfigsByProvider()

  return (
    <div className='flex flex-col max-h-[calc(100vh-200px)] min-h-[200px]'>
      {copyMessage && (
        <div className='fixed top-4 right-4 theme-bg-brand-success text-white px-3 py-1.5 rounded-lg text-sm shadow-lg animate-fade-in z-50'>
          {copyMessage}
        </div>
      )}

      <div className='flex items-center justify-between mb-4'>
        <div className='flex items-center gap-2'>
          <Brain className='w-5 h-5 theme-text-muted' />
          <h3 className='font-medium theme-text-primary'>模型列表</h3>
        </div>
        <button
          onClick={handleOpenAddModal}
          className='flex items-center gap-1.5 px-4 py-2 theme-bg-accent-sky text-white rounded-lg hover:bg-[var(--accent-sky)]/80 transition-colors text-sm font-medium'
        >
          <Plus className='w-4 h-4' />
          添加模型
        </button>
      </div>

      {isLoading ? (
        <div className='flex items-center justify-center py-12'>
          <div className='w-8 h-8 border-3 border-[var(--accent-sky)] border-t-transparent rounded-full animate-spin'></div>
        </div>
      ) : configs.length === 0 ? (
        <div className='theme-bg-sidebar/80 backdrop-blur-xl rounded-2xl p-8 border-0 shadow-[0_2px_8px_rgba(0,0,0,0.15),0_4px_16px_rgba(0,0,0,0.1)] text-center'>
          <div className='w-14 h-14 rounded-full theme-bg-input flex items-center justify-center mb-4'>
            <Database className='w-7 h-7 theme-text-muted' />
          </div>
          <h3 className='text-base font-medium theme-text-primary mb-1'>暂无模型配置</h3>
          <p className='theme-text-muted text-sm mb-5'>添加你的第一个 AI 模型</p>
          <button
            onClick={handleOpenAddModal}
            className='px-4 py-2 theme-bg-hover/50 hover:theme-bg-hover theme-text-secondary rounded-lg transition-colors text-sm'
          >
            开始添加
          </button>
        </div>
      ) : (
        <div className='theme-bg-sidebar/80 backdrop-blur-xl rounded-2xl p-4 border-0 shadow-[0_2px_8px_rgba(0,0,0,0.15),0_4px_16px_rgba(0,0,0,0.1)]'>
          <div className='space-y-4 sm:space-y-6 max-h-[calc(100vh-340px)] overflow-y-auto scrollbar-hidden'>
            {PROVIDERS.map((provider) => {
              const providerConfigs = groupedConfigs[provider.type]
              if (providerConfigs.length === 0) return null

              return (
                <div key={provider.type}>
                  <div className='flex items-center gap-2.5 mb-3'>
                    <div
                      className={`w-7 h-7 rounded-lg ${provider.color} flex items-center justify-center text-white flex-shrink-0`}
                    >
                      <span className='text-sm'>{provider.icon}</span>
                    </div>
                    <h3 className='text-sm font-medium theme-text-secondary'>
                      {provider.displayName}
                    </h3>
                    <span className='text-xs theme-text-muted/70 theme-bg-input px-2 py-0.5 rounded-full'>
                      {providerConfigs.length}
                    </span>
                  </div>
                  <div className='space-y-2'>
                    {providerConfigs.map((config) => {
                      return (
                        <div
                          key={config.id}
                          className='group flex items-center justify-between p-4 theme-bg-input rounded-xl border theme-border-primary hover:border-theme-border-secondary transition-colors w-full'
                        >
                          <div className='min-w-0 flex-1'>
                            <h4 className='text-sm font-medium theme-text-primary truncate'>
                              {config.name}
                            </h4>
                            <div className='flex items-center gap-2 mt-0.5'>
                              <p className='text-xs theme-text-muted font-mono truncate max-w-[200px] sm:max-w-[250px]'>
                                {config.modelId}
                              </p>
                              <button
                                onClick={() => handleCopy(config.modelId, '模型 ID')}
                                className='p-0.5 theme-text-muted/70 hover:theme-text-muted rounded transition-colors flex-shrink-0'
                                title='复制模型 ID'
                              >
                                <Copy className='w-3 h-3' />
                              </button>
                            </div>
                          </div>

                          <div className='flex items-center gap-2 sm:gap-3 flex-shrink-0'>
                            <button
                              onClick={() => handleToggleEnabled(config)}
                              disabled={updatingId === config.id}
                              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-[var(--accent-sky)]/30 ${
                                config.enabled ? 'bg-[var(--brand-success)]' : 'theme-bg-hover'
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
                              className='p-2 theme-text-muted/70 hover:theme-text-secondary hover:theme-bg-hover hover:scale-110 rounded-lg transition-all duration-200'
                              title='编辑'
                            >
                              <Edit2 className='w-4 h-4' />
                            </button>
                            <button
                              onClick={() => handleDelete(config.id, config.name)}
                              className='p-2 theme-text-muted/70 hover:text-[var(--brand-danger)] hover:bg-[var(--brand-danger)]/10 hover:scale-110 rounded-lg transition-all duration-200'
                              title='删除'
                            >
                              <Trash2 className='w-4 h-4' />
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
        </div>
      )}

      {showAddModal && (
        <div className='fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4'>
          <div className='theme-bg-card rounded-xl w-full max-w-lg border theme-border-primary max-h-[90vh] flex flex-col'>
            <div className='flex items-center justify-between p-4 border-b theme-border-primary flex-shrink-0'>
              <h3 className='text-base font-semibold theme-text-primary'>
                {editingConfig ? '编辑模型' : '添加模型'}
              </h3>
              <button
                onClick={() => setShowAddModal(false)}
                className='p-1.5 theme-text-muted hover:theme-text-primary hover:theme-bg-hover rounded-lg transition-colors'
              >
                <X className='w-5 h-5' />
              </button>
            </div>

            <div className='p-4 space-y-4 overflow-y-auto'>
              <div>
                <label className='block text-sm font-medium theme-text-secondary mb-3'>
                  服务商
                </label>
                <div className='grid grid-cols-3 gap-3'>
                  {PROVIDERS.map((provider) => (
                    <button
                      key={provider.type}
                      onClick={() => handleProviderChange(provider.type)}
                      className={`relative p-3 rounded-lg border transition-all ${
                        selectedProvider === provider.type
                          ? 'border-[var(--accent-sky)]/50 bg-[var(--accent-sky)]/10'
                          : 'theme-border-primary hover:theme-border-primary/80'
                      }`}
                    >
                      <div className='text-sm font-medium theme-text-primary mb-1'>
                        <span
                          className={`w-6 h-6 rounded ${provider.color} flex items-center justify-center text-white text-xs flex-shrink-0 inline mr-2`}
                        >
                          {provider.icon}
                        </span>
                        {provider.displayName}
                      </div>
                      {selectedProvider === provider.type && (
                        <div className='absolute top-2 right-2 w-2 h-2 bg-[var(--accent-sky)] rounded-full' />
                      )}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className='block text-sm font-medium theme-text-secondary mb-1.5'>
                  名称
                </label>
                <input
                  type='text'
                  name='name'
                  value={formData.name}
                  onChange={handleChange}
                  className='w-full px-3 py-2 theme-bg-input border theme-border-primary rounded-lg theme-text-primary text-sm focus:outline-none focus:border-[var(--accent-sky)]/50 transition-colors'
                  placeholder='我的模型'
                />
              </div>

              <div>
                <label className='block text-sm font-medium theme-text-secondary mb-1.5'>
                  模型 ID
                </label>
                <input
                  type='text'
                  name='modelId'
                  value={formData.modelId}
                  onChange={handleChange}
                  className='w-full px-3 py-2 theme-bg-input border theme-border-primary rounded-lg theme-text-primary text-sm font-mono focus:outline-none focus:border-[var(--accent-sky)]/50 transition-colors'
                  placeholder='gpt-3.5-turbo'
                />
              </div>

              <div>
                <label className='block text-sm font-medium theme-text-secondary mb-1.5'>
                  API 地址
                </label>
                <input
                  type='text'
                  name='baseUrl'
                  value={formData.baseUrl}
                  onChange={handleChange}
                  className='w-full px-3 py-2 theme-bg-input border theme-border-primary rounded-lg theme-text-primary text-sm font-mono focus:outline-none focus:border-[var(--accent-sky)]/50 transition-colors'
                  placeholder='https://api.openai.com'
                />
              </div>

              <div>
                <label className='block text-sm font-medium theme-text-secondary mb-1.5'>
                  API Key
                </label>
                <input
                  type='password'
                  name='apiKey'
                  value={formData.apiKey}
                  onChange={handleChange}
                  className='w-full px-3 py-2 theme-bg-input border theme-border-primary rounded-lg theme-text-primary text-sm font-mono focus:outline-none focus:border-[var(--accent-sky)]/50 transition-colors'
                  placeholder='sk-...'
                />
                {editingConfig && (
                  <p className='text-xs theme-text-muted/70 mt-1.5'>留空保持原 API Key</p>
                )}
              </div>

              <div className='flex items-center gap-2.5'>
                <input
                  type='checkbox'
                  name='enabled'
                  id='enabled'
                  checked={formData.enabled}
                  onChange={handleChange}
                  className='w-4 h-4 rounded border-theme-border-secondary theme-bg-input text-[var(--accent-sky)] focus:ring-[var(--accent-sky)]/50'
                />
                <label htmlFor='enabled' className='text-sm theme-text-muted'>
                  启用此模型
                </label>
              </div>
            </div>

            <div className='flex justify-end gap-2 p-4 border-t theme-border-primary flex-shrink-0'>
              <button
                onClick={() => setShowAddModal(false)}
                className='px-4 py-2 theme-bg-hover rounded-lg hover:theme-bg-hover/80 transition-colors text-sm'
              >
                取消
              </button>
              <button
                onClick={handleSave}
                className='flex items-center gap-1.5 px-4 py-2 theme-bg-accent-sky text-white rounded-lg hover:bg-[var(--accent-sky)]/80 transition-colors text-sm font-medium'
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
