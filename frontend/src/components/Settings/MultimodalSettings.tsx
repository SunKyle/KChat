import { useEffect, useState } from 'react'
import { Loader2, Save } from 'lucide-react'
import { multimodalApi } from '../../api/user'
import { models } from '../../api/models'
import type { MultimodalConfig } from '../../types/user'
import { useToast } from '../../hooks/useToast'

const FIELD_OPTIONS = [
  { key: 'plannerModel', label: '规划模型（Planner）', hint: '负责分析输入并生成多模态计划，建议选快且 JSON 稳定的模型', capabilities: ['TEXT_IN', 'TEXT_OUT'] },
  { key: 'visionModel', label: '图片理解模型（Vision）', hint: '负责理解上传的图片，支持 IMAGE_IN 能力', capabilities: ['IMAGE_IN'] },
  { key: 'imageModel', label: '文生图模型（Image Gen）', hint: '负责生成图片，支持 IMAGE_OUT 能力', capabilities: ['IMAGE_OUT'] },
  { key: 'textModel', label: '文本回答模型（Text）', hint: '负责普通文本回答，支持 TEXT_IN/TEXT_OUT', capabilities: ['TEXT_IN', 'TEXT_OUT'] },
] as const

export function MultimodalSettings() {
  const [config, setConfig] = useState<MultimodalConfig | null>(null)
  const [modelOptions, setModelOptions] = useState<string[]>([])
  const [capabilitiesByModel, setCapabilitiesByModel] = useState<Record<string, string[]>>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const { success: toastSuccess, error: toastError } = useToast()

  useEffect(() => {
    Promise.all([multimodalApi.get(), models.list(), models.capabilities()])
      .then(([savedConfig, savedModels, savedCapabilities]) => {
        setConfig(savedConfig)
        setModelOptions(savedModels)
        setCapabilitiesByModel(
          Object.fromEntries(savedCapabilities.map((item) => [item.model, item.capabilities]))
        )
      })
      .catch(() => toastError('加载多模态配置失败'))
      .finally(() => setLoading(false))
  }, [])

  const updateField = (key: keyof MultimodalConfig, value: string | number) => {
    setConfig((prev) => (prev ? { ...prev, [key]: value } : prev))
  }

  const handleSave = async () => {
    if (!config) return
    setSaving(true)
    try {
      const updated = await multimodalApi.update(config)
      setConfig(updated)
      toastSuccess('多模态模型配置已保存')
    } catch (err) {
      console.error('Failed to save multimodal config:', err)
      toastError('保存多模态配置失败')
    } finally {
      setSaving(false)
    }
  }

  const filteredOptions = (requiredCapabilities: readonly string[]) =>
    modelOptions.filter((model) =>
      requiredCapabilities.every((capability) =>
        capabilitiesByModel[model]?.includes(capability)
      )
    )

  if (loading) {
    return (
      <div className='flex items-center justify-center min-h-[300px]'>
        <Loader2 className='w-6 h-6 theme-text-muted animate-spin' />
      </div>
    )
  }

  if (!config) return null

  return (
    <div className='space-y-6'>
      <div className='card-float-solid rounded-2xl p-6'>
        <h2 className='font-h3 mb-1'>多模态模型配置</h2>
        <p className='font-secondary theme-text-muted mb-6'>
          留空表示自动选择：优先使用全局配置，再按模型能力自动挑选。
        </p>

        <div className='space-y-5'>
          {FIELD_OPTIONS.map(({ key, label, hint, capabilities }) => (
            <div key={key}>
              <label className='block font-semibold text-sm mb-1.5'>{label}</label>
              <select
                value={config[key] || ''}
                onChange={(e) => updateField(key, e.target.value)}
                className='w-full px-3 py-2 rounded-lg border theme-border-primary theme-bg-card theme-text-primary focus:outline-none focus:border-[var(--brand-primary)]'
              >
                <option value=''>自动选择</option>
                {filteredOptions(capabilities).map((model) => (
                  <option key={model} value={model}>
                    {model}
                  </option>
                ))}
              </select>
              <p className='text-xs theme-text-muted mt-1.5'>{hint}</p>
            </div>
          ))}

          <div>
            <label className='block font-semibold text-sm mb-1.5'>最大执行步骤数</label>
            <input
              type='number'
              min={1}
              max={10}
              value={config.maxSteps}
              onChange={(e) => updateField('maxSteps', Number(e.target.value))}
              className='w-32 px-3 py-2 rounded-lg border theme-border-primary theme-bg-card theme-text-primary focus:outline-none focus:border-[var(--brand-primary)]'
            />
            <p className='text-xs theme-text-muted mt-1.5'>单次多模态任务最多执行的模型步骤数，建议 1-10。</p>
          </div>
        </div>

        <div className='mt-6 flex justify-end'>
          <button
            onClick={handleSave}
            disabled={saving}
            className='flex items-center gap-2 px-4 py-2 rounded-lg bg-[var(--brand-primary)] text-white font-semibold disabled:opacity-50'
          >
            {saving ? <Loader2 className='w-4 h-4 animate-spin' /> : <Save className='w-4 h-4' />}
            保存配置
          </button>
        </div>
      </div>
    </div>
  )
}
