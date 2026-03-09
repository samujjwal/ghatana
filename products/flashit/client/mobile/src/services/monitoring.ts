/**
 * Monitoring Service for Flashit Mobile
 * Application monitoring, crash reporting, and analytics
 *
 * @doc.type service
 * @doc.purpose Mobile app monitoring and crash reporting
 * @doc.layer product
 * @doc.pattern MonitoringService
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppState, AppStateStatus } from 'react-native';
import * as Device from 'expo-device';
import * as Application from 'expo-application';
import Constants from 'expo-constants';

// ============================================================================
// Types & Interfaces
// ============================================================================

export type MetricType = 'counter' | 'gauge' | 'histogram' | 'timing';
export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'fatal';

export interface Metric {
  name: string;
  type: MetricType;
  value: number;
  timestamp: number;
  tags?: Record<string, string>;
}

export interface LogEntry {
  level: LogLevel;
  message: string;
  timestamp: number;
  context?: Record<string, unknown>;
  stackTrace?: string;
}

export interface CrashReport {
  id: string;
  timestamp: number;
  error: string;
  stackTrace: string;
  deviceInfo: DeviceInfo;
  appInfo: AppInfo;
  breadcrumbs: Breadcrumb[];
}

export interface DeviceInfo {
  brand: string;
  model: string;
  osName: string;
  osVersion: string;
  platform: string;
  memory?: number;
}

export interface AppInfo {
  version: string;
  buildVersion: string;
  bundleId: string;
  isDevice: boolean;
}

export interface Breadcrumb {
  timestamp: number;
  category: string;
  message: string;
  level: LogLevel;
  data?: Record<string, unknown>;
}

export interface PerformanceMetrics {
  appLaunchTime?: number;
  screenLoadTimes: Record<string, number>;
  apiCallDurations: Record<string, number[]>;
  memoryUsage: number[];
}

// ============================================================================
// Constants
// ============================================================================

const METRICS_KEY = '@ghatana/flashit-metrics';
const LOGS_KEY = '@ghatana/flashit-logs';
const CRASHES_KEY = '@ghatana/flashit-crashes';
const MAX_BREADCRUMBS = 100;
const MAX_STORED_METRICS = 1000;
const MAX_STORED_LOGS = 500;

// ============================================================================
// Monitoring Service
// ============================================================================

/**
 * MonitoringService handles app monitoring and crash reporting
 */
class MonitoringService {
  private static instance: MonitoringService | null = null;

  private metrics: Metric[] = [];
  private logs: LogEntry[] = [];
  private breadcrumbs: Breadcrumb[] = [];
  private performanceMetrics: PerformanceMetrics;
  private appStartTime: number;
  private currentScreen: string = 'Unknown';
  private screenStartTime: number = Date.now();
  private deviceInfo: DeviceInfo | null = null;
  private appInfo: AppInfo | null = null;

  private constructor() {
    this.appStartTime = Date.now();
    this.performanceMetrics = {
      screenLoadTimes: {},
      apiCallDurations: {},
      memoryUsage: [],
    };
    
    this.initialize();
  }

  /**
   * Get singleton instance
   */
  static getInstance(): MonitoringService {
    if (!this.instance) {
      this.instance = new MonitoringService();
    }
    return this.instance;
  }

  /**
   * Initialize monitoring
   */
  private async initialize(): Promise<void> {
    // Collect device info
    this.deviceInfo = {
      brand: Device.brand || 'Unknown',
      model: Device.modelName || 'Unknown',
      osName: Device.osName || 'Unknown',
      osVersion: Device.osVersion || 'Unknown',
      platform: Device.osInternalBuildId || 'Unknown',
    };

    // Collect app info
    this.appInfo = {
      version: Application.nativeApplicationVersion || '1.0.0',
      buildVersion: Application.nativeBuildVersion || '1',
      bundleId: Application.applicationId || 'unknown',
      isDevice: Device.isDevice,
    };

    // Record app launch
    this.recordMetric('app.launch', 'counter', 1);
    this.addBreadcrumb('app', 'App launched', 'info');

    // Monitor app state changes
    AppState.addEventListener('change', this.handleAppStateChange.bind(this));

    // Setup error handlers
    this.setupErrorHandlers();

    // Load persisted data
    await this.loadPersistedData();
  }

