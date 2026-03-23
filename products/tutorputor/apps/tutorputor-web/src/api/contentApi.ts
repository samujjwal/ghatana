import type { Content } from '@/types/content';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000/api';

export const contentApi = {
  async getContent(id: string): Promise<Content | null> {
    try {
      const response = await fetch(`${API_BASE_URL}/content/${id}`);
      if (!response.ok) return null;
      return await response.json();
    } catch (error) {
      console.error('Failed to fetch content:', error);
      return null;
    }
  },

  async listContent(filters?: Record<string, unknown>): Promise<Content[]> {
    try {
      const params = new URLSearchParams(filters as Record<string, string>);
      const response = await fetch(`${API_BASE_URL}/content?${params}`);
      if (!response.ok) return [];
      const data = await response.json();
      return data.items || [];
    } catch (error) {
      console.error('Failed to list content:', error);
      return [];
    }
  },

  async createContent(content: Partial<Content>): Promise<Content> {
    const response = await fetch(`${API_BASE_URL}/content`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(content),
    });
    return await response.json();
  },

  async updateContent(id: string, updates: Partial<Content>): Promise<Content> {
    const response = await fetch(`${API_BASE_URL}/content/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(updates),
    });
    return await response.json();
  },

  async deleteContent(id: string): Promise<void> {
    await fetch(`${API_BASE_URL}/content/${id}`, { method: 'DELETE' });
  },
};
