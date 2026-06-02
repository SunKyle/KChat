import { request } from './client'
import type {
  UserProfile,
  UpdateProfileRequest,
  UpdatePreferencesRequest,
  UpdatePrivacyRequest,
  CreateAPIKeyRequest,
  APIKey,
} from '../types/user'

export const userApi = {
  getProfile: async (): Promise<UserProfile> => {
    return request('/api/user/profile')
  },

  updateProfile: async (data: UpdateProfileRequest): Promise<UserProfile> => {
    return request('/api/user/profile', {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  updatePreferences: async (
    data: UpdatePreferencesRequest
  ): Promise<UserProfile> => {
    return request('/api/user/preferences', {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  updatePrivacy: async (data: UpdatePrivacyRequest): Promise<UserProfile> => {
    return request('/api/user/privacy', {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  getAPIKeys: async (): Promise<APIKey[]> => {
    return request('/api/user/api-keys')
  },

  createAPIKey: async (data: CreateAPIKeyRequest): Promise<APIKey> => {
    return request('/api/user/api-keys', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  deleteAPIKey: async (keyId: string): Promise<void> => {
    return request(`/api/user/api-keys/${keyId}`, {
      method: 'DELETE',
    })
  },

  revokeDevice: async (deviceId: string): Promise<void> => {
    return request(`/api/user/devices/${deviceId}`, {
      method: 'DELETE',
    })
  },
}

export default userApi