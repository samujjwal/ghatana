/**
 * Error Monitoring System
 * 
 * Comprehensive error tracking and reporting for the DCMAAR extension.
 * Captures JavaScript errors, performance degradation, and user-reported issues.
 */

import { telemetryManager } from './TelemetryManager';
import browser from 'webextension-polyfill';

// ================================================================================================
// Error Types and Interfaces
// ================================================================================================

export interface ErrorReport {
  /** Unique error identifier */
  id: string;
  
  /** Error classification */
  type: 'javascript' | 'performance' | 'storage' | 'network' | 'user' | 'csp' | 'extension';
  
  /** Error message */
  message: string;
  
  /** Stack trace if available */
  stack?: string;
  
  /** Error context and metadata */
  context: {
    url?: string;
    userAgent: string;
    extensionVersion: string;
    timestamp: number;
    userId: string;
    sessionId: string;
    source: string;
    lineNumber?: number;
    columnNumber?: number;
    filename?: string;
  };
  
  /** Error severity classification */
  severity: 'low' | 'medium' | 'high' | 'critical';
  
  /** Additional metadata */
  metadata?: Record<string, any>;
  
  /** User interaction leading to error */
  userAction?: string;
  
  /** Performance metrics at time of error */
  performanceSnapshot?: {
    memory?: Record<string, number>;
    timing?: Record<string, number>;
    resources?: number;
  };
}

export interface PerformanceAlert {
  id: string;
  type: 'memory' | 'timing' | 'resource' | 'quota';
  message: string;
  threshold: number;
  actualValue: number;
  timestamp: number;
  severity: 'warning' | 'error' | 'critical';
  context: Record<string, any>;
}

// ================================================================================================
// Error Monitor - Main Service Class
// ================================================================================================

export class ErrorMonitor {
  private initialized = false;
  private errorThresholds: Record<string, number> = {};
  private performanceBaseline: Record<string, number> = {};
  private errorRateWindow: number[] = [];
  private readonly MAX_WINDOW_SIZE = 100;

  constructor() {
    this.setupDefaultThresholds();
  }

  /**
   * Initialize error monitoring
   */
  async initialize(): Promise<void> {
    if (this.initialized) return;

    try {
      // Set up global error handlers
      this.setupGlobalErrorHandlers();
      
      // Set up performance monitoring
      this.setupPerformanceMonitoring();
      
      // Set up unhandled promise rejection handler
      this.setupPromiseRejectionHandler();
      
      // Set up CSP violation handler
      this.setupCSPViolationHandler();
      
      // Load error thresholds from storage
      await this.loadThresholds();
      
      this.initialized = true;
      
      await telemetryManager.track('system', 'error_monitor.initialized');
      
    } catch (error) {
      console.error('Failed to initialize ErrorMonitor:', error);
    }
  }

  /**
   * Report an error manually
   */
  async reportError(
    error: Error,
    type: ErrorReport['type'] = 'javascript',
    severity: ErrorReport['severity'] = 'medium',
    userAction?: string,
    metadata?: Record<string, any>
  ): Promise<void> {
    try {
      const errorReport = await this.createErrorReport(error, type, severity, userAction, metadata);
      
      // Track with telemetry
      await telemetryManager.trackError(error, {
        errorId: errorReport.id,
        type: errorReport.type,
        severity: errorReport.severity,
        userAction: userAction,
        metadata: metadata
      });
      
      // Store error for analysis
      await this.storeErrorReport(errorReport);
      
      // Update error rate tracking
      this.updateErrorRate();
      
      // Check if error rate exceeds threshold
      await this.checkErrorRateThreshold();
      
    } catch (reportingError) {
      console.error('Failed to report error:', reportingError);
    }
  }

