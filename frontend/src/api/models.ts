import { request } from './client';

export interface Model {
  id: string;
  name: string;
  type: string;
}

export type ProviderType = 'OPENAI' | 'OPENAI_COMPATIBLE' | 'ANTHROPIC' | 'GOOGLE' | 'OLLAMA' | 'AZURE' | 'CUSTOM';

export interface ModelConfig {
  id: string;
  name: string;
  modelId: string;
  baseUrl: string;
  apiKey: string;
  type: ProviderType;
  enabled: boolean;
  createdAt?: string;
}

export const models = {
  list: async (): Promise<Model[]> => {
    return request('/models');
  },
};

export const modelConfigs = {
  list: async (): Promise<ModelConfig[]> => {
    return request('/model-configs');
  },

  get: async (id: string): Promise<ModelConfig> => {
    return request(`/model-configs/${id}`);
  },

  listByType: async (type: string): Promise<ModelConfig[]> => {
    return request(`/model-configs/type/${type}`);
  },

  getTypes: async (): Promise<string[]> => {
    return request('/model-configs/types');
  },

  create: async (data: Omit<ModelConfig, 'id'>): Promise<ModelConfig> => {
    return request('/model-configs', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  update: async (id: string, data: Partial<ModelConfig>): Promise<ModelConfig> => {
    return request(`/model-configs/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  delete: async (id: string): Promise<void> => {
    await request(`/model-configs/${id}`, {
      method: 'DELETE',
    });
  },
};