  /**
   * Record a metric
   */
  recordMetric(
    name: string,
    type: MetricType,
    value: number,
    tags?: Record<string, string>
  ): void {
    const metric: Metric = {
      name,
      type,
      value,
      timestamp: Date.now(),
      tags,
    };

    this.metrics.push(metric);

    // Keep only recent metrics
    if (this.metrics.length > MAX_STORED_METRICS) {
      this.metrics.shift();
    }

    this.persistMetrics();
  }

  /**
   * Log a message
   */
  log(
    level: LogLevel,
    message: string,
    context?: Record<string, unknown>
  ): void {
    const entry: LogEntry = {
      level,
      message,
      timestamp: Date.now(),
      context,
    };

    this.logs.push(entry);

    // Keep only recent logs
    if (this.logs.length > MAX_STORED_LOGS) {
      this.logs.shift();
    }

    // Add as breadcrumb
    this.addBreadcrumb('log', message, level, context);

    // Console output in development
    if (__DEV__) {
      console[level === 'fatal' ? 'error' : level](message, context);
    }

    this.persistLogs();
  }

  /**
   * Add breadcrumb
   */
  addBreadcrumb(
    category: string,
    message: string,
    level: LogLevel = 'info',
    data?: Record<string, unknown>
  ): void {
    const breadcrumb: Breadcrumb = {
      timestamp: Date.now(),
      category,
      message,
      level,
      data,
    };

    this.breadcrumbs.push(breadcrumb);

    // Keep only recent breadcrumbs
    if (this.breadcrumbs.length > MAX_BREADCRUMBS) {
      this.breadcrumbs.shift();
    }
  }

