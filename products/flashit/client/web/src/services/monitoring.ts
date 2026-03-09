/**
 * Monitoring Service for Flashit Web
 * Performance monitoring, error tracking, and analytics
 *
 * @doc.type service
 * @doc.purpose Web application monitoring and analytics
 * @doc.layer product
 * @doc.pattern MonitoringService
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

export type MetricType = 'counter' | 'gauge' | 'histogram' | 'timing';
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export interface Metric {
  name: string;
  type: MetricType;
  value: number;
  timestamp: number;
  tags?: Record<string, string>;
}

export interface PerformanceEntry {
  name: string;
  duration: number;
  startTime: number;
  type: string;
}

export interface ResourceTiming {
  name: string;
  duration: number;
  size: number;
  type: string;
}

export interface WebVitals {
  FCP?: number; // First Contentful Paint
  LCP?: number; // Largest Contentful Paint
  FID?: number; // First Input Delay
  CLS?: number; // Cumulative Layout Shift
  TTFB?: number; // Time to First Byte
}

export interface Breadcrumb {
  timestamp: number;
  category: string;
  message: string;
  level: LogLevel;
  data?: Record<string, unknown>;
}

// ============================================================================
// Constants
// ============================================================================

const MAX_BREADCRUMBS = 100;
const MAX_METRICS = 1000;

// ============================================================================
// Monitoring Service
// ============================================================================

/**
 * MonitoringService for web application
 */
class MonitoringService {
  private static instance: MonitoringService | null = null;

  private metrics: Metric[] = [];
  private breadcrumbs: Breadcrumb[] = [];
  private webVitals: WebVitals = {};
  private performanceObserver: PerformanceObserver | null = null;

