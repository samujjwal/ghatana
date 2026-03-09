export interface Device {
  id: string;
  name: string;
  type: 'android' | 'ios' | 'windows';
  status: 'online' | 'offline';
  lastSync: Date;
  childName: string;
  batteryLevel?: number;
  location?: {
    latitude: number;
    longitude: number;
    address?: string;
  };
}

export interface UsageData {
  date: string;
  screenTime: number; // minutes
  appUsage: AppUsage[];
  websiteVisits: WebsiteVisit[];
}

export interface AppUsage {
  appName: string;
  packageName: string;
  category: string;
  duration: number; // minutes
  timestamp: Date;
}

export interface WebsiteVisit {
  url: string;
  domain: string;
  duration: number; // seconds
  timestamp: Date;
}

export interface Policy {
  id: string;
  name: string;
  deviceId: string;
  screenTimeLimit?: number; // minutes per day
  blockedApps: string[]; // package names
  blockedWebsites: string[]; // domains
  timeRestrictions?: TimeRestriction[];
  enabled: boolean;
}

export interface TimeRestriction {
  dayOfWeek: number; // 0-6, Sunday = 0
  startTime: string; // HH:mm format
  endTime: string; // HH:mm format
}

export interface Alert {
  id: string;
  type: 'policy_violation' | 'device_offline' | 'battery_low' | 'location_alert';
  deviceId: string;
  message: string;
  timestamp: Date;
  read: boolean;
  severity: 'info' | 'warning' | 'critical';
}

export interface PushNotification {
  id: string;
  title: string;
  body: string;
  data?: Record<string, any>;
  timestamp: Date;
}
