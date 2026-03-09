import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { Device, UsageData, Policy, Alert } from '@/types';

const API_BASE_URL = __DEV__ ? 'http://localhost:8080/api' : 'https://api.guardian.app';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token to requests
apiClient.interceptors.request.use(async (config) => {
  const token = await AsyncStorage.getItem('authToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Mock data for development
const mockDevices: Device[] = [
  {
    id: '1',
    name: "Emma's iPhone",
    type: 'ios',
    status: 'online',
    lastSync: new Date(),
    childName: 'Emma',
    batteryLevel: 85,
    location: {
      latitude: 37.7749,
      longitude: -122.4194,
      address: 'Home',
    },
  },
  {
    id: '2',
    name: "Liam's Android",
    type: 'android',
    status: 'online',
    lastSync: new Date(Date.now() - 5 * 60 * 1000),
    childName: 'Liam',
    batteryLevel: 42,
  },
  {
    id: '3',
    name: "Olivia's PC",
    type: 'windows',
    status: 'offline',
    lastSync: new Date(Date.now() - 2 * 60 * 60 * 1000),
    childName: 'Olivia',
  },
];

const mockUsageData: UsageData[] = Array.from({ length: 7 }, (_, i) => {
  const date = new Date();
  date.setDate(date.getDate() - (6 - i));
  
  return {
    date: date.toISOString().split('T')[0],
    screenTime: Math.floor(Math.random() * 300) + 60,
    appUsage: [
      {
        appName: 'Instagram',
        packageName: 'com.instagram.android',
        category: 'Social',
        duration: Math.floor(Math.random() * 120),
        timestamp: date,
      },
      {
        appName: 'YouTube',
        packageName: 'com.google.android.youtube',
        category: 'Entertainment',
        duration: Math.floor(Math.random() * 180),
        timestamp: date,
      },
    ],
    websiteVisits: [],
  };
});

const mockPolicies: Policy[] = [
  {
    id: '1',
    name: 'School Days',
    deviceId: '1',
    screenTimeLimit: 120,
    blockedApps: ['com.instagram.android', 'com.tiktok'],
    blockedWebsites: ['youtube.com', 'tiktok.com'],
    timeRestrictions: [
      {
        dayOfWeek: 1,
        startTime: '22:00',
        endTime: '07:00',
      },
    ],
    enabled: true,
  },
];

const mockAlerts: Alert[] = [
  {
    id: '1',
    type: 'policy_violation',
    deviceId: '1',
    message: "Emma attempted to access blocked app 'Instagram'",
    timestamp: new Date(Date.now() - 30 * 60 * 1000),
    read: false,
    severity: 'warning',
  },
  {
    id: '2',
    type: 'battery_low',
    deviceId: '2',
    message: "Liam's device battery is at 42%",
    timestamp: new Date(Date.now() - 60 * 60 * 1000),
    read: false,
    severity: 'info',
  },
];

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(() => resolve(), ms));

export const api = {
  // Devices
  getDevices: async (): Promise<Device[]> => {
    if (__DEV__) {
      await delay(500);
      return mockDevices;
    }
    const response = await apiClient.get<Device[]>('/devices');
    return response.data;
  },

  // Usage
  getUsageData: async (deviceId: string, days: number = 7): Promise<UsageData[]> => {
    if (__DEV__) {
      await delay(500);
      return mockUsageData;
    }
    const response = await apiClient.get<UsageData[]>(`/devices/${deviceId}/usage`, {
      params: { days },
    });
    return response.data;
  },

  // Policies
  getPolicies: async (deviceId?: string): Promise<Policy[]> => {
    if (__DEV__) {
      await delay(500);
      return deviceId ? mockPolicies.filter((p) => p.deviceId === deviceId) : mockPolicies;
    }
    const response = await apiClient.get<Policy[]>('/policies', {
      params: deviceId ? { deviceId } : {},
    });
    return response.data;
  },

  createPolicy: async (policy: Omit<Policy, 'id'>): Promise<Policy> => {
    if (__DEV__) {
      await delay(500);
      const newPolicy = { ...policy, id: String(Date.now()) };
      mockPolicies.push(newPolicy);
      return newPolicy;
    }
    const response = await apiClient.post<Policy>('/policies', policy);
    return response.data;
  },

  updatePolicy: async (id: string, updates: Partial<Policy>): Promise<Policy> => {
    if (__DEV__) {
      await delay(500);
      const index = mockPolicies.findIndex((p) => p.id === id);
      if (index === -1) throw new Error('Policy not found');
      mockPolicies[index] = { ...mockPolicies[index], ...updates };
      return mockPolicies[index];
    }
    const response = await apiClient.patch<Policy>(`/policies/${id}`, updates);
    return response.data;
  },

  deletePolicy: async (id: string): Promise<void> => {
    if (__DEV__) {
      await delay(500);
      const index = mockPolicies.findIndex((p) => p.id === id);
      if (index === -1) throw new Error('Policy not found');
      mockPolicies.splice(index, 1);
      return;
    }
    await apiClient.delete(`/policies/${id}`);
  },

  // Alerts
  getAlerts: async (): Promise<Alert[]> => {
    if (__DEV__) {
      await delay(500);
      return mockAlerts;
    }
    const response = await apiClient.get<Alert[]>('/alerts');
    return response.data;
  },

  markAlertRead: async (id: string): Promise<void> => {
    if (__DEV__) {
      await delay(300);
      const alert = mockAlerts.find((a) => a.id === id);
      if (alert) alert.read = true;
      return;
    }
    await apiClient.patch(`/alerts/${id}/read`);
  },
};
