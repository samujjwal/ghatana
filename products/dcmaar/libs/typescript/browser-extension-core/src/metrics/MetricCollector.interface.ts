/**
 * @fileoverview Metric Collector Interface
 *
 * Defines the contract for collecting browser-specific metrics.
 * Implementations should focus on extracting metrics from browser APIs only.
 *
 * @module core/interfaces/MetricCollector
 */

/**
 * Page performance metrics from Navigation Timing API
 */
export type WebVitalRating = 'good' | 'needs-improvement' | 'poor';

export interface PageMetrics {
  /** URL of the page */
  url: string;
  /** Page title */
  title: string;
  /** Timestamp when metrics were collected */
  timestamp: number;
  /** Time to first byte (ms) */
  ttfb?: number;
  /** DOM content loaded time (ms) */
  domContentLoaded?: number;
  /** Page load time (ms) */
  loadTime?: number;
  /** First contentful paint (ms) */
  fcp?: number;
  /** Largest contentful paint (ms) */
  lcp?: number;
  /** Cumulative layout shift */
  cls?: number;
  /** First input delay (ms) */
  fid?: number;
  /** Interaction to next paint (ms) */
  inp?: number;
  /** Total blocking time (ms) */
  tbt?: number;
  /** Speed Index approximation (ms) */
  speedIndex?: number;
  /** Ratings per web vital */
  ratings?: Partial<Record<'lcp' | 'cls' | 'inp' | 'fid', WebVitalRating>>;
  /** Overall web vital rating */
  overallRating?: WebVitalRating;
  /** Additional diagnostics */
  diagnostics?: {
    /** Count of long tasks observed */
    longTaskCount?: number;
    /** Sum of blocking time above 50ms */
    totalBlockingTime?: number;
    /** Longest interaction latency observed (ms) */
    maxInteractionLatency?: number;
  };
  /** Additional contextual details */
  details?: {
    /** Descriptor for LCP element */
    lcpElement?: string;
    /** Timestamp when LCP was recorded (epoch milliseconds) */
    lcpTimestamp?: number;
    /** Interaction type that produced INP */
    inpInteractionType?: string;
    /** Timestamp when INP was recorded (epoch milliseconds) */
    inpTimestamp?: number;
  };
  /** Custom user marks captured on the page */
  customMarks?: Record<string, number>;
}

/**
 * Navigation metrics (route changes, history events)
 */
export interface NavigationMetrics {
  /** Navigation type (navigate, reload, back_forward) */
  type: 'navigate' | 'reload' | 'back_forward' | 'prerender';
  /** Source URL */
  from?: string;
  /** Destination URL */
  to: string;
  /** Timestamp */
  timestamp: number;
  /** Navigation duration (ms) */
  duration?: number;
  /** Tab ID */
  tabId?: number;
}

/**
 * Resource loading metrics (scripts, stylesheets, images, etc.)
 */
export interface ResourceMetrics {
  /** Resource URL */
  url: string;
  /** Resource type */
  type: 'script' | 'stylesheet' | 'image' | 'font' | 'xhr' | 'fetch' | 'other';
  /** Resource size in bytes */
  size: number;
  /** Load duration (ms) */
  duration: number;
  /** HTTP status code */
  status?: number;
  /** Whether resource was cached */
  cached: boolean;
  /** Timestamp when resource started loading */
  timestamp: number;
  /** Transfer size (actual bytes transferred, 0 if cached) */
  transferSize?: number;
}

/**
 * User interaction metrics (clicks, scrolls, form submissions)
 */
export interface InteractionMetrics {
  /** Interaction type */
  type: 'click' | 'scroll' | 'submit' | 'input' | 'keypress';
  /** Target element selector */
  target?: string;
  /** Target element type */
  element?: string;
  /** Timestamp */
  timestamp: number;
  /** Page URL where interaction occurred */
  url: string;
  /** Additional interaction data */
  data?: Record<string, unknown>;
}

/**
 * Tab metrics (creation, updates, switching)
 */
export interface TabMetrics {
  /** Tab ID */
  tabId: number;
  /** Event type */
  event: 'created' | 'updated' | 'activated' | 'removed';
  /** Tab URL */
  url?: string;
  /** Tab title */
  title?: string;
  /** Window ID */
  windowId?: number;
  /** Is tab active */
  active?: boolean;
  /** Timestamp */
  timestamp: number;
}

/**
 * Metric Collector Interface
 *
 * Implementations collect various browser metrics using browser APIs.
 * All methods should be non-blocking and return promises.
 *
 * @example
 * ```typescript
 * class PageMetricsCollector implements MetricCollector {
 *   async collectPageMetrics(): Promise<PageMetrics> {
 *     const timing = performance.timing;
 *     return {
 *       url: window.location.href,
 *       title: document.title,
 *       timestamp: Date.now(),
 *       ttfb: timing.responseStart - timing.requestStart,
 *       domContentLoaded: timing.domContentLoadedEventEnd - timing.navigationStart,
 *       loadTime: timing.loadEventEnd - timing.navigationStart,
 *     };
 *   }
 * }
 * ```
 */
export interface MetricCollector {
  /**
   * Collect page performance metrics from Navigation Timing API
   *
   * @returns Promise resolving to page metrics
   */
  collectPageMetrics(): Promise<PageMetrics>;

  /**
   * Collect navigation metrics (route changes)
   *
   * @returns Promise resolving to navigation metrics
   */
  collectNavigationMetrics(): Promise<NavigationMetrics>;

  /**
   * Collect resource loading metrics
   *
   * @returns Promise resolving to array of resource metrics
   */
  collectResourceMetrics(): Promise<ResourceMetrics[]>;

  /**
   * Collect user interaction metrics
   *
   * @returns Promise resolving to array of interaction metrics
   */
  collectUserInteractionMetrics(): Promise<InteractionMetrics[]>;

  /**
   * Collect tab-related metrics
   *
   * @returns Promise resolving to array of tab metrics
   */
  collectTabMetrics(): Promise<TabMetrics[]>;
}

/**
 * Batch collector for efficient metric collection
 */
export interface BatchMetricCollector extends MetricCollector {
  /**
   * Collect all available metrics in one batch
   *
   * @returns Promise resolving to all collected metrics
   */
  collectAll(): Promise<{
    page?: PageMetrics;
    navigation?: NavigationMetrics;
    resources: ResourceMetrics[];
    interactions: InteractionMetrics[];
    tabs: TabMetrics[];
  }>;

  /**
   * Start automatic metric collection at specified interval
   *
   * @param intervalMs - Collection interval in milliseconds
   * @param callback - Called with collected metrics
   */
  startAutoCollect(intervalMs: number, callback: (metrics: unknown) => void | Promise<void>): void;

  /**
   * Stop automatic metric collection
   */
  stopAutoCollect(): void;
}
