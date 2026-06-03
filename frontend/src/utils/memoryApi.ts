import type { Memory } from '../types';

export interface MemoryRecallRequest {
  userId: string;
  query: string;
  topK?: number;
  types?: string[];
}

export interface MemoryRecallResponse {
  memories: Memory[];
  count: number;
}

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export const memoryApi = {
  getAll: async (userId: string): Promise<Memory[]> => {
    const response = await fetch(`${BASE_URL}/memories?userId=${userId}`);
    if (!response.ok) throw new Error('获取记忆失败');
    return response.json();
  },

  getByType: async (userId: string, type: string): Promise<Memory[]> => {
    const response = await fetch(`${BASE_URL}/memories/type/${type}?userId=${userId}`);
    if (!response.ok) throw new Error('获取记忆失败');
    return response.json();
  },

  getById: async (id: number): Promise<Memory> => {
    const response = await fetch(`${BASE_URL}/memories/${id}`);
    if (!response.ok) throw new Error('获取记忆失败');
    return response.json();
  },

  create: async (memory: Omit<Memory, 'id' | 'createdAt'>): Promise<Memory> => {
    const response = await fetch(`${BASE_URL}/memories`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(memory),
    });
    if (!response.ok) throw new Error('创建记忆失败');
    return response.json();
  },

  update: async (id: number, memory: Partial<Omit<Memory, 'id' | 'createdAt'>>): Promise<Memory> => {
    const response = await fetch(`${BASE_URL}/memories/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(memory),
    });
    if (!response.ok) throw new Error('更新记忆失败');
    return response.json();
  },

  createBatch: async (memories: Omit<Memory, 'id' | 'createdAt'>[]): Promise<Memory[]> => {
    const response = await fetch(`${BASE_URL}/memories/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(memories),
    });
    if (!response.ok) throw new Error('批量创建记忆失败');
    return response.json();
  },

  recall: async (request: MemoryRecallRequest): Promise<MemoryRecallResponse> => {
    const response = await fetch(`${BASE_URL}/memories/recall`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    if (!response.ok) throw new Error('召回失败');
    return response.json();
  },

  delete: async (id: number): Promise<void> => {
    const response = await fetch(`${BASE_URL}/memories/${id}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error('删除失败');
  },

  deleteByUserId: async (userId: string): Promise<void> => {
    const response = await fetch(`${BASE_URL}/memories/user/${userId}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error('删除失败');
  },

  getTypes: async (): Promise<string[]> => {
    const response = await fetch(`${BASE_URL}/memories/types`);
    if (!response.ok) throw new Error('获取类型失败');
    return response.json();
  },
};