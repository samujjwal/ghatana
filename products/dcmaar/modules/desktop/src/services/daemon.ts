import api from './api';
import { QueryFunction } from '@tanstack/react-query';

export interface Metric {
  timestamp: string;
  name: string;
  value: number;
  unit?: string;
}

export interface Event {
  id: string;
  timestamp: string;
  type: string;
  message: string;
  metadata?: Record<string, unknown>;
}

export const getMetrics: QueryFunction<Metric[]> = async ({ queryKey }) => {
  try {
  const [_key, params] = queryKey as [string, { from?: string; to?: string }];
  void _key;
    const response = await api.get('/metrics', { params });
    return response.data;
  } catch (error) {
    console.error('Failed to fetch metrics:', error);
    return []; // Return empty array instead of failing
  }
};

export const getLatestMetrics = async (): Promise<Metric[]> => {
  try {
    const response = await api.get('/metrics/latest');
    return response.data;
  } catch (error) {
    console.error('Failed to fetch latest metrics:', error);
    return [];
  }
};

export const getEvents: QueryFunction<Event[]> = async ({ queryKey }) => {
  try {
  const [_key, params] = queryKey as [string, { type?: string; limit?: number }];
  void _key;
    const response = await api.get('/events', { params });
    return response.data;
  } catch (error) {
    console.error('Failed to fetch events:', error);
    return [];
  }
};

export const getEventTypes = async (): Promise<string[]> => {
  try {
    const response = await api.get('/events/types');
    return response.data;
  } catch (error) {
    console.error('Failed to fetch event types:', error);
    return [];
  }
};
