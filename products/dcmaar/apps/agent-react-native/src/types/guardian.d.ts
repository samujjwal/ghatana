declare module 'react-native-guardian' {
  export interface AppInfo {
    packageName: string;
    appName: string;
    startTime: number;
  }

  export interface DeviceInfo {
    deviceId: string;
    deviceName: string;
    platform: 'ios' | 'android';
    osVersion: string;
    appVersion: string;
    lastSyncTimestamp: number;
  }

  export interface UsageStats {
    date: string;
    totalUsage: number;
    apps: Array<{
      packageName: string;
      usageTime: number;
    }>;
  }

  export interface GuardianInterface {
    AccessibilityService: {
      onAppChange: (callback: (app: AppInfo) => void) => { remove: () => void };
      getCurrentApp: () => Promise<AppInfo>;
    };
    isMonitoringActive: () => Promise<boolean>;
    getDeviceInfo: () => Promise<DeviceInfo>;
    getAppList: () => Promise<Array<{ packageName: string; appName: string }>>;
    initiateSync: () => Promise<{ success: boolean; timestamp: number }>;
    UsageStatsCollector?: {
      getDailyUsage: (date: string) => Promise<UsageStats>;
    };
  }

  const Guardian: GuardianInterface;
  export default Guardian;
}
