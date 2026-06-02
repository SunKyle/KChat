import { useState, useEffect } from 'react'
import { Camera, User, Save, X, Loader2 } from 'lucide-react'
import { useUser } from '../../context/UserContext'

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

    setAvatarUploading(true)
    try {
      const formData = new FormData()
      formData.append('avatar', file)
      await updateProfile({ avatar: URL.createObjectURL(file) })
    } catch (err) {
      console.error('Failed to upload avatar:', err)
    } finally {
      setAvatarUploading(false)
    }
  }

  if (!profile) return null

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <div className="relative">
          <div className="w-20 h-20 rounded-full overflow-hidden theme-border-primary border-2 flex items-center justify-center">
            {profile.avatar ? (
              <img
                src={profile.avatar}
                alt="Avatar"
                className="w-full h-full object-cover"
              />
            ) : (
              <User className="w-10 h-10 theme-text-muted" />
            )}
          </div>
          <label className="absolute -bottom-1 -right-1 w-7 h-7 bg-sky-500 rounded-full flex items-center justify-center cursor-pointer hover:bg-sky-400 transition-colors">
            <Camera className="w-4 h-4 text-white" />
            <input
              type="file"
              accept="image/*"
              onChange={handleAvatarUpload}
              disabled={avatarUploading}
              className="hidden"
            />
          </label>
          {avatarUploading && (
            <div className="absolute inset-0 bg-black/50 rounded-full flex items-center justify-center">
              <Loader2 className="w-6 h-6 text-white animate-spin" />
            </div>
          )}
        </div>
        <div>
          <h2 className="text-xl font-semibold theme-text-primary">
            {profile.nickname}
          </h2>
          <p className="text-sm theme-text-muted">{profile.email}</p>
        </div>
      </div>

      <div className="theme-bg-card rounded-xl p-6 border theme-border-primary">
        <div className="flex items-center justify-between mb-4">
          <h3 className="font-medium theme-text-primary">基本信息</h3>
          {editing ? (
            <div className="flex items-center gap-2">
              <button
                onClick={handleSave}
                disabled={isLoading}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-sky-500 text-white rounded-lg hover:bg-sky-400 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isLoading ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Save className="w-3.5 h-3.5" />
                )}
                保存
              </button>
              <button
                onClick={() => {
                  setEditing(false)
                  setLocalProfile({
                    nickname: profile.nickname,
                    email: profile.email,
                    bio: profile.bio || '',
                  })
                  setErrors({})
                }}
                className="flex items-center gap-1.5 px-3 py-1.5 theme-bg-hover rounded-lg hover:theme-bg-hover/80 transition-colors"
              >
                <X className="w-3.5 h-3.5" />
                取消
              </button>
            </div>
          ) : (
            <button
              onClick={() => setEditing(true)}
              className="px-3 py-1.5 theme-bg-hover rounded-lg hover:theme-bg-hover/80 transition-colors text-sm"
            >
              编辑
            </button>
          )}
        </div>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium theme-text-secondary mb-1.5">
              昵称
            </label>
            <input
              type="text"
              value={localProfile.nickname}
              onChange={(e) => setLocalProfile((prev) => ({ ...prev, nickname: e.target.value }))}
              disabled={!editing}
              className={`w-full px-3 py-2 rounded-lg theme-bg-input border ${
                errors.nickname ? 'border-red-500/50' : 'theme-border-primary'
              } theme-text-primary focus:outline-none focus:border-sky-500/50 disabled:opacity-60`}
              placeholder="请输入昵称"
            />
            {errors.nickname && (
              <p className="mt-1 text-xs text-red-400">{errors.nickname}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium theme-text-secondary mb-1.5">
              邮箱
            </label>
            <input
              type="email"
              value={localProfile.email}
              onChange={(e) => setLocalProfile((prev) => ({ ...prev, email: e.target.value }))}
              disabled={!editing}
              className={`w-full px-3 py-2 rounded-lg theme-bg-input border ${
                errors.email ? 'border-red-500/50' : 'theme-border-primary'
              } theme-text-primary focus:outline-none focus:border-sky-500/50 disabled:opacity-60`}
              placeholder="请输入邮箱"
            />
            {errors.email && (
              <p className="mt-1 text-xs text-red-400">{errors.email}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium theme-text-secondary mb-1.5">
              简介
            </label>
            <textarea
              value={localProfile.bio}
              onChange={(e) => setLocalProfile((prev) => ({ ...prev, bio: e.target.value }))}
              disabled={!editing}
              rows={3}
              className={`w-full px-3 py-2 rounded-lg theme-bg-input border ${
                errors.bio ? 'border-red-500/50' : 'theme-border-primary'
              } theme-text-primary focus:outline-none focus:border-sky-500/50 disabled:opacity-60 resize-none`}
              placeholder="介绍一下你自己..."
            />
            {errors.bio && (
              <p className="mt-1 text-xs text-red-400">{errors.bio}</p>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}