  private constructor() {
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
  private initialize(): void {
    // Record page load
    this.recordMetric('page.load', 'counter', 1);
    this.addBreadcrumb('navigation', 'Page loaded', 'info');

    // Setup performance monitoring
    this.setupPerformanceMonitoring();

    // Setup Web Vitals
    this.setupWebVitals();

    // Monitor navigation
    this.monitorNavigation();

    // Monitor errors
    this.monitorErrors();

    // Monitor resources
    this.monitorResources();
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
    if (this.metrics.length > MAX_METRICS) {
      this.metrics.shift();
    }

    // Send to backend (debounced)
    this.sendMetrics();
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
   * Track page view
   */
  trackPageView(path: string, title?: string): void {
    this.recordMetric('page.view', 'counter', 1, {
      path,
      title: title || document.title,
    });

    this.addBreadcrumb('navigation', `Page view: ${path}`, 'info', {
      title,
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
   * Track API call
   */
  trackApiCall(
    endpoint: string,
    method: string,
    duration: number,
    statusCode: number
  ): void {
    this.recordMetric('api.call', 'histogram', duration, {
      endpoint,
      method,
      status: statusCode.toString(),
    });

    const level = statusCode >= 500 ? 'error' : statusCode >= 400 ? 'warn' : 'info';
    this.addBreadcrumb('api', `${method} ${endpoint}`, level, {
      duration,
      statusCode,
    });
  }

  /**
   * Track error
   */
  trackError(error: Error, context?: Record<string, unknown>): void {
    this.recordMetric('error.count', 'counter', 1, {
      type: error.name,
      message: error.message,
    });

    this.addBreadcrumb('error', error.message, 'error', {
      stack: error.stack,
      ...context,
    });
  }

  /**
   * Track performance timing
   */
  trackTiming(name: string, duration: number, tags?: Record<string, string>): void {
    this.recordMetric('timing', 'histogram', duration, {
      name,
      ...tags,
    });
  }

  /**
   * Get Web Vitals
   */
  getWebVitals(): WebVitals {
    return { ...this.webVitals };
  }

  /**
   * Get metrics
   */
  getMetrics(): Metric[] {
    return [...this.metrics];
  }

  /**
   * Get breadcrumbs
   */
  getBreadcrumbs(): Breadcrumb[] {
    return [...this.breadcrumbs];
  }

  /**
   * Get performance entries
   */
  getPerformanceEntries(type?: string): PerformanceEntry[] {
    const entries = type
      ? performance.getEntriesByType(type)
      : performance.getEntries();

    return entries.map((entry) => ({
      name: entry.name,
      duration: entry.duration,
      startTime: entry.startTime,
      type: entry.entryType,
    }));
  }

  /**
   * Clear all data
   */
  clearAll(): void {
    this.metrics = [];
    this.breadcrumbs = [];
    this.webVitals = {};
  }

  /**
   * Send metrics to backend
   */
  private sendMetricsDebounced = this.debounce(() => {
    if (this.metrics.length === 0) return;

    // TODO: Send to backend
    // fetch('/api/metrics', {
    //   method: 'POST',
    //   headers: { 'Content-Type': 'application/json' },
    //   body: JSON.stringify(this.metrics),
    // });

    console.debug('Sending metrics:', this.metrics.length);
  }, 5000);

  private sendMetrics(): void {
    this.sendMetricsDebounced();
  }

  // ============================================================================
  // Performance Monitoring
  // ============================================================================

  private setupPerformanceMonitoring(): void {
    if (!('PerformanceObserver' in window)) return;

    try {
      // Monitor navigation timing
      this.performanceObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (entry.entryType === 'navigation') {
            this.handleNavigationEntry(entry as PerformanceNavigationTiming);
          } else if (entry.entryType === 'resource') {
            this.handleResourceEntry(entry as PerformanceResourceTiming);
          } else if (entry.entryType === 'paint') {
            this.handlePaintEntry(entry);
          }
        }
      });

      this.performanceObserver.observe({
        entryTypes: ['navigation', 'resource', 'paint', 'measure'],
      });
    } catch (e) {
      console.warn('Failed to setup performance observer:', e);
    }
  }

  private handleNavigationEntry(entry: PerformanceNavigationTiming): void {
    // DNS lookup
    const dnsTime = entry.domainLookupEnd - entry.domainLookupStart;
    this.recordMetric('navigation.dns', 'timing', dnsTime);

    // TCP connection
    const tcpTime = entry.connectEnd - entry.connectStart;
    this.recordMetric('navigation.tcp', 'timing', tcpTime);

    // Request/Response
    const requestTime = entry.responseStart - entry.requestStart;
    this.recordMetric('navigation.request', 'timing', requestTime);

    // DOM processing
    const domTime = entry.domContentLoadedEventEnd - entry.domContentLoadedEventStart;
    this.recordMetric('navigation.dom', 'timing', domTime);

    // Page load
    const loadTime = entry.loadEventEnd - entry.loadEventStart;
    this.recordMetric('navigation.load', 'timing', loadTime);

    // TTFB
    const ttfb = entry.responseStart - entry.requestStart;
    this.webVitals.TTFB = ttfb;
    this.recordMetric('vitals.ttfb', 'gauge', ttfb);
  }

  private handleResourceEntry(entry: PerformanceResourceTiming): void {
    const duration = entry.duration;
    const size = (entry as unknown as { transferSize?: number }).transferSize || 0;

    this.recordMetric('resource.load', 'histogram', duration, {
      type: entry.initiatorType,
      name: entry.name,
    });

    if (size > 0) {
      this.recordMetric('resource.size', 'histogram', size, {
        type: entry.initiatorType,
      });
    }
  }

  private handlePaintEntry(entry: PerformanceEntry): void {
    if (entry.name === 'first-contentful-paint') {
      this.webVitals.FCP = entry.startTime;
      this.recordMetric('vitals.fcp', 'gauge', entry.startTime);
    }
  }

  // ============================================================================
  // Web Vitals
  // ============================================================================

  private setupWebVitals(): void {
    // LCP (Largest Contentful Paint)
    try {
      const lcpObserver = new PerformanceObserver((list) => {
        const entries = list.getEntries();
        const lastEntry = entries[entries.length - 1];
        this.webVitals.LCP = lastEntry.startTime;
        this.recordMetric('vitals.lcp', 'gauge', lastEntry.startTime);
      });
      lcpObserver.observe({ entryTypes: ['largest-contentful-paint'] });
    } catch (e) {
      // LCP not supported
    }

    // FID (First Input Delay)
    try {
      const fidObserver = new PerformanceObserver((list) => {
        const entries = list.getEntries();
        for (const entry of entries) {
          const fid = (entry as unknown as { processingStart: number }).processingStart - entry.startTime;
          this.webVitals.FID = fid;
          this.recordMetric('vitals.fid', 'gauge', fid);
        }
      });
      fidObserver.observe({ entryTypes: ['first-input'] });
    } catch (e) {
      // FID not supported
    }

    // CLS (Cumulative Layout Shift)
    try {
      let clsValue = 0;
      const clsObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (!(entry as unknown as { hadRecentInput: boolean }).hadRecentInput) {
            clsValue += (entry as unknown as { value: number }).value;
            this.webVitals.CLS = clsValue;
            this.recordMetric('vitals.cls', 'gauge', clsValue);
          }
        }
      });
      clsObserver.observe({ entryTypes: ['layout-shift'] });
    } catch (e) {
      // CLS not supported
    }
  }

  // ============================================================================
  // Navigation Monitoring
  // ============================================================================

  private monitorNavigation(): void {
    // Track page visibility changes
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        this.addBreadcrumb('visibility', 'Page hidden', 'info');
        this.recordMetric('page.hidden', 'counter', 1);
      } else {
        this.addBreadcrumb('visibility', 'Page visible', 'info');
        this.recordMetric('page.visible', 'counter', 1);
      }
    });

    // Track online/offline
    window.addEventListener('online', () => {
      this.addBreadcrumb('connection', 'Online', 'info');
      this.recordMetric('connection.online', 'counter', 1);
    });

    window.addEventListener('offline', () => {
      this.addBreadcrumb('connection', 'Offline', 'warn');
      this.recordMetric('connection.offline', 'counter', 1);
    });
  }

  // ============================================================================
  // Error Monitoring
  // ============================================================================

  private monitorErrors(): void {
    // Global error handler
    window.addEventListener('error', (event) => {
      this.trackError(event.error || new Error(event.message), {
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno,
      });
    });

    // Unhandled promise rejections
    window.addEventListener('unhandledrejection', (event) => {
      this.trackError(
        new Error(event.reason),
        { type: 'unhandled-promise-rejection' }
      );
    });
  }

  // ============================================================================
  // Resource Monitoring
  // ============================================================================

  private monitorResources(): void {
    // Monitor when page is fully loaded
    window.addEventListener('load', () => {
      this.addBreadcrumb('page', 'Fully loaded', 'info');
      
      // Collect resource timings
      const resources = performance.getEntriesByType('resource') as PerformanceResourceTiming[];
      
      let totalSize = 0;
      let totalDuration = 0;

      for (const resource of resources) {
        totalDuration += resource.duration;
        totalSize += (resource as unknown as { transferSize?: number }).transferSize || 0;
      }

      this.recordMetric('resources.total_count', 'gauge', resources.length);
      this.recordMetric('resources.total_size', 'gauge', totalSize);
      this.recordMetric('resources.total_duration', 'gauge', totalDuration);
    });
  }

  // ============================================================================
  // Utilities
  // ============================================================================

  private debounce<T extends (...args: unknown[]) => unknown>(
    func: T,
    wait: number
  ): (...args: Parameters<T>) => void {
    let timeout: NodeJS.Timeout | null = null;
    
    return (...args: Parameters<T>) => {
      if (timeout) clearTimeout(timeout);
      timeout = setTimeout(() => func(...args), wait);
    };
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
  
  trackPageView: (path: string, title?: string) => {
    getMonitoring().trackPageView(path, title);
  },
  
  trackAction: (action: string, properties?: Record<string, unknown>) => {
    getMonitoring().trackAction(action, properties);
  },
  
  trackApiCall: (endpoint: string, method: string, duration: number, statusCode: number) => {
    getMonitoring().trackApiCall(endpoint, method, duration, statusCode);
  },
  
  trackError: (error: Error, context?: Record<string, unknown>) => {
    getMonitoring().trackError(error, context);
  },
};

export default MonitoringService;
