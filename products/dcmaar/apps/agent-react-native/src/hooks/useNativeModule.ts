/**
 * React Native hooks for Guardian Native Module
 */

import { useEffect, useState } from 'react';
import { Platform } from 'react-native';
// Define the types inline since we're having module resolution issues
interface AppInfo {
  packageName: string;
  appName: string;
  startTime: number;
}

interface DeviceInfo {
  deviceId: string;
  deviceName: string;
  platform: 'ios' | 'android';
  osVersion: string;
  appVersion: string;
  lastSyncTimestamp: number;
}

interface UsageStats {
  date: string;
  totalUsage: number;
  apps: Array<{
    packageName: string;
    usageTime: number;
  }>;
}

// Mock implementation for non-native environments (web, dev)
const MockGuardian = {
  AccessibilityService: {
    onAppChange: (callback: (app: AppInfo) => void) => {
      const interval = setInterval(() => {
        callback({
          packageName: 'com.example.app',
          appName: 'Example App',
          startTime: Date.now(),
        });
      }, 5000);

      return {
        remove: () => clearInterval(interval)
      };
    },
    getCurrentApp: async (): Promise<AppInfo> => ({
      packageName: 'com.example.app',
      appName: 'Example App',
      startTime: Date.now(),
    }),
  },
  isMonitoringActive: async (): Promise<boolean> => true,
  getDeviceInfo: async (): Promise<DeviceInfo> => ({
    deviceId: 'mock-device-id',
    deviceName: 'Mock Device',
    platform: Platform.OS as 'ios' | 'android',
    osVersion: '14.0',
    appVersion: '1.0.0',
    lastSyncTimestamp: Date.now(),
  }),
  getAppList: async () => [],
  initiateSync: async () => ({ success: true, timestamp: Date.now() }),
  UsageStatsCollector: {
    getDailyUsage: async (date: string): Promise<UsageStats> => ({
      date,
      totalUsage: 3600000,
      apps: [
        { packageName: 'com.example.app', usageTime: 1800000 },
        { packageName: 'com.example.another', usageTime: 1800000 },
      ],
    }),
  },
};

// Try to get real native module, fallback to mock
declare global {
  interface GuardianInterface {
    AccessibilityService: {
      onAppChange: (callback: (app: any) => void) => { remove: () => void };
      getCurrentApp: () => Promise<any>;
    };
    isMonitoringActive: () => Promise<boolean>;
    getDeviceInfo: () => Promise<any>;
    UsageStatsCollector?: {
      getDailyUsage: (date: string) => Promise<any>;
    };
  }

  var Guardian: GuardianInterface;
}

let Guardian: GuardianInterface;
try {
  const { NativeModules } = require('react-native');
  Guardian = NativeModules.GuardianNativeModule || MockGuardian;
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
} catch (_) {
  Guardian = MockGuardian;
}

export { Guardian };

/**
 * Hook for monitoring currently active app
 */
export function useCurrentApp(): AppInfo | null {
  const [currentApp, setCurrentApp] = useState<AppInfo | null>(null);

  useEffect(() => {
    if (!Guardian?.AccessibilityService) {
      return;
    }

    // Set up listener
    const listener = Guardian?.AccessibilityService?.onAppChange((app: AppInfo) => {
      setCurrentApp(app);
    });

    // Get initial app
    Guardian?.AccessibilityService?.getCurrentApp()
      .then(setCurrentApp)
      .catch((err: Error) => console.error('Failed to get current app:', err));

    return () => {
      // Cleanup
      listener?.remove?.();
    };
  }, []);

  return currentApp;
}

/**
 * Hook for device status
 */
export function useDeviceStatus() {
  const [status, setStatus] = useState({
    isReady: false,
    isMonitoring: false,
    hasDeviceAdmin: false,
    lastSync: null as string | null
  });

  useEffect(() => {
    async function checkStatus() {
      try {
        const isMonitoring = await Guardian?.isMonitoringActive?.() ?? false;
        const deviceInfo = await Guardian?.getDeviceInfo?.() ?? {};
        
        setStatus({
          isReady: true,
          isMonitoring,
          hasDeviceAdmin: false, // TODO: check device admin status
          lastSync: deviceInfo.lastSyncTimestamp 
            ? new Date(deviceInfo.lastSyncTimestamp).toLocaleString()
            : null
        });
      } catch (err) {
        console.error('Failed to check device status:', err);
        setStatus(prev => ({ ...prev, isReady: true }));
      }
    }

    checkStatus();
  }, []);

  return status;
}

/**
 * Hook for policies
 */
export function usePolicies() {
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    async function fetchPolicies() {
      try {
        // TODO: Fetch from local storage or backend
        setPolicies([]);
        setLoading(false);
      } catch (err) {
        setError(err as Error);
        setLoading(false);
      }
    }

    fetchPolicies();
  }, []);

  return { policies, loading, error };
}

/**
 * Hook for usage stats
 */
export function useUsageStats(date?: Date) {
  const [usage, setUsage] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchUsage() {
      try {
        if (!Guardian?.UsageStatsCollector) {
          setLoading(false);
          return;
        }

        const dateStr = date?.toISOString().split('T')[0] || new Date().toISOString().split('T')[0];
        const data = await Guardian?.UsageStatsCollector?.getDailyUsage?.(dateStr) ?? [];
        
        setUsage(data);
        setLoading(false);
      } catch (err) {
        console.error('Failed to fetch usage stats:', err);
        setLoading(false);
      }
    }

    fetchUsage();
  }, [date]);

  return { usage, loading };
}