  /**
   * Report performance degradation
   */
  async reportPerformanceIssue(
    metric: string,
    value: number,
    threshold: number,
    context: Record<string, any> = {}
  ): Promise<void> {
    const severity = this.calculatePerformanceSeverity(value, threshold);
    
    const alert: PerformanceAlert = {
      id: `perf_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      type: this.getPerformanceAlertType(metric),
      message: `Performance degradation detected: ${metric} (${value}) exceeds threshold (${threshold})`,
      threshold,
      actualValue: value,
      timestamp: Date.now(),
      severity,
      context
    };

    await telemetryManager.track('performance', 'degradation.detected', {
      alert,
      metric,
      value,
      threshold,
      severity
    });

    await this.storePerformanceAlert(alert);
  }

  /**
   * Report storage issues
   */
  async reportStorageIssue(operation: string, error: Error, context: Record<string, any> = {}): Promise<void> {
    await this.reportError(error, 'storage', 'high', `storage.${operation}`, {
      operation,
      ...context
    });
  }

  /**
   * Report network issues
   */
  async reportNetworkIssue(url: string, error: Error, context: Record<string, any> = {}): Promise<void> {
    await this.reportError(error, 'network', 'medium', 'network.request', {
      url: this.sanitizeUrl(url),
      ...context
    });
  }

  /**
   * Get error statistics
   */
  async getErrorStats(): Promise<{
    totalErrors: number;
    errorsByType: Record<string, number>;
    errorsBySeverity: Record<string, number>;
    errorRate: number;
    recentErrors: ErrorReport[];
  }> {
    const errors = await this.getStoredErrors();
    
    const stats = {
      totalErrors: errors.length,
      errorsByType: {} as Record<string, number>,
      errorsBySeverity: {} as Record<string, number>,
      errorRate: this.calculateCurrentErrorRate(),
      recentErrors: errors.slice(-10)
    };

    errors.forEach(error => {
      stats.errorsByType[error.type] = (stats.errorsByType[error.type] || 0) + 1;
      stats.errorsBySeverity[error.severity] = (stats.errorsBySeverity[error.severity] || 0) + 1;
    });

    return stats;
  }

  /**
   * Clear old error reports
   */
  async cleanupErrors(olderThanDays: number = 7): Promise<void> {
    const cutoffTime = Date.now() - (olderThanDays * 24 * 60 * 60 * 1000);
    const errors = await this.getStoredErrors();
    const filtered = errors.filter(error => error.context.timestamp > cutoffTime);
    
    await browser.storage.local.set({
      'dcmaar.error_monitor.errors': filtered
    });

    await telemetryManager.track('system', 'error_monitor.cleanup', {
      removed: errors.length - filtered.length,
      remaining: filtered.length
    });
  }

  // ================================================================================================
  // Private Helper Methods
  // ================================================================================================

  private setupDefaultThresholds(): void {
    this.errorThresholds = {
      errorRate: 0.05, // 5% error rate threshold
      memoryUsage: 100 * 1024 * 1024, // 100MB memory threshold
      loadTime: 3000, // 3 second load time threshold
      queryTime: 1000, // 1 second query time threshold
      storageQuota: 0.8 // 80% storage quota threshold
    };
  }

  private setupGlobalErrorHandlers(): void {
    // Handle JavaScript errors
    if (typeof window !== 'undefined') {
      window.addEventListener('error', async (event) => {
        const error = new Error(event.message || 'Unknown error');
        error.stack = `${event.filename}:${event.lineno}:${event.colno}`;
        
        await this.reportError(error, 'javascript', 'medium', undefined, {
          filename: event.filename,
          lineno: event.lineno,
          colno: event.colno
        });
      });

      // Handle resource loading errors
      window.addEventListener('error', async (event) => {
        if (event.target !== window) {
          const element = event.target as HTMLElement;
          const tagName = element?.tagName?.toLowerCase();
          
          if (['img', 'script', 'link', 'iframe'].includes(tagName)) {
            const error = new Error(`Failed to load resource: ${tagName}`);
            await this.reportError(error, 'network', 'low', 'resource.load.failed', {
              tagName,
              src: (element as any).src || (element as any).href
            });
          }
        }
      }, true);
    }
  }

  private setupPromiseRejectionHandler(): void {
    if (typeof window !== 'undefined') {
      window.addEventListener('unhandledrejection', async (event) => {
        const error = event.reason instanceof Error 
          ? event.reason 
          : new Error(String(event.reason));
        
        await this.reportError(error, 'javascript', 'high', 'promise.rejection', {
          reason: event.reason,
          promise: 'Unhandled Promise Rejection'
        });
      });
    }
  }

  private setupCSPViolationHandler(): void {
    if (typeof window !== 'undefined') {
      window.addEventListener('securitypolicyviolation', async (event) => {
        const error = new Error(`CSP Violation: ${event.violatedDirective}`);
        await this.reportError(error, 'csp', 'high', 'csp.violation', {
          violatedDirective: event.violatedDirective,
          blockedURI: event.blockedURI,
          originalPolicy: event.originalPolicy
        });
      });
    }
  }

  private setupPerformanceMonitoring(): void {
    // Monitor performance metrics periodically
    setInterval(async () => {
      await this.checkPerformanceMetrics();
    }, 30000); // Check every 30 seconds
  }

  private async checkPerformanceMetrics(): Promise<void> {
    try {
      // Check memory usage
      if ('memory' in performance) {
        const memory = (performance as any).memory;
        const memoryUsage = memory.usedJSHeapSize;
        
        if (memoryUsage > this.errorThresholds.memoryUsage) {
          await this.reportPerformanceIssue('memory.usage', memoryUsage, this.errorThresholds.memoryUsage, {
            totalHeapSize: memory.totalJSHeapSize,
            heapSizeLimit: memory.jsHeapSizeLimit
          });
        }
      }

      // Check storage quota
      if ('storage' in navigator && 'estimate' in navigator.storage) {
        const estimate = await navigator.storage.estimate();
        if (estimate.quota && estimate.usage) {
          const usageRatio = estimate.usage / estimate.quota;
          
          if (usageRatio > this.errorThresholds.storageQuota) {
            await this.reportPerformanceIssue('storage.quota', usageRatio, this.errorThresholds.storageQuota, {
              usage: estimate.usage,
              quota: estimate.quota
            });
          }
        }
      }

    } catch (error) {
      console.warn('Performance monitoring check failed:', error);
    }
  }

  private async createErrorReport(
    error: Error,
    type: ErrorReport['type'],
    severity: ErrorReport['severity'],
    userAction?: string,
    metadata?: Record<string, any>
  ): Promise<ErrorReport> {
    return {
      id: `error_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      type,
      message: error.message || 'Unknown error',
      stack: error.stack,
      severity,
      userAction,
      metadata,
      context: {
        url: typeof window !== 'undefined' ? window.location?.href : undefined,
        userAgent: navigator.userAgent,
        extensionVersion: browser.runtime.getManifest().version,
        timestamp: Date.now(),
        userId: await this.getUserId(),
        sessionId: await this.getSessionId(),
        source: this.detectSource(),
        lineNumber: this.extractLineNumber(error.stack),
        columnNumber: this.extractColumnNumber(error.stack),
        filename: this.extractFilename(error.stack)
      },
      performanceSnapshot: await this.capturePerformanceSnapshot()
    };
  }

