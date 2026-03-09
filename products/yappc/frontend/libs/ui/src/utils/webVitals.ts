/**
 * Web Vitals monitoring utilities
 */

/**
 *
 */
export interface WebVitalsMetric {
  name: string;
  value: number;
  rating: 'good' | 'needs-improvement' | 'poor';
  delta: number;
  id: string;
}

/**
 *
 */
type ReportHandler = (metric: WebVitalsMetric) => void;

/**
 * Report Web Vitals metrics
 * 
 * @param onReport - Callback to handle metrics
 * 
 * @example
 * ```tsx
 * reportWebVitals((metric) => {
 *   console.log(metric);
 *   // Send to analytics
 *   analytics.track('Web Vitals', {
 *     name: metric.name,
 *     value: metric.value,
 *     rating: metric.rating,
 *   });
 * });
 * ```
 */
export function reportWebVitals(onReport: ReportHandler): void {
  if (typeof window === 'undefined') return;

  // Dynamically import web-vitals to avoid bundling it
  import('web-vitals').then(({ onCLS, onFID, onFCP, onLCP, onTTFB }) => {
    onCLS(onReport);
    onFID(onReport);
    onFCP(onReport);
    onLCP(onReport);
    onTTFB(onReport);
  }).catch((error) => {
    console.error('Failed to load web-vitals:', error);
  });
}

/**
 * Get performance rating for a metric
 */
export function getPerformanceRating(
  name: string,
  value: number
): 'good' | 'needs-improvement' | 'poor' {
  const thresholds: Record<string, [number, number]> = {
    FCP: [1800, 3000],
    LCP: [2500, 4000],
    FID: [100, 300],
    CLS: [0.1, 0.25],
    TTFB: [800, 1800],
  };

  const [good, poor] = thresholds[name] || [0, 0];
  
  if (value <= good) return 'good';
  if (value <= poor) return 'needs-improvement';
  return 'poor';
}

/**
 * Performance observer for custom metrics
 */
export class PerformanceMonitor {
  private marks: Map<string, number> = new Map();
  private measures: Map<string, number> = new Map();

  /**
   * Mark a performance point
   */
  mark(name: string): void {
    if (typeof performance === 'undefined') return;
    
    performance.mark(name);
    this.marks.set(name, performance.now());
  }

  /**
   * Measure time between two marks
   */
  measure(name: string, startMark: string, endMark?: string): number {
    if (typeof performance === 'undefined') return 0;

    try {
      if (endMark) {
        performance.measure(name, startMark, endMark);
      } else {
        performance.measure(name, startMark);
      }

      const entries = performance.getEntriesByName(name, 'measure');
      const duration = entries[entries.length - 1]?.duration || 0;
      
      this.measures.set(name, duration);
      return duration;
    } catch (error) {
      console.warn(`Failed to measure ${name}:`, error);
      return 0;
    }
  }

  /**
   * Get all measures
   */
  getMeasures(): Record<string, number> {
    return Object.fromEntries(this.measures);
  }

  /**
   * Clear all marks and measures
   */
  clear(): void {
    if (typeof performance === 'undefined') return;
    
    performance.clearMarks();
    performance.clearMeasures();
    this.marks.clear();
    this.measures.clear();
  }
}

/**
 * Track component render time
 */
export function trackRenderTime(componentName: string): () => void {
  const monitor = new PerformanceMonitor();
  const startMark = `${componentName}-render-start`;
  const endMark = `${componentName}-render-end`;
  const measureName = `${componentName}-render`;

  monitor.mark(startMark);

  return () => {
    monitor.mark(endMark);
    const duration = monitor.measure(measureName, startMark, endMark);
    
    if (duration > 16) { // Longer than one frame (60fps)
      console.warn(`Slow render: ${componentName} took ${duration.toFixed(2)}ms`);
    }
  };
}
