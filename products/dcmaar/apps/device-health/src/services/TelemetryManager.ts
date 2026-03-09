/**
 * Telemetry Framework - Core Event Collection System
 * 
 * Provides comprehensive telemetry and observability for the DCMAAR extension.
 * Focuses on privacy-first data collection with user consent and anonymization.
 */

import browser from 'webextension-polyfill';
import { v4 as uuidv4 } from 'uuid';
// Note: crypto is not available in browser extensions, using Web Crypto API instead

// ================================================================================================
// Core Telemetry Types
// ================================================================================================

export interface TelemetryEvent {
  /** Unique event identifier */
  id: string;
  
  /** Event category for filtering and analysis */
  type: 'performance' | 'interaction' | 'system' | 'business' | 'error' | 'security';
  
  /** Event name for specific tracking */
  name: string;
  
  /** Unix timestamp in milliseconds */
  timestamp: number;
  
  /** Source component that generated the event */
  source: 'background' | 'popup' | 'options' | 'content' | 'dashboard';
  
  /** Event-specific data payload */
  data: Record<string, any>;
  
  /** Session identifier for user journey tracking */
  sessionId: string;
  
  /** Anonymous user identifier (hashed) */
  userId: string;
  
  /** Extension version for compatibility tracking */
  version: string;
  
  /** Event severity level */
  severity: 'info' | 'warn' | 'error' | 'critical';
  
  /** Optional tags for categorization */
  tags?: string[];
  
  /** Context information */
  context?: {
    url?: string;
    userAgent?: string;
    viewport?: { width: number; height: number };
    timezone?: string;
    language?: string;
  };
}

export interface TelemetryConfig {
  /** Enable/disable telemetry collection */
  enabled: boolean;
  
  /** Consent for different data types */
  consent: {
    performance: boolean;
    interactions: boolean;
    errors: boolean;
    analytics: boolean;
  };
  
  /** Data retention period in days */
  retentionDays: number;
  
  /** Batch size for event uploads */
  batchSize: number;
  
  /** Upload interval in milliseconds */
  uploadInterval: number;
  
  /** Enable debug logging */
  debug: boolean;
  
  /** Sampling rate (0-1) for performance events */
  samplingRate: number;
}

export interface TelemetryMetrics {
  /** Total events collected */
  totalEvents: number;
  
  /** Events by type */
  eventsByType: Record<string, number>;
  
  /** Events by source */
  eventsBySource: Record<string, number>;
  
  /** Error rate (errors/total events) */
  errorRate: number;
  
  /** Average event size in bytes */
  averageEventSize: number;
  
  /** Storage usage for telemetry data */
  storageUsage: number;
  
  /** Last upload timestamp */
  lastUpload: number;
}

// ================================================================================================
// Telemetry Manager - Main Service Class
// ================================================================================================

export class TelemetryManager {
  private config: TelemetryConfig;
  private sessionId: string;
  private userId: string;
  private extensionVersion: string;
  private eventQueue: TelemetryEvent[] = [];
  private uploadTimer: NodeJS.Timeout | null = null;
  private initialized = false;

  constructor() {
    this.sessionId = uuidv4();
    this.userId = '';
    this.extensionVersion = browser.runtime.getManifest().version;
    this.config = this.getDefaultConfig();
  }

  /**
   * Initialize the telemetry manager
   */
  async initialize(): Promise<void> {
    if (this.initialized) return;

    try {
      // Load configuration from storage
      await this.loadConfiguration();
      
      // Generate or load anonymous user ID
      await this.initializeUserId();
      
      // Start upload timer if enabled
      if (this.config.enabled) {
        this.startUploadTimer();
      }

      // Clean up old events
      await this.cleanupOldEvents();

      this.initialized = true;
      
      // Track initialization
      await this.track('system', 'telemetry.initialized', {
        config: {
          enabled: this.config.enabled,
          retentionDays: this.config.retentionDays,
          samplingRate: this.config.samplingRate
        }
      });

    } catch (error) {
      console.error('Failed to initialize TelemetryManager:', error);
    }
  }

