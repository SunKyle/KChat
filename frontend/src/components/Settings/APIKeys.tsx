import { useState } from 'react'
import { Key, Plus, Copy, Trash2, Eye, EyeOff, Loader2, AlertTriangle } from 'lucide-react'
import { useUser } from '../../context/UserContext'
import type { CreateAPIKeyRequest } from '../../types/user'

export function APIKeys() {
  const { profile, createAPIKey, deleteAPIKey, isLoading } = useUser()
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newKeyName, setNewKeyName] = useState('')
  const [copiedKey, setCopiedKey] = useState<string | null>(null)
  const [showKey, setShowKey] = useState<string | null>(null)
  const [creatingKey, setCreatingKey] = useState(false)
  const [newKey, setNewKey] = useState<string | null>(null)

  const handleCreateKey = async () => {
    if (!newKeyName.trim()) return

    setCreatingKey(true)
    try {
      const scopes = ['chat', 'models', 'memory']
      const request: CreateAPIKeyRequest = {
        name: newKeyName.trim(),
        scopes,
      }
      const result = await createAPIKey(request)
      setNewKey(result.key)
    } catch (err) {
      console.error('Failed to create API key:', err)
    } finally {
      setCreatingKey(false)
    }
  }

  const handleCopyKey = async (key: string, keyId: string) => {
    try {
      await navigator.clipboard.writeText(key)
      setCopiedKey(keyId)
      setTimeout(() => setCopiedKey(null), 2000)
    } catch (err) {
      console.error('Failed to copy key:', err)
    }
  }

  const handleDeleteKey = async (keyId: string, keyName: string) => {
    if (!confirm(`确定要删除 API 密钥 "${keyName}" 吗？此操作不可撤销。`)) {
      return
    }
    try {
      await deleteAPIKey(keyId)
    } catch (err) {
      console.error('Failed to delete API key:', err)
    }
  }

  const formatKey = (key: string) => {
    return `${key.slice(0, 8)}${'*'.repeat(24)}${key.slice(-4)}`
  }

  const formatDate = (dateString: string) => {
    const date = new Date(dateString)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  }

  if (!profile) return null

  return (
    <div className='space-y-6'>
      <div className='flex items-center justify-between'>
        <div className='flex items-center gap-2'>
          <Key className='w-5 h-5 theme-text-muted' />
          <h3 className='font-medium theme-text-primary'>API 密钥</h3>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          disabled={isLoading}
          className='flex items-center gap-1.5 px-4 py-2 theme-bg-accent-sky text-white rounded-lg hover:bg-[var(--accent-sky)]/80 transition-colors text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed'
        >
          <Plus className='w-4 h-4' />
          生成密钥
        </button>
      </div>

      {profile.apiKeys.length === 0 ? (
        <div className='theme-bg-sidebar/80 backdrop-blur-xl rounded-2xl p-8 border-0 shadow-[0_2px_8px_rgba(0,0,0,0.15),0_4px_16px_rgba(0,0,0,0.1)] hover:theme-bg-sidebar hover:shadow-[0_4px_12px_rgba(0,0,0,0.18),0_8px_20px_rgba(0,0,0,0.12)] transition-all duration-200 ease-out text-center'>
          <Key className='w-12 h-12 theme-text-muted mx-auto mb-4' />
          <p className='theme-text-secondary mb-2'>暂无 API 密钥</p>
          <p className='text-sm theme-text-muted mb-4'>创建 API 密钥以通过编程方式访问您的数据</p>
          <button
            onClick={() => setShowCreateModal(true)}
            disabled={isLoading}
            className='inline-flex items-center gap-1.5 px-4 py-2 theme-bg-accent-sky text-white rounded-lg hover:bg-[var(--accent-sky)]/80 transition-colors text-sm font-medium disabled:opacity-50'
          >
            <Plus className='w-4 h-4' />
            生成密钥
          </button>
        </div>
      ) : (
        <div className='space-y-3'>
          {profile.apiKeys.map((apiKey) => (
            <div
              key={apiKey.id}
              className='theme-bg-sidebar/80 backdrop-blur-xl rounded-2xl p-4 border-0 shadow-[0_2px_8px_rgba(0,0,0,0.15),0_4px_16px_rgba(0,0,0,0.1)] hover:theme-bg-sidebar hover:shadow-[0_4px_12px_rgba(0,0,0,0.18),0_8px_20px_rgba(0,0,0,0.12)] transition-all duration-200 ease-out'
            >
              <div className='flex items-center justify-between mb-3'>
                <div>
                  <div className='font-medium theme-text-primary'>{apiKey.name}</div>
                  <div className='text-xs theme-text-muted'>
                    创建于 {formatDate(apiKey.createdAt)}
                  </div>
                </div>
                <div className='flex items-center gap-2'>
                  {showKey === apiKey.id ? (
                    <button
                      onClick={() => setShowKey(null)}
                      className='p-2 rounded-lg theme-bg-hover hover:theme-bg-hover/80 transition-colors'
                      title='隐藏密钥'
                    >
                      <EyeOff className='w-4 h-4 theme-text-muted' />
                    </button>
                  ) : (
                    <button
                      onClick={() => setShowKey(apiKey.id)}
                      className='p-2 rounded-lg theme-bg-hover hover:theme-bg-hover/80 transition-colors'
                      title='显示密钥'
                    >
                      <Eye className='w-4 h-4 theme-text-muted' />
                    </button>
                  )}
                  <button
                    onClick={() => handleCopyKey(apiKey.key, apiKey.id)}
                    className='p-2 rounded-lg theme-bg-hover hover:theme-bg-hover/80 transition-colors'
                    title='复制密钥'
                  >
                    {copiedKey === apiKey.id ? (
                      <Eye className='w-4 h-4 text-green-400' />
                    ) : (
                      <Copy className='w-4 h-4 theme-text-muted' />
                    )}
                  </button>
                  <button
                    onClick={() => handleDeleteKey(apiKey.id, apiKey.name)}
                    className='p-2 rounded-lg theme-bg-hover hover:theme-bg-hover/80 hover:text-red-400 transition-colors'
                    title='删除密钥'
                  >
                    <Trash2 className='w-4 h-4 theme-text-muted' />
                  </button>
                </div>
              </div>
              <div className='font-mono text-sm bg-slate-900/50 rounded-lg px-3 py-2 break-all'>
                {showKey === apiKey.id ? apiKey.key : formatKey(apiKey.key)}
              </div>
              <div className='flex flex-wrap gap-2 mt-3'>
                {apiKey.scopes.map((scope) => (
                  <span
                    key={scope}
                    className='text-xs px-2 py-0.5 rounded-full theme-bg-hover text-capitalize'
                  >
                    {scope}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {showCreateModal && (
        <div className='fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4'>
          <div className='theme-bg-card rounded-xl border theme-border-primary w-full max-w-md overflow-hidden'>
            <div className='px-6 py-4 border-b theme-border-primary'>
              <h3 className='text-lg font-semibold theme-text-primary'>生成 API 密钥</h3>
            </div>
            <div className='p-6'>
              {newKey ? (
                <div className='space-y-4'>
                  <div className='flex items-start gap-2 p-3 rounded-lg bg-amber-500/10 border border-amber-500/30'>
                    <AlertTriangle className='w-5 h-5 text-amber-500 flex-shrink-0 mt-0.5' />
                    <div>
                      <div className='text-sm font-medium text-amber-600'>安全提醒</div>
                      <div className='text-xs text-amber-500'>
                        请务必立即复制您的新 API 密钥。出于安全考虑，我们不会再次显示完整密钥。
                      </div>
                    </div>
                  </div>
                  <div className='font-mono text-sm bg-slate-900/50 rounded-lg px-3 py-3 break-all'>
                    {newKey}
                  </div>
                  <button
                    onClick={() => {
                      handleCopyKey(newKey, 'new')
                    }}
                    className='w-full flex items-center justify-center gap-2 px-4 py-2 theme-bg-accent-sky text-white rounded-lg hover:bg-[var(--accent-sky)]/80 transition-colors text-sm font-medium'
                  >
                    {copiedKey === 'new' ? (
                      <Eye className='w-4 h-4' />
                    ) : (
                      <Copy className='w-4 h-4' />
                    )}
                    {copiedKey === 'new' ? '已复制' : '复制密钥'}
                  </button>
                </div>
              ) : (
                <div className='space-y-4'>
                  <div>
                    <label className='block text-sm font-medium theme-text-secondary mb-1.5'>
                      密钥名称
                    </label>
                    <input
                      type='text'
                      value={newKeyName}
                      onChange={(e) => setNewKeyName(e.target.value)}
                      className='w-full px-3 py-2 rounded-lg theme-bg-input border theme-border-primary theme-text-primary focus:outline-none focus:border-sky-500/50'
                      placeholder='输入密钥名称'
                    />
                  </div>
                  <div className='flex gap-3'>
                    <button
                      onClick={() => {
                        setShowCreateModal(false)
                        setNewKeyName('')
                      }}
                      className='flex-1 px-4 py-2 theme-bg-hover rounded-lg hover:theme-bg-hover/80 transition-colors'
                    >
                      取消
                    </button>
                    <button
                      onClick={handleCreateKey}
                      disabled={!newKeyName.trim() || creatingKey}
                      className='flex-1 flex items-center justify-center gap-2 px-4 py-2 theme-bg-accent-sky text-white rounded-lg hover:bg-[var(--accent-sky)]/80 transition-colors text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed'
                    >
                      {creatingKey ? (
                        <Loader2 className='w-4 h-4 animate-spin' />
                      ) : (
                        <Plus className='w-4 h-4' />
                      )}
                      生成
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