  private async storeErrorReport(report: ErrorReport): Promise<void> {
    try {
      const errors = await this.getStoredErrors();
      errors.push(report);
      
      // Keep only last 100 errors to prevent storage bloat
      const trimmed = errors.slice(-100);
      
      await browser.storage.local.set({
        'dcmaar.error_monitor.errors': trimmed
      });
    } catch (error) {
      console.error('Failed to store error report:', error);
    }
  }

  private async storePerformanceAlert(alert: PerformanceAlert): Promise<void> {
    try {
      const result = await browser.storage.local.get('dcmaar.error_monitor.performance_alerts');
      const alerts = Array.isArray(result['dcmaar.error_monitor.performance_alerts']) 
        ? result['dcmaar.error_monitor.performance_alerts'] 
        : [];
      
      alerts.push(alert);
      
      // Keep only last 50 alerts
      const trimmed = alerts.slice(-50);
      
      await browser.storage.local.set({
        'dcmaar.error_monitor.performance_alerts': trimmed
      });
    } catch (error) {
      console.error('Failed to store performance alert:', error);
    }
  }

  private async getStoredErrors(): Promise<ErrorReport[]> {
    try {
      const result = await browser.storage.local.get('dcmaar.error_monitor.errors');
      const errors = result['dcmaar.error_monitor.errors'];
      return Array.isArray(errors) ? errors : [];
    } catch {
      return [];
    }
  }

  private updateErrorRate(): void {
    const now = Date.now();
    this.errorRateWindow.push(now);
    
    // Remove old entries (older than 1 hour)
    const oneHourAgo = now - (60 * 60 * 1000);
    this.errorRateWindow = this.errorRateWindow.filter(time => time > oneHourAgo);
    
    // Keep window size manageable
    if (this.errorRateWindow.length > this.MAX_WINDOW_SIZE) {
      this.errorRateWindow = this.errorRateWindow.slice(-this.MAX_WINDOW_SIZE);
    }
  }

  private calculateCurrentErrorRate(): number {
    const now = Date.now();
    const oneHourAgo = now - (60 * 60 * 1000);
    const recentErrors = this.errorRateWindow.filter(time => time > oneHourAgo);
    
    // Calculate errors per minute
    return recentErrors.length / 60;
  }

  private async checkErrorRateThreshold(): Promise<void> {
    const errorRate = this.calculateCurrentErrorRate();
    
    if (errorRate > this.errorThresholds.errorRate) {
      await telemetryManager.track('system', 'error_rate.threshold_exceeded', {
        errorRate,
        threshold: this.errorThresholds.errorRate,
        windowSize: this.errorRateWindow.length
      }, 'error');
    }
  }

  private calculatePerformanceSeverity(value: number, threshold: number): PerformanceAlert['severity'] {
    const ratio = value / threshold;
    
    if (ratio > 3) return 'critical';
    if (ratio > 2) return 'error';
    return 'warning';
  }

  private getPerformanceAlertType(metric: string): PerformanceAlert['type'] {
    if (metric.includes('memory')) return 'memory';
    if (metric.includes('time') || metric.includes('duration')) return 'timing';
    if (metric.includes('quota') || metric.includes('storage')) return 'quota';
    return 'resource';
  }