  /**
   * Track an event
   */
  async track(
    type: TelemetryEvent['type'],
    name: string,
    data: Record<string, any> = {},
    severity: TelemetryEvent['severity'] = 'info',
    tags: string[] = []
  ): Promise<void> {
    if (!this.config.enabled || !this.hasConsent(type)) {
      return;
    }

    // Apply sampling for performance events
    if (type === 'performance' && Math.random() > this.config.samplingRate) {
      return;
    }

    const event: TelemetryEvent = {
      id: uuidv4(),
      type,
      name,
      timestamp: Date.now(),
      source: this.detectSource(),
      data: this.sanitizeData(data),
      sessionId: this.sessionId,
      userId: this.userId,
      version: this.extensionVersion,
      severity,
      tags,
      context: await this.getContext()
    };

    // Add to queue
    this.eventQueue.push(event);

    // Store immediately for critical events
    if (severity === 'critical' || severity === 'error') {
      await this.storeEvents([event]);
    }

    // Debug logging
    if (this.config.debug) {
      console.log(`[Telemetry] ${type}:${name}`, event);
    }

    // Trigger immediate upload for critical events
    if (severity === 'critical') {
      await this.uploadEvents();
    }
  }

  /**
   * Track performance metrics
   */
  async trackPerformance(name: string, metrics: Record<string, number>): Promise<void> {
    await this.track('performance', name, {
      metrics,
      timestamp: performance.now(),
      navigation: performance.getEntriesByType('navigation')[0] || null
    }, 'info', ['performance']);
  }

  /**
   * Track user interactions
   */
  async trackInteraction(element: string, action: string, data: Record<string, any> = {}): Promise<void> {
    await this.track('interaction', `${element}.${action}`, {
      element,
      action,
      ...data,
      timestamp: Date.now()
    }, 'info', ['user-interaction']);
  }

  /**
   * Track system health metrics
   */
  async trackSystemHealth(metrics: Record<string, any>): Promise<void> {
    const systemMetrics = {
      ...metrics,
      memory: await this.getMemoryUsage(),
      storage: await this.getStorageUsage(),
      timestamp: Date.now()
    };

    await this.track('system', 'health.check', systemMetrics, 'info', ['system-health']);
  }

  /**
   * Track business events
   */
  async trackBusinessEvent(event: string, data: Record<string, any> = {}): Promise<void> {
    await this.track('business', event, {
      ...data,
      timestamp: Date.now()
    }, 'info', ['business']);
  }

  /**
   * Track errors with full context
   */
  async trackError(error: Error, context: Record<string, any> = {}): Promise<void> {
    const errorData = {
      message: error.message,
      stack: error.stack,
      name: error.name,
      context,
      userAgent: navigator.userAgent,
      url: window.location?.href,
      timestamp: Date.now()
    };

    await this.track('error', 'javascript.error', errorData, 'error', ['error', 'javascript']);
  }

  /**
   * Track security events
   */
  async trackSecurity(event: string, data: Record<string, any> = {}): Promise<void> {
    await this.track('security', event, {
      ...data,
      timestamp: Date.now(),
      userAgent: navigator.userAgent
    }, 'warn', ['security']);
  }

  /**
   * Update telemetry configuration
   */
  async updateConfig(newConfig: Partial<TelemetryConfig>): Promise<void> {
    this.config = { ...this.config, ...newConfig };
    
    await browser.storage.local.set({
      'dcmaar.telemetry.config': this.config
    });

    // Restart upload timer if interval changed
    if (newConfig.uploadInterval && this.uploadTimer) {
      clearInterval(this.uploadTimer);
      this.startUploadTimer();
    }

    // Track configuration change
    await this.track('system', 'config.updated', {
      changes: Object.keys(newConfig),
      newConfig: this.config
    });
  }

  /**
   * Get telemetry metrics and statistics
   */
  async getMetrics(): Promise<TelemetryMetrics> {
    const events = await this.getStoredEvents();
    const eventsByType: Record<string, number> = {};
    const eventsBySource: Record<string, number> = {};
    let totalSize = 0;
    let errorCount = 0;

    events.forEach(event => {
      eventsByType[event.type] = (eventsByType[event.type] || 0) + 1;
      eventsBySource[event.source] = (eventsBySource[event.source] || 0) + 1;
      totalSize += JSON.stringify(event).length;
      
      if (event.severity === 'error' || event.severity === 'critical') {
        errorCount++;
      }
    });

    const storageInfo = await browser.storage.local.getBytesInUse();

    return {
      totalEvents: events.length,
      eventsByType,
      eventsBySource,
      errorRate: events.length > 0 ? errorCount / events.length : 0,
      averageEventSize: events.length > 0 ? totalSize / events.length : 0,
      storageUsage: storageInfo,
      lastUpload: await this.getLastUploadTime()
    };
  }

  /**
   * Export telemetry data for analysis
   */
  async exportData(startDate?: Date, endDate?: Date): Promise<TelemetryEvent[]> {
    const events = await this.getStoredEvents();
    
    if (!startDate && !endDate) {
      return events;
    }

    const start = startDate?.getTime() || 0;
    const end = endDate?.getTime() || Date.now();

    return events.filter(event => 
      event.timestamp >= start && event.timestamp <= end
    );
  }

