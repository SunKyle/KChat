import { useState, useEffect } from 'react'
import { Icon } from '../common/Icon'
import { useUser } from '../../context/UserContext'
import { images } from '../../api'
import { Button } from '../ui/Button'

export function ProfileInfo() {
  const { profile, updateProfile, isLoading } = useUser()
  const [editing, setEditing] = useState(false)
  const [avatarUploading, setAvatarUploading] = useState(false)
  const [localProfile, setLocalProfile] = useState({
    nickname: '',
    email: '',
    bio: '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (profile) {
      setLocalProfile({
        nickname: profile.nickname,
        email: profile.email,
        bio: profile.bio || '',
      })
    }
  }, [profile])

  const validateForm = () => {
    const newErrors: Record<string, string> = {}

    if (!localProfile.nickname.trim()) {
      newErrors.nickname = '请输入昵称'
    } else if (localProfile.nickname.length > 50) {
      newErrors.nickname = '昵称不能超过50个字符'
    }

    if (!localProfile.email.trim()) {
      newErrors.email = '请输入邮箱'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(localProfile.email)) {
      newErrors.email = '请输入有效的邮箱地址'
    }

    if (localProfile.bio && localProfile.bio.length > 200) {
      newErrors.bio = '简介不能超过200个字符'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSave = async () => {
    if (!validateForm()) return

    try {
      await updateProfile({
        nickname: localProfile.nickname.trim(),
        email: localProfile.email.trim(),
        bio: localProfile.bio.trim() || undefined,
      })
      setEditing(false)
    } catch (err) {
      console.error('Failed to save profile:', err)
    }
  }

  const handleAvatarUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      alert('请选择图片文件')
      return
    }

    // 验证文件大小 (5MB)
    if (file.size > 5 * 1024 * 1024) {
      alert('图片大小不能超过5MB')
      return
    }

    setAvatarUploading(true)
    try {
      const result = await images.upload(file)

      await updateProfile({ avatar: result.url })
    } catch (err) {
      console.error('Failed to upload avatar:', err)
      alert('头像上传失败，请重试')
    } finally {
      setAvatarUploading(false)
    }
  }

  if (!profile) return null

  return (
    <div className='space-y-6'>
      <div className='flex items-center gap-4'>
        <div className='relative'>
          <div className='w-20 h-20 rounded-full overflow-hidden ring-2 ring-[var(--border-primary)] flex items-center justify-center'>
            {profile.avatar ? (
              <img src={profile.avatar} alt='Avatar' className='w-full h-full object-cover' />
            ) : (
              <Icon name='User' size={40} className='theme-text-muted' />
            )}
          </div>
          <label className='absolute -bottom-1 -right-1 w-7 h-7 theme-bg-accent-primary rounded-full flex items-center justify-center cursor-pointer hover:bg-[var(--accent-primary)]/80 transition-colors'>
            <Icon name='Camera' size='md' className='text-white' />
            <input
              type='file'
              accept='image/*'
              onChange={handleAvatarUpload}
              disabled={avatarUploading}
              className='hidden'
            />
          </label>
          {avatarUploading && (
            <div className='absolute inset-0 theme-bg-overlay rounded-full flex items-center justify-center'>
              <Icon name='Loader2' size='xl' className='text-white animate-spin' />
            </div>
          )}
        </div>
        <div>
          <h2 className='text-xl font-semibold theme-text-primary'>{profile.nickname}</h2>
          <p className='text-sm theme-text-muted'>{profile.email}</p>
        </div>
      </div>

      <div className='card-float-solid rounded-2xl p-6'>
        <div className='flex items-center justify-between mb-4'>
          <h3 className='font-semibold theme-text-primary'>基本信息</h3>
          {editing ? (
            <div className='flex items-center gap-2'>
              <Button
                onClick={handleSave}
                disabled={isLoading}
                className='disabled:opacity-50 disabled:cursor-not-allowed'
              >
                {isLoading ? (
                  <Icon name='Loader2' size='sm' className='animate-spin' />
                ) : (
                  <Icon name='Save' size='sm' />
                )}
                保存
              </Button>
              <Button
                variant='ghost'
                onClick={() => {
                  setEditing(false)
                  setLocalProfile({
                    nickname: profile.nickname,
                    email: profile.email,
                    bio: profile.bio || '',
                  })
                  setErrors({})
                }}
                className='text-sm'
              >
                <Icon name='X' size='sm' />
                取消
              </Button>
            </div>
          ) : (
            <Button variant='ghost' onClick={() => setEditing(true)} className='text-sm'>
              编辑
            </Button>
          )}
        </div>

        <div className='space-y-4'>
          <div>
            <label className='block font-secondary font-weight-semibold mb-1.5'>昵称</label>
            <input
              type='text'
              value={localProfile.nickname}
              onChange={(e) => setLocalProfile((prev) => ({ ...prev, nickname: e.target.value }))}
              disabled={!editing}
              className={`input-field ${
                errors.nickname ? 'border-[var(--brand-danger)]/50' : ''
              } disabled:opacity-60`}
              placeholder='请输入昵称'
            />
            {errors.nickname && (
              <p className='mt-1 text-xs theme-brand-danger'>{errors.nickname}</p>
            )}
          </div>

          <div>
            <label className='block font-secondary font-weight-semibold mb-1.5'>邮箱</label>
            <input
              type='email'
              value={localProfile.email}
              onChange={(e) => setLocalProfile((prev) => ({ ...prev, email: e.target.value }))}
              disabled={!editing}
              className={`input-field ${
                errors.email ? 'border-[var(--brand-danger)]/50' : ''
              } disabled:opacity-60`}
              placeholder='请输入邮箱'
            />
            {errors.email && <p className='mt-1 text-xs theme-brand-danger'>{errors.email}</p>}
          </div>

          <div>
            <label className='block font-secondary font-weight-semibold mb-1.5'>简介</label>
            <textarea
              value={localProfile.bio}
              onChange={(e) => setLocalProfile((prev) => ({ ...prev, bio: e.target.value }))}
              disabled={!editing}
              rows={3}
              className={`input-field resize-none ${
                errors.bio ? 'border-[var(--brand-danger)]/50' : ''
              } disabled:opacity-60`}
              placeholder='介绍一下你自己...'
            />
            {errors.bio && <p className='mt-1 text-xs theme-brand-danger'>{errors.bio}</p>}
          </div>
        </div>
      </div>
    </div>
  )
}
