import { onCLS, onLCP, onFCP, onTTFB, onINP, type Metric } from 'web-vitals';

import { performanceMonitor } from '../../services/PerformanceMonitor';

import { devLog } from './dev-logger';

// Environment detection
const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined';
const isServiceWorker = typeof self !== 'undefined' && 'ServiceWorkerGlobalScope' in self;

// Safe performance access
interface NavigationTimingLike {
  navigationStart?: number;
  loadEventEnd?: number;
  [key: string]: number | undefined;
}

type PerformanceWithTiming = Performance & { timing?: NavigationTimingLike };

const safePerformance: PerformanceWithTiming | undefined = isBrowser
  ? (window.performance as PerformanceWithTiming)
  : undefined;

type MetricHandler = (metric: Metric) => void;

/**
 * Reports Web Vitals metrics to the console and PerformanceMonitor
 * @param onPerfEntry Optional callback function to handle metrics
 */
const reportWebVitals = (onPerfEntry?: MetricHandler): void => {
  if (!isBrowser || isServiceWorker) {
    devLog.debug('Skipping Web Vitals in non-browser context');
    return;
  }

  if (!onPerfEntry || typeof onPerfEntry !== 'function') {
    return;
  }

  try {
    // Track Core Web Vitals
    onCLS((metric) => {
      onPerfEntry(metric);
      trackWebVital(metric);
    });
    
    // Use INP (Interaction to Next Paint) instead of FID
    onINP((metric) => {
      onPerfEntry(metric);
      trackWebVital(metric);
    });
    
    onLCP((metric) => {
      onPerfEntry(metric);
      trackWebVital(metric);
    });
    
    onFCP((metric) => {
      onPerfEntry(metric);
      trackWebVital(metric);
    });
    
    onTTFB((metric) => {
      onPerfEntry(metric);
      trackWebVital(metric);
    });
  } catch (error) {
    devLog.error('Error initializing Web Vitals:', error);
  }
};

/**
 * Tracks a Web Vitals metric using PerformanceMonitor
 * @param metric The Web Vitals metric to track
 */
const trackWebVital = (metric: Metric): void => {
  try {
    const { name, value, rating } = metric;
    const tags: Record<string, string> = {
      rating: rating || 'unknown',
      id: metric.id,
      navigationType: metric.navigationType || 'navigate',
    };

    // Add entry type if available
    if (metric.entries && metric.entries.length > 0) {
      const entry = metric.entries[0];
      if (entry && 'entryType' in entry) {
        tags.entryType = entry.entryType;
      }
    }

    // Record the metric with appropriate type
    performanceMonitor.record('timing', `web_vitals.${name.toLowerCase()}`, value, tags);
  } catch (error) {
    devLog.error('Error tracking Web Vitals:', error);
  }
};

/**
 * Logs Web Vitals metrics to the console
 * @param metric The Web Vitals metric to log
 */
const logMetrics = (metric: Metric): void => {
  const logData: Record<string, unknown> = {
    value: Number(metric.value.toFixed(2)),
    id: metric.id,
    rating: metric.rating,
    navigationType: metric.navigationType,
  };

  // Add entries if available
  if (metric.entries && metric.entries.length > 0) {
    logData.entries = metric.entries;
  }

  // Log with appropriate level based on rating
  const logMethod = metric.rating === 'poor' ? 'warn' : 'info';
  devLog[logMethod](`[Web Vitals] ${metric.name}:`, logData);
};

/**
 * Initializes metrics collection
 * @param options Configuration options
 */
const initMetrics = (options: { trackWebVitals?: boolean; logToConsole?: boolean } = {}): void => {
  if (isServiceWorker) {
    devLog.debug('Skipping metrics initialization in service worker');
    return;
  }

  const { trackWebVitals = true, logToConsole = process.env.NODE_ENV === 'development' } = options;

  try {
    if (trackWebVitals && isBrowser) {
      reportWebVitals(logToConsole ? logMetrics : undefined);
    }

    // Track page load performance
    if (isBrowser) {
      trackPageLoad();
    }
  } catch (error) {
    devLog.error('Error initializing metrics:', error);
  }
};

/**
 * Tracks page load performance metrics
 */
const trackPageLoad = (): void => {
  // Skip if not in a browser environment or if performance API is not available
  if (!isBrowser || isServiceWorker || !safePerformance) {
    devLog.debug('Skipping page load tracking in current context');
    return;
  }

  try {
    // Track page load time if navigation timing is available
    const timing = safePerformance.timing;

    const loadEventEnd = typeof timing?.loadEventEnd === 'number' ? timing.loadEventEnd : undefined;
    const navigationStart = typeof timing?.navigationStart === 'number' ? timing.navigationStart : undefined;

    if (loadEventEnd !== undefined && navigationStart !== undefined) {
      const pageLoadTime = loadEventEnd - navigationStart;
      
      // Safely get navigation type
      let navigationType = 'navigate';
      try {
        if (typeof safePerformance.getEntriesByType === 'function') {
          const navEntry = safePerformance.getEntriesByType('navigation')?.[0] as PerformanceNavigationTiming | undefined;
          if (navEntry?.type) {
            navigationType = navEntry.type;
          }
        }
      } catch (e) {
        devLog.debug('Could not determine navigation type:', e);
      }
      
      performanceMonitor.record('timing', 'page.load', pageLoadTime, {
        type: 'full',
        navigationType,
      });
    }

    // Track resource timing if available
    if (typeof safePerformance.getEntriesByType === 'function') {
      try {
        const resources = safePerformance.getEntriesByType('resource') as PerformanceResourceTiming[];
        resources?.forEach((resource) => {
          performanceMonitor.record('timing', 'resource.load', resource.duration, {
            type: resource.initiatorType,
            name: resource.name,
            transferSize: resource.transferSize?.toString() || '0',
          });
        });
      } catch (e) {
        devLog.debug('Error getting resource timing:', e);
      }
    }
  } catch (error) {
    devLog.error('Error tracking page load metrics:', error);
  }
};

// Export all metrics functions directly
export {
  reportWebVitals,
  logMetrics,
  initMetrics,
  trackPageLoad,
  trackWebVital,
};

// Also export as default for backward compatibility
const metrics = {
  reportWebVitals,
  logMetrics,
  initMetrics,
  trackPageLoad,
  trackWebVital,
};

export default metrics;
