/**
 * Shared type definitions for Guardian Agent Core
 */

export interface DeviceInfo {
  deviceId: string;
  deviceName: string;
  platform: 'android' | 'ios' | 'windows';
  osVersion: string;
  appVersion: string;
  lastSyncTimestamp: number;
}

export interface AppInfo {
  packageName: string;
  appName: string;
  category: AppCategory;
  icon?: string;
  timestamp: number;
}

export enum AppCategory {
  SOCIAL = 'social',
  GAMING = 'gaming',
  STREAMING = 'streaming',
  EDUCATION = 'education',
  COMMUNICATION = 'communication',
  PRODUCTIVITY = 'productivity',
  ENTERTAINMENT = 'entertainment',
  OTHER = 'other'
}

export interface UsageData {
  appId: string;
  appName: string;
  duration: number;
  launchCount: number;
  date: string;
}

export interface Policy {
  id: string;
  name: string;
  enabled: boolean;
  targetApps: string[];
  targetCategories: AppCategory[];
  dailyLimitMs?: number;
  timeWindows?: TimeWindow[];
  blockReason?: string;
  createdAt: number;
  updatedAt: number;
}

export interface TimeWindow {
  daysOfWeek: number[];
  startMinutes: number;
  endMinutes: number;
  isBlocked: boolean;
}

export enum PolicyAction {
  ALLOW = 'allow',
  BLOCK = 'block',
  WARN = 'warn',
  LOG = 'log'
}

export interface BlockEvent {
  id: string;
  appId: string;
  appName: string;
  policyId: string;
  reason: string;
  timestamp: number;
  duration?: number;
}

export interface SyncRequest {
  deviceId: string;
  usageData: UsageData[];
  blockEvents: BlockEvent[];
  lastSyncTimestamp: number;
}

export interface SyncResponse {
  policies: Policy[];
  serverTimestamp: number;
  acknowledgement: {
    usageDataReceived: number;
    blockEventsReceived: number;
  };
}