  /**
   * Clear all telemetry data
   */
  async clearData(): Promise<void> {
    await browser.storage.local.remove([
      'dcmaar.telemetry.events',
      'dcmaar.telemetry.uploadTime'
    ]);

    this.eventQueue = [];

    await this.track('system', 'data.cleared');
  }

  // ================================================================================================
  // Private Helper Methods
  // ================================================================================================

  private getDefaultConfig(): TelemetryConfig {
    return {
      enabled: true,
      consent: {
        performance: true,
        interactions: true,
        errors: true,
        analytics: false
      },
      retentionDays: 30,
      batchSize: 50,
      uploadInterval: 5 * 60 * 1000, // 5 minutes
      debug: process.env.NODE_ENV === 'development',
      samplingRate: 0.1 // 10% sampling for performance events
    };
  }

  private async loadConfiguration(): Promise<void> {
    try {
      const result = await browser.storage.local.get('dcmaar.telemetry.config');
      if (result['dcmaar.telemetry.config']) {
        this.config = { ...this.config, ...result['dcmaar.telemetry.config'] };
      }
    } catch (error) {
      console.warn('Failed to load telemetry config:', error);
    }
  }

  private async initializeUserId(): Promise<void> {
    try {
      const result = await browser.storage.local.get('dcmaar.telemetry.userId');
      
      if (result['dcmaar.telemetry.userId'] && typeof result['dcmaar.telemetry.userId'] === 'string') {
        this.userId = result['dcmaar.telemetry.userId'];
      } else {
        // Generate anonymous hash-based user ID using Web Crypto API
        const randomData = `${Date.now()}-${Math.random()}-${navigator.userAgent}`;
        const encoder = new TextEncoder();
        const data = encoder.encode(randomData);
        const hashBuffer = await crypto.subtle.digest('SHA-256', data);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        this.userId = hashArray.map(b => b.toString(16).padStart(2, '0')).join('').substring(0, 16);
        
        await browser.storage.local.set({
          'dcmaar.telemetry.userId': this.userId
        });
      }
    } catch (error) {
      console.warn('Failed to initialize user ID:', error);
      this.userId = 'anonymous';
    }
  }

  private detectSource(): TelemetryEvent['source'] {
    if (typeof window === 'undefined') return 'background';
    
    const url = window.location?.href || '';
    if (url.includes('popup.html')) return 'popup';
    if (url.includes('options.html')) return 'options';
    if (url.includes('dashboard.html')) return 'dashboard';
    
    return 'content';
  }

  private sanitizeData(data: Record<string, any>): Record<string, any> {
    // Remove potentially sensitive information
    const sanitized = { ...data };
    
    // Remove URLs that might contain sensitive query parameters
    if (sanitized.url && typeof sanitized.url === 'string') {
      try {
        const url = new URL(sanitized.url);
        sanitized.url = `${url.protocol}//${url.host}${url.pathname}`;
      } catch {
        delete sanitized.url;
      }
    }

    // Remove any keys that might contain sensitive data
    const sensitiveKeys = ['password', 'token', 'key', 'secret', 'auth', 'session'];
    Object.keys(sanitized).forEach(key => {
      if (sensitiveKeys.some(sensitive => key.toLowerCase().includes(sensitive))) {
        sanitized[key] = '[REDACTED]';
      }
    });

    return sanitized;
  }

  private async getContext(): Promise<TelemetryEvent['context']> {
    const context: TelemetryEvent['context'] = {
      userAgent: navigator.userAgent,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      language: navigator.language
    };

    if (typeof window !== 'undefined') {
      context.url = window.location?.href;
      context.viewport = {
        width: window.innerWidth,
        height: window.innerHeight
      };
    }

    return context;
  }

  private hasConsent(type: TelemetryEvent['type']): boolean {
    switch (type) {
      case 'performance':
        return this.config.consent.performance;
      case 'interaction':
        return this.config.consent.interactions;
      case 'error':
      case 'security':
        return this.config.consent.errors;
      case 'business':
      case 'system':
        return this.config.consent.analytics;
      default:
        return false;
    }
  }

  private startUploadTimer(): void {
    if (this.uploadTimer) {
      clearInterval(this.uploadTimer);
    }

    this.uploadTimer = setInterval(() => {
      this.uploadEvents().catch(console.error);
    }, this.config.uploadInterval);
  }

  private async storeEvents(events: TelemetryEvent[]): Promise<void> {
    if (events.length === 0) return;

    try {
      const existing = await this.getStoredEvents();
      const combined = [...existing, ...events];
      
      await browser.storage.local.set({
        'dcmaar.telemetry.events': combined
      });
    } catch (error) {
      console.error('Failed to store telemetry events:', error);
    }
  }