  private sanitizeUrl(url: string): string {
    try {
      const urlObj = new URL(url);
      return `${urlObj.protocol}//${urlObj.host}${urlObj.pathname}`;
    } catch {
      return '[invalid-url]';
    }
  }

  private detectSource(): string {
    if (typeof window === 'undefined') return 'background';
    
    const url = window.location?.href || '';
    if (url.includes('popup.html')) return 'popup';
    if (url.includes('options.html')) return 'options';
    if (url.includes('dashboard.html')) return 'dashboard';
    
    return 'content';
  }

  private extractLineNumber(stack?: string): number | undefined {
    if (!stack) return undefined;
    
    const match = stack.match(/:(\d+):\d+/);
    return match ? parseInt(match[1], 10) : undefined;
  }

  private extractColumnNumber(stack?: string): number | undefined {
    if (!stack) return undefined;
    
    const match = stack.match(/:(\d+):(\d+)/);
    return match ? parseInt(match[2], 10) : undefined;
  }

  private extractFilename(stack?: string): string | undefined {
    if (!stack) return undefined;
    
    const lines = stack.split('\n');
    const firstLine = lines[1] || lines[0];
    const match = firstLine.match(/\((.*?):\d+:\d+\)/);
    return match ? match[1] : undefined;
  }

  private async capturePerformanceSnapshot(): Promise<ErrorReport['performanceSnapshot']> {
    const snapshot: ErrorReport['performanceSnapshot'] = {};

    try {
      // Memory snapshot
      if ('memory' in performance) {
        const memory = (performance as any).memory;
        snapshot.memory = {
          used: memory.usedJSHeapSize,
          total: memory.totalJSHeapSize,
          limit: memory.jsHeapSizeLimit
        };
      }

      // Timing snapshot
      const navigation = performance.getEntriesByType('navigation')[0] as any;
      if (navigation) {
        snapshot.timing = {
          domContentLoaded: navigation.domContentLoadedEventEnd - navigation.domContentLoadedEventStart,
          loadComplete: navigation.loadEventEnd - navigation.loadEventStart,
          domInteractive: navigation.domInteractive - navigation.fetchStart
        };
      }

      // Resource count
      snapshot.resources = performance.getEntriesByType('resource').length;

    } catch (error) {
      console.warn('Failed to capture performance snapshot:', error);
    }

    return snapshot;
  }

  private async loadThresholds(): Promise<void> {
    try {
      const result = await browser.storage.local.get('dcmaar.error_monitor.thresholds');
      if (result['dcmaar.error_monitor.thresholds']) {
        this.errorThresholds = { ...this.errorThresholds, ...result['dcmaar.error_monitor.thresholds'] };
      }
    } catch (error) {
      console.warn('Failed to load error thresholds:', error);
    }
  }

  private async getUserId(): Promise<string> {
    try {
      const result = await browser.storage.local.get('dcmaar.telemetry.userId');
      const userId = result['dcmaar.telemetry.userId'];
      return typeof userId === 'string' ? userId : 'anonymous';
    } catch {
      return 'anonymous';
    }
  }

  private async getSessionId(): Promise<string> {
    try {
      const result = await browser.storage.local.get('dcmaar.telemetry.sessionId');
      const sessionId = result['dcmaar.telemetry.sessionId'];
      return typeof sessionId === 'string' ? sessionId : 'unknown';
    } catch {
      return 'unknown';
    }
  }
}

// ================================================================================================
// Singleton Instance and Helper Functions
// ================================================================================================

export const errorMonitor = new ErrorMonitor();

// Global error handler setup
export const setupGlobalErrorHandling = () => {
  errorMonitor.initialize().catch(console.error);
};

// Helper functions for different error types
export const reportJavaScriptError = (error: Error, userAction?: string, metadata?: Record<string, any>) =>
  errorMonitor.reportError(error, 'javascript', 'medium', userAction, metadata);

export const reportStorageError = (operation: string, error: Error, context?: Record<string, any>) =>
  errorMonitor.reportStorageIssue(operation, error, context);

export const reportNetworkError = (url: string, error: Error, context?: Record<string, any>) =>
  errorMonitor.reportNetworkIssue(url, error, context);

export const reportPerformanceIssue = (metric: string, value: number, threshold: number, context?: Record<string, any>) =>
  errorMonitor.reportPerformanceIssue(metric, value, threshold, context);

// Error boundary helper for React components
// Note: This function should be used in React component files with proper React import
export const createErrorBoundary = () => {
  return {
    componentDidCatch: (error: Error, errorInfo: any) => {
      reportJavaScriptError(error, 'react.component.error', {
        componentStack: errorInfo.componentStack,
        errorBoundary: true
      });
    }
  };
};