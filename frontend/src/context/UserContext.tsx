import { createContext, useContext, useState, useCallback, useEffect } from 'react'
import type { ReactNode } from 'react'
import type {
  UserProfile,
  UpdateProfileRequest,
  UpdatePreferencesRequest,
  UpdatePrivacyRequest,
  CreateAPIKeyRequest,
  APIKey,
} from '../types/user'
import { userApi } from '../api/user'

interface UserContextType {
  profile: UserProfile | null
  isLoading: boolean
  error: string | null
  fetchProfile: () => Promise<void>
  updateProfile: (data: UpdateProfileRequest) => Promise<void>
  updatePreferences: (data: UpdatePreferencesRequest) => Promise<void>
  updatePrivacy: (data: UpdatePrivacyRequest) => Promise<void>
  createAPIKey: (data: CreateAPIKeyRequest) => Promise<APIKey>
  deleteAPIKey: (keyId: string) => Promise<void>
}

const UserContext = createContext<UserContextType | undefined>(undefined)

const defaultProfile: UserProfile = {
  id: '1',
  nickname: '用户',
  email: 'user@example.com',
  preferences: {
    theme: 'dark',
    language: 'zh-CN',
    notifications: {
      message: true,
      email: true,
      push: false,
      sound: true,
    },
  },
  privacy: {
    onlineStatus: true,
    messageHistory: true,
    readReceipts: true,
    typingIndicator: true,
  },
  apiKeys: [],
  devices: [],
}

export function UserProvider({ children }: { children: ReactNode }) {
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchProfile = useCallback(async () => {
    try {
      setIsLoading(true)
      setError(null)
      const data = await userApi.getProfile()
      console.log('[UserContext] Profile fetched:', data)
      setProfile(data)
    } catch (err) {
      console.error('Failed to fetch profile:', err)
      setError('获取用户信息失败，使用默认配置')
      setProfile(defaultProfile)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchProfile()
  }, [fetchProfile])

  const updateProfile = useCallback(async (data: UpdateProfileRequest) => {
    try {
      setError(null)
      const updated = await userApi.updateProfile(data)
      console.log('[UserContext] Profile updated:', updated)
      setProfile(updated)
    } catch (err) {
      console.error('Failed to update profile:', err)
      setError('更新个人信息失败')
      throw err
    }
  }, [])

  const updatePreferences = useCallback(async (data: UpdatePreferencesRequest) => {
    try {
      setError(null)
      const updated = await userApi.updatePreferences(data)
      setProfile(updated)
    } catch (err) {
      console.error('Failed to update preferences:', err)
      setError('更新偏好设置失败')
      throw err
    }
  }, [])

  const updatePrivacy = useCallback(async (data: UpdatePrivacyRequest) => {
    try {
      setError(null)
      const updated = await userApi.updatePrivacy(data)
      setProfile(updated)
    } catch (err) {
      console.error('Failed to update privacy:', err)
      setError('更新隐私设置失败')
      throw err
    }
  }, [])

  const createAPIKey = useCallback(async (data: CreateAPIKeyRequest) => {
    try {
      setError(null)
      const newKey = await userApi.createAPIKey(data)
      setProfile((prev) =>
        prev
          ? {
              ...prev,
              apiKeys: [...prev.apiKeys, newKey],
            }
          : null
      )
      return newKey
    } catch (err) {
      console.error('Failed to create API key:', err)
      setError('创建API密钥失败')
      throw err
    }
  }, [])

  const deleteAPIKey = useCallback(async (keyId: string) => {
    try {
      setError(null)
      await userApi.deleteAPIKey(keyId)
      setProfile((prev) =>
        prev
          ? {
              ...prev,
              apiKeys: prev.apiKeys.filter((key) => key.id !== keyId),
            }
          : null
      )
    } catch (err) {
      console.error('Failed to delete API key:', err)
      setError('删除API密钥失败')
      throw err
    }
  }, [])

  return (
    <UserContext.Provider
      value={{
        profile,
        isLoading,
        error,
        fetchProfile,
        updateProfile,
        updatePreferences,
        updatePrivacy,
        createAPIKey,
        deleteAPIKey,
      }}
    >
      {children}
    </UserContext.Provider>
  )
}

export function useUser() {
  const context = useContext(UserContext)
  if (context === undefined) {
    throw new Error('useUser must be used within a UserProvider')
  }
  return context
}

export default UserContext
