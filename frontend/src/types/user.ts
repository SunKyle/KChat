export interface UserProfile {
  id: string
  nickname: string
  avatar?: string
  email: string
  bio?: string
  preferences: UserPreferences
  privacy: UserPrivacy
  apiKeys: APIKey[]
  devices: UserDevice[]
}

export interface UserPreferences {
  theme: 'dark' | 'light' | 'system'
  language: string
  notifications: NotificationSettings
}

export interface NotificationSettings {
  message: boolean
  email: boolean
  push: boolean
  sound: boolean
}

export interface UserPrivacy {
  onlineStatus: boolean
  messageHistory: boolean
  readReceipts: boolean
  typingIndicator: boolean
}

export interface APIKey {
  id: string
  name: string
  key: string
  createdAt: string
  lastUsed?: string
  scopes: string[]
}

export interface UserDevice {
  id: string
  name: string
  type: 'desktop' | 'mobile' | 'tablet' | 'other'
  ipAddress: string
  location?: string
  lastActive: string
}

export interface UpdateProfileRequest {
  nickname?: string
  avatar?: string
  email?: string
  bio?: string
}

export interface UpdatePreferencesRequest {
  theme?: 'dark' | 'light' | 'system'
  language?: string
  notifications?: Partial<NotificationSettings>
}

export interface UpdatePrivacyRequest {
  onlineStatus?: boolean
  messageHistory?: boolean
  readReceipts?: boolean
  typingIndicator?: boolean
}

export interface CreateAPIKeyRequest {
  name: string
  scopes: string[]
}