  /**
   * Report crash
   */
  async reportCrash(error: Error): Promise<void> {
    const crash: CrashReport = {
      id: `crash_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      timestamp: Date.now(),
      error: error.message,
      stackTrace: error.stack || '',
      deviceInfo: this.deviceInfo!,
      appInfo: this.appInfo!,
      breadcrumbs: [...this.breadcrumbs],
    };

    // Store crash report
    try {
      const crashes = await this.getStoredCrashes();
      crashes.unshift(crash);
      
      // Keep only last 10 crashes
      if (crashes.length > 10) {
        crashes.splice(10);
      }

      await AsyncStorage.setItem(CRASHES_KEY, JSON.stringify(crashes));

      // TODO: Send to crash reporting service
      this.log('error', 'Crash reported', { crashId: crash.id });
    } catch (e) {
      console.error('Failed to store crash report:', e);
    }
  }

  /**
   * Track screen view
   */
  trackScreenView(screenName: string): void {
    // Record previous screen time
    if (this.currentScreen !== 'Unknown') {
      const duration = Date.now() - this.screenStartTime;
      this.performanceMetrics.screenLoadTimes[this.currentScreen] = duration;
      this.recordMetric('screen.duration', 'timing', duration, {
        screen: this.currentScreen,
      });
    }

    // Update current screen
    this.currentScreen = screenName;
    this.screenStartTime = Date.now();

    this.addBreadcrumb('navigation', `Navigated to ${screenName}`, 'info');
    this.recordMetric('screen.view', 'counter', 1, { screen: screenName });
  }

  /**
   * Track API call
   */
  trackApiCall(
    endpoint: string,
    method: string,
    duration: number,
    statusCode: number
  ): void {
    if (!this.performanceMetrics.apiCallDurations[endpoint]) {
      this.performanceMetrics.apiCallDurations[endpoint] = [];
    }
    this.performanceMetrics.apiCallDurations[endpoint].push(duration);

    this.recordMetric('api.call', 'histogram', duration, {
      endpoint,
      method,
      status: statusCode.toString(),
    });

    this.addBreadcrumb('api', `${method} ${endpoint}`, 'info', {
      duration,
      statusCode,
    });
  }

  /**
   * Track user action
   */
  trackAction(action: string, properties?: Record<string, unknown>): void {
    this.recordMetric('user.action', 'counter', 1, {
      action,
      ...properties as Record<string, string>,
    });

    this.addBreadcrumb('user', action, 'info', properties);
  }

  /**
   * Get metrics
   */
  getMetrics(): Metric[] {
    return [...this.metrics];
  }

  /**
   * Get logs
   */
  getLogs(level?: LogLevel): LogEntry[] {
    if (level) {
      return this.logs.filter((log) => log.level === level);
    }
    return [...this.logs];
  }

  /**
   * Get breadcrumbs
   */
  getBreadcrumbs(): Breadcrumb[] {
    return [...this.breadcrumbs];
  }

  /**
   * Get performance metrics
   */
  getPerformanceMetrics(): PerformanceMetrics {
    return { ...this.performanceMetrics };
  }

  /**
   * Get crash reports
   */
  async getCrashReports(): Promise<CrashReport[]> {
    return this.getStoredCrashes();
  }

  /**
   * Clear all data
   */
  async clearAll(): Promise<void> {
    this.metrics = [];
    this.logs = [];
    this.breadcrumbs = [];
    this.performanceMetrics = {
      screenLoadTimes: {},
      apiCallDurations: {},
      memoryUsage: [],
    };

    await Promise.all([
      AsyncStorage.removeItem(METRICS_KEY),
      AsyncStorage.removeItem(LOGS_KEY),
      AsyncStorage.removeItem(CRASHES_KEY),
    ]);
  }

  /**
   * Flush data to server
   */
  async flush(): Promise<void> {
    // TODO: Send data to monitoring server
    this.log('info', 'Flushing monitoring data');
  }

  // ============================================================================
  // Private Methods
  // ============================================================================

  private handleAppStateChange(state: AppStateStatus): void {
    this.addBreadcrumb('app', `App state changed to ${state}`, 'info');
    
    if (state === 'background') {
      this.flush();
    }
  }

  private setupErrorHandlers(): void {
    // Global error handler
    const originalHandler = ErrorUtils.getGlobalHandler();
    
    ErrorUtils.setGlobalHandler((error, isFatal) => {
      this.reportCrash(error);
      
      if (originalHandler) {
        originalHandler(error, isFatal);
      }
    });

    // Unhandled promise rejections
    const unhandledRejection = (event: PromiseRejectionEvent) => {
      const error = new Error(event.reason);
      this.reportCrash(error);
    };

    if (global.addEventListener) {
      global.addEventListener('unhandledrejection', unhandledRejection);
    }
  }

  private async persistMetrics(): Promise<void> {
    try {
      await AsyncStorage.setItem(METRICS_KEY, JSON.stringify(this.metrics));
    } catch (e) {
      console.error('Failed to persist metrics:', e);
    }
  }

  private async persistLogs(): Promise<void> {
    try {
      await AsyncStorage.setItem(LOGS_KEY, JSON.stringify(this.logs));
    } catch (e) {
      console.error('Failed to persist logs:', e);
    }
  }

  private async loadPersistedData(): Promise<void> {
    try {
      const [metricsJson, logsJson] = await Promise.all([
        AsyncStorage.getItem(METRICS_KEY),
        AsyncStorage.getItem(LOGS_KEY),
      ]);

      if (metricsJson) {
        this.metrics = JSON.parse(metricsJson);
      }
      if (logsJson) {
        this.logs = JSON.parse(logsJson);
      }
    } catch (e) {
      console.error('Failed to load persisted data:', e);
    }
  }

  private async getStoredCrashes(): Promise<CrashReport[]> {
    try {
      const json = await AsyncStorage.getItem(CRASHES_KEY);
      return json ? JSON.parse(json) : [];
    } catch {
      return [];
    }
  }
}

// ============================================================================
// Exports
// ============================================================================

/**
 * Get monitoring service instance
 */
export function getMonitoring(): MonitoringService {
  return MonitoringService.getInstance();
}

/**
 * Convenience functions
 */
export const monitoring = {
  recordMetric: (name: string, type: MetricType, value: number, tags?: Record<string, string>) => {
    getMonitoring().recordMetric(name, type, value, tags);
  },
  
  log: (level: LogLevel, message: string, context?: Record<string, unknown>) => {
    getMonitoring().log(level, message, context);
  },
  
  trackScreen: (screenName: string) => {
    getMonitoring().trackScreenView(screenName);
  },
  
  trackAction: (action: string, properties?: Record<string, unknown>) => {
    getMonitoring().trackAction(action, properties);
  },
};

export default MonitoringService;
