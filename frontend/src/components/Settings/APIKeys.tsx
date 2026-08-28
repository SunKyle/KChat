import { useState } from 'react'
import { Icon } from '../common/Icon'
import { useUser } from '../../context/UserContext'
import { Modal } from '../common/Modal'
import { Button } from '../ui/Button'
import type { CreateAPIKeyRequest } from '../../types/user'

export function APIKeys() {
  const { profile, createAPIKey, deleteAPIKey, isLoading } = useUser()
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newKeyName, setNewKeyName] = useState('')
  const [copiedKey, setCopiedKey] = useState<string | null>(null)
  const [showKey, setShowKey] = useState<string | null>(null)
  const [creatingKey, setCreatingKey] = useState(false)
  const [newKey, setNewKey] = useState<string | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: string; name: string } | null>(null)

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

  const handleDeleteKey = (keyId: string, keyName: string) => {
    setDeleteConfirm({ id: keyId, name: keyName })
  }

  const confirmDeleteKey = async () => {
    if (!deleteConfirm) return
    try {
      await deleteAPIKey(deleteConfirm.id)
    } catch (err) {
      console.error('Failed to delete API key:', err)
    } finally {
      setDeleteConfirm(null)
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
          <Icon name='Key' size='lg' className='theme-text-muted' />
          <h3 className='font-semibold theme-text-primary'>API 密钥</h3>
        </div>
        <Button
          onClick={() => setShowCreateModal(true)}
          disabled={isLoading}
          className='disabled:opacity-50 disabled:cursor-not-allowed'
        >
          <Icon name='Plus' size='md' />
          生成密钥
        </Button>
      </div>

      {profile.apiKeys.length === 0 ? (
        <div className='card-float-solid rounded-2xl p-8 text-center'>
          <Icon name='Key' size={48} className='theme-text-muted mx-auto mb-4' />
          <p className='theme-text-secondary mb-2'>暂无 API 密钥</p>
          <p className='text-sm theme-text-muted mb-4'>创建 API 密钥以通过编程方式访问您的数据</p>
          <Button
            onClick={() => setShowCreateModal(true)}
            disabled={isLoading}
            className='disabled:opacity-50'
          >
            <Icon name='Plus' size='md' />
            生成密钥
          </Button>
        </div>
      ) : (
        <div className='space-y-3'>
          {profile.apiKeys.map((apiKey) => (
            <div key={apiKey.id} className='card-float-solid rounded-2xl p-4'>
              <div className='flex items-center justify-between mb-3'>
                <div>
                  <div className='font-semibold theme-text-primary'>{apiKey.name}</div>
                  <div className='text-xs theme-text-muted'>
                    创建于 {formatDate(apiKey.createdAt)}
                  </div>
                </div>
                <div className='flex items-center gap-2'>
                  {showKey === apiKey.id ? (
                    <button onClick={() => setShowKey(null)} className='icon-btn' title='隐藏密钥'>
                      <Icon name='EyeOff' size='md' className='theme-text-muted' />
                    </button>
                  ) : (
                    <button
                      onClick={() => setShowKey(apiKey.id)}
                      className='icon-btn'
                      title='显示密钥'
                    >
                      <Icon name='Eye' size='md' className='theme-text-muted' />
                    </button>
                  )}
                  <button
                    onClick={() => handleCopyKey(apiKey.key, apiKey.id)}
                    className='icon-btn'
                    title='复制密钥'
                  >
                    {copiedKey === apiKey.id ? (
                      <Icon name='Eye' size='md' className='text-green-400' />
                    ) : (
                      <Icon name='Copy' size='md' className='theme-text-muted' />
                    )}
                  </button>
                  <button
                    onClick={() => handleDeleteKey(apiKey.id, apiKey.name)}
                    className='icon-btn hover:text-red-400'
                    title='删除密钥'
                  >
                    <Icon name='Trash2' size='md' className='theme-text-muted' />
                  </button>
                </div>
              </div>
              <div className='font-mono text-sm bg-[var(--bg-code)]/50 rounded-lg px-3 py-2 break-all'>
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
        <div className='fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4'>
          <div className='theme-bg-card rounded-2xl border theme-border-primary w-full max-w-md overflow-hidden'>
            <div className='px-6 py-4 border-b theme-border-primary'>
              <h3 className='text-lg font-semibold theme-text-primary'>生成 API 密钥</h3>
            </div>
            <div className='p-6'>
              {newKey ? (
                <div className='space-y-4'>
                  <div className='flex items-start gap-2 p-3 rounded-lg bg-amber-500/10 border border-amber-500/30'>
                    <Icon name='AlertTriangle' size='lg' className='text-amber-500 flex-shrink-0 mt-0.5' />
                    <div>
                      <div className='text-sm font-semibold text-amber-600'>安全提醒</div>
                      <div className='text-xs text-amber-500'>
                        请务必立即复制您的新 API 密钥。出于安全考虑，我们不会再次显示完整密钥。
                      </div>
                    </div>
                  </div>
                  <div className='font-mono text-sm bg-[var(--bg-code)]/50 rounded-lg px-3 py-3 break-all'>
                    {newKey}
                  </div>
                  <button
                    onClick={() => {
                      handleCopyKey(newKey, 'new')
                    }}
                    className='w-full flex items-center justify-center gap-2 px-4 py-2 theme-bg-accent-primary text-white rounded-lg hover:bg-[var(--accent-primary)]/80 transition-colors text-sm font-semibold'
                  >
                    {copiedKey === 'new' ? (
                      <Icon name='Eye' size='md' />
                    ) : (
                      <Icon name='Copy' size='md' />
                    )}
                    {copiedKey === 'new' ? '已复制' : '复制密钥'}
                  </button>
                </div>
              ) : (
                <div className='space-y-4'>
                  <div>
                    <label className='block text-sm font-semibold theme-text-secondary mb-1.5'>
                      密钥名称
                    </label>
                    <input
                      type='text'
                      value={newKeyName}
                      onChange={(e) => setNewKeyName(e.target.value)}
                      className='input-field'
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
                      className='flex-1 flex items-center justify-center gap-2 px-4 py-2 theme-bg-accent-primary text-white rounded-lg hover:bg-[var(--accent-primary)]/80 transition-colors text-sm font-semibold disabled:opacity-50 disabled:cursor-not-allowed'
                    >
                      {creatingKey ? (
                        <Icon name='Loader2' size='md' className='animate-spin' />
                      ) : (
                        <Icon name='Plus' size='md' />
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

      <Modal
        isOpen={deleteConfirm !== null}
        title='删除 API 密钥'
        message={
          deleteConfirm ? `确定要删除 API 密钥 "${deleteConfirm.name}" 吗？此操作不可撤销。` : ''
        }
        confirmText='删除'
        cancelText='取消'
        onConfirm={confirmDeleteKey}
        onClose={() => setDeleteConfirm(null)}
        type='danger'
      />
    </div>
  )
}
