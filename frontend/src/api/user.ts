import { request } from './client'
import type {
  UserProfile,
  UpdateProfileRequest,
  UpdatePreferencesRequest,
  UpdatePrivacyRequest,
  CreateAPIKeyRequest,
  APIKey,
} from '../types/user'

// 默认用户ID
const DEFAULT_USER_ID = 'default'

export const userApi = {
  getProfile: async (userId: string = DEFAULT_USER_ID): Promise<UserProfile> => {
    return request(`/user/profile?userId=${userId}`)
  },

  updateProfile: async (data: UpdateProfileRequest, userId: string = DEFAULT_USER_ID): Promise<UserProfile> => {
    return request(`/user/profile?userId=${userId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  updatePreferences: async (
    data: UpdatePreferencesRequest,
    userId: string = DEFAULT_USER_ID
  ): Promise<UserProfile> => {
    return request(`/user/preferences?userId=${userId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  updatePrivacy: async (data: UpdatePrivacyRequest, userId: string = DEFAULT_USER_ID): Promise<UserProfile> => {
    return request(`/user/privacy?userId=${userId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  getAPIKeys: async (userId: string = DEFAULT_USER_ID): Promise<APIKey[]> => {
    return request(`/user/api-keys?userId=${userId}`)
  },

  createAPIKey: async (data: CreateAPIKeyRequest, userId: string = DEFAULT_USER_ID): Promise<APIKey> => {
    return request(`/user/api-keys?userId=${userId}`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  deleteAPIKey: async (keyId: string, userId: string = DEFAULT_USER_ID): Promise<void> => {
    return request(`/user/api-keys/${keyId}?userId=${userId}`, {
      method: 'DELETE',
    })
  },

  revokeDevice: async (deviceId: string, userId: string = DEFAULT_USER_ID): Promise<void> => {
    return request(`/user/devices/${deviceId}?userId=${userId}`, {
      method: 'DELETE',
    })
  },
}

export default userApi
