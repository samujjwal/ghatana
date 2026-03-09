import apiClient, { get, post, put, del } from './client';

export * from './client';

export interface ApiResponse<T> {
  data: T;
  message?: string;
  success: boolean;
  timestamp: string;
}

// Example API service functions
export const authApi = {
  login: (credentials: { email: string; password: string }) =>
    post<{ token: string; user: unknown }>('/auth/login', credentials),
  
  getProfile: () => get<unknown>('/auth/me'),
};

export const metricsApi = {
  getMetrics: (params?: unknown) => get<unknown>('/metrics', { params }),
  getMetricById: (id: string) => get<unknown>(`/metrics/${id}`),
  createMetric: (data: unknown) => post<unknown>('/metrics', data),
  updateMetric: (id: string, data: unknown) => put<unknown>(`/metrics/${id}`, data),
  deleteMetric: (id: string) => del(`/metrics/${id}`),
};

export const eventsApi = {
  getEvents: (params?: unknown) => get<unknown>('/events', { params }),
  getEventById: (id: string) => get<unknown>(`/events/${id}`),
  createEvent: (data: unknown) => post<unknown>('/events', data),
  updateEvent: (id: string, data: unknown) => put<unknown>(`/events/${id}`, data),
  deleteEvent: (id: string) => del(`/events/${id}`),
};

// Export the API client for direct use if needed
export default apiClient;