  private async getStoredEvents(): Promise<TelemetryEvent[]> {
    try {
      const result = await browser.storage.local.get('dcmaar.telemetry.events');
      const events = result['dcmaar.telemetry.events'];
      return Array.isArray(events) ? events : [];
    } catch (error) {
      console.error('Failed to get stored events:', error);
      return [];
    }
  }

  private async uploadEvents(): Promise<void> {
    if (this.eventQueue.length === 0) return;

    try {
      // Move events from queue to batch
      const batch = this.eventQueue.splice(0, this.config.batchSize);
      
      // Store events locally first
      await this.storeEvents(batch);

      // In a real implementation, you would send to your analytics service
      // For now, we'll just log that events would be uploaded
      if (this.config.debug) {
        console.log(`[Telemetry] Would upload ${batch.length} events`);
      }

      // Update last upload time
      await browser.storage.local.set({
        'dcmaar.telemetry.uploadTime': Date.now()
      });

    } catch (error) {
      console.error('Failed to upload telemetry events:', error);
      
      // Track upload failure
      await this.track('error', 'telemetry.upload.failed', {
        error: error instanceof Error ? error.message : String(error),
        queueSize: this.eventQueue.length
      });
    }
  }

  private async getLastUploadTime(): Promise<number> {
    try {
      const result = await browser.storage.local.get('dcmaar.telemetry.uploadTime');
      const uploadTime = result['dcmaar.telemetry.uploadTime'];
      return typeof uploadTime === 'number' ? uploadTime : 0;
    } catch {
      return 0;
    }
  }

  private async cleanupOldEvents(): Promise<void> {
    try {
      const events = await this.getStoredEvents();
      const cutoffTime = Date.now() - (this.config.retentionDays * 24 * 60 * 60 * 1000);
      
      const filtered = events.filter(event => event.timestamp > cutoffTime);
      
      if (filtered.length !== events.length) {
        await browser.storage.local.set({
          'dcmaar.telemetry.events': filtered
        });
        
        await this.track('system', 'cleanup.completed', {
          removed: events.length - filtered.length,
          remaining: filtered.length
        });
      }
    } catch (error) {
      console.error('Failed to cleanup old events:', error);
    }
  }

  private async getMemoryUsage(): Promise<Record<string, number>> {
    if ('memory' in performance) {
      return {
        used: (performance as any).memory.usedJSHeapSize,
        total: (performance as any).memory.totalJSHeapSize,
        limit: (performance as any).memory.jsHeapSizeLimit
      };
    }
    return {};
  }

  private async getStorageUsage(): Promise<number> {
    try {
      return await browser.storage.local.getBytesInUse();
    } catch {
      return 0;
    }
  }
}

// ================================================================================================
// Singleton Instance and Helper Functions
// ================================================================================================

export const telemetryManager = new TelemetryManager();

// Convenience functions for common tracking scenarios
export const trackPerformance = (name: string, metrics: Record<string, number>) => 
  telemetryManager.trackPerformance(name, metrics);

export const trackInteraction = (element: string, action: string, data?: Record<string, any>) =>
  telemetryManager.trackInteraction(element, action, data);

export const trackError = (error: Error, context?: Record<string, any>) =>
  telemetryManager.trackError(error, context);

export const trackBusinessEvent = (event: string, data?: Record<string, any>) =>
  telemetryManager.trackBusinessEvent(event, data);

// Performance measurement helper
export function measurePerformance<T>(name: string, fn: () => T): T {
  const start = performance.now();
  const result = fn();
  const end = performance.now();
  
  trackPerformance(name, {
    duration: end - start,
    timestamp: start
  });
  
  return result;
}

// Async performance measurement helper
export async function measurePerformanceAsync<T>(name: string, fn: () => Promise<T>): Promise<T> {
  const start = performance.now();
  const result = await fn();
  const end = performance.now();
  
  await trackPerformance(name, {
    duration: end - start,
    timestamp: start
  });
  
  return result;
}

// Error boundary integration
export function withErrorTracking<T extends (...args: any[]) => any>(fn: T): T {
  return ((...args: any[]) => {
    try {
      const result = fn(...args);
      
      if (result instanceof Promise) {
        return result.catch((error: Error) => {
          trackError(error, { function: fn.name, args: args.length });
          throw error;
        });
      }
      
      return result;
    } catch (error) {
      trackError(error as Error, { function: fn.name, args: args.length });
      throw error;
    }
  }) as T;
}