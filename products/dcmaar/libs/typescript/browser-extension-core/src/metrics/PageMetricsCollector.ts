/**
 * @fileoverview Page Metrics Collector
 *
 * Collects browser performance metrics using Performance API, Navigation Timing,
 * Resource Timing, and user interaction tracking.
 *
 * @module browser/metrics/PageMetricsCollector
 */

import type {
  MetricCollector,
  BatchMetricCollector,
  PageMetrics,
  NavigationMetrics,
  ResourceMetrics,
  InteractionMetrics,
  TabMetrics,
  WebVitalRating,
} from "./MetricCollector.interface";

type LargestContentfulPaintEntry = PerformanceEntry & {
  renderTime?: number;
  loadTime?: number;
  startTime: number;
  element?: Element | null;
};

type LayoutShiftEntry = PerformanceEntry & {
  value: number;
  hadRecentInput?: boolean;
};

type PerformanceEventTimingEntry = PerformanceEntry & {
  name: string;
  duration: number;
  startTime: number;
  processingStart?: number;
  processingEnd?: number;
};

type PerformanceLongTaskTimingEntry = PerformanceEntry & {
  duration: number;
};

/**
 * Page Metrics Collector implementation
 *
 * Collects various browser performance metrics from Performance APIs.
 * Must be run in a content script or page context.
 *
 * @example
 * ```typescript
 * const collector = new PageMetricsCollector();
 * const metrics = await collector.collectPageMetrics();
 * console.log('FCP:', metrics.fcp, 'LCP:', metrics.lcp);
 * ```
 */
export class PageMetricsCollector implements MetricCollector {
  private observers: PerformanceObserver[] = [];
  private observersInitialized = false;
  private lcpData?: { value: number; element?: string; timestamp: number };
  private clsValue = 0;
  private fidData?: { value: number; timestamp: number; type?: string };
  private inpData?: { value: number; timestamp: number; type?: string };
  private totalBlockingTime = 0;
  private longTaskCount = 0;
  private maxInteractionLatency = 0;

  constructor() {
    if (this.isInPageContext()) {
      this.initializePerformanceObservers();
    }
  }

  /**
   * Check if running in a context with window object (content script or page)
   */
  private isInPageContext(): boolean {
    try {
      return typeof window !== "undefined" && typeof document !== "undefined";
    } catch {
      return false;
    }
  }

  /**
   * Ensure performance observers are registered once in page context
   */
  private initializePerformanceObservers(): void {
    if (this.observersInitialized || !this.isInPageContext()) {
      return;
    }

    if (typeof PerformanceObserver === "undefined") {
      return;
    }

    this.observersInitialized = true;

    // Largest Contentful Paint
    try {
      const lcpObserver = new PerformanceObserver((entryList) => {
        const entries = entryList.getEntries();
        const lastEntry = entries[entries.length - 1] as
          | LargestContentfulPaintEntry
          | undefined;
        if (!lastEntry) {
          return;
        }

        const lcpValue =
          lastEntry.renderTime ?? lastEntry.loadTime ?? lastEntry.startTime;
        const timeOrigin = this.getTimeOrigin();
        this.lcpData = {
          value: lcpValue,
          element: this.describeElement(lastEntry.element ?? undefined),
          timestamp: timeOrigin + lcpValue,
        };
      });
      lcpObserver.observe({ type: "largest-contentful-paint", buffered: true });
      this.observers.push(lcpObserver);
    } catch (error) {
      this.debug("[PageMetricsCollector] Failed to observe LCP", error);
    }

    // Cumulative Layout Shift
    try {
      const clsObserver = new PerformanceObserver((entryList) => {
        for (const entry of entryList.getEntries() as LayoutShiftEntry[]) {
          if (entry.hadRecentInput) {
            continue;
          }
          this.clsValue += entry.value;
        }
      });
      clsObserver.observe({ type: "layout-shift", buffered: true });
      this.observers.push(clsObserver);
    } catch (error) {
      this.debug("[PageMetricsCollector] Failed to observe CLS", error);
    }

    // First Input Delay
    try {
      const fidObserver = new PerformanceObserver((entryList) => {
        const firstEntry = entryList.getEntries()[0] as
          | PerformanceEventTimingEntry
          | undefined;
        if (!firstEntry) {
          return;
        }
        const fidValue =
          firstEntry.processingStart !== undefined
            ? Math.max(firstEntry.processingStart - firstEntry.startTime, 0)
            : Math.max(firstEntry.duration, 0);
        const timeOrigin = this.getTimeOrigin();
        this.fidData = {
          value: fidValue,
          timestamp: timeOrigin + firstEntry.startTime,
          type: firstEntry.name,
        };
        fidObserver.disconnect();
      });
      fidObserver.observe({ type: "first-input", buffered: true });
      this.observers.push(fidObserver);
    } catch (error) {
      this.debug("[PageMetricsCollector] Failed to observe FID", error);
    }

    // Interaction to Next Paint (largest interaction)
    try {
      const eventObserver = new PerformanceObserver((entryList) => {
        for (const entry of entryList.getEntries() as PerformanceEventTimingEntry[]) {
          const interactionDuration =
            entry.duration ||
            (entry.processingEnd !== undefined
              ? Math.max(entry.processingEnd - entry.startTime, 0)
              : 0);

          const interactionType = entry.name;

          if (interactionDuration > this.maxInteractionLatency) {
            this.maxInteractionLatency = interactionDuration;
          }

          if (
            !this.inpData ||
            interactionDuration > (this.inpData.value ?? 0)
          ) {
            const timeOrigin = this.getTimeOrigin();
            this.inpData = {
              value: interactionDuration,
              timestamp: timeOrigin + entry.startTime,
              type: interactionType,
            };
          }
        }
      });
      // durationThreshold to filter out quick events (aligned with INP spec)
      eventObserver.observe({
        type: "event",
        buffered: true,
        durationThreshold: 16,
      } as PerformanceObserverInit);
      this.observers.push(eventObserver);
    } catch (error) {
      this.debug("[PageMetricsCollector] Failed to observe INP", error);
    }

    // Long tasks to compute Total Blocking Time
    try {
      const longTaskObserver = new PerformanceObserver((entryList) => {
        for (const entry of entryList.getEntries() as PerformanceLongTaskTimingEntry[]) {
          const blockingTime = entry.duration - 50;
          if (blockingTime > 0) {
            this.totalBlockingTime += blockingTime;
            this.longTaskCount += 1;
          }
        }
      });
      longTaskObserver.observe({ type: "longtask", buffered: true });
      this.observers.push(longTaskObserver);
    } catch (error) {
      this.debug("[PageMetricsCollector] Failed to observe long tasks", error);
    }
  }

  /**
   * Generates a lightweight selector description for an element
   */
  private describeElement(element?: Element | null): string | undefined {
    if (!element) {
      return undefined;
    }

    if (element.id) {
      return `#${element.id}`;
    }

    const tag = element.tagName?.toLowerCase();
    if (!tag) {
      return undefined;
    }

    const classTokens: string[] = element.classList
      ? Array.from(element.classList)
      : [];
    const classList = classTokens
      .slice(0, 3)
      .map((token) => token.replace(/\s+/g, "-"))
      .filter(Boolean);

    return classList.length ? `${tag}.${classList.join(".")}` : tag;
  }

  /**
   * Calculate rating for a web vital based on thresholds
   */
  private getWebVitalRating(
    value: number | undefined,
    thresholds: { good: number; poor: number },
    lowerIsBetter = true
  ): WebVitalRating | undefined {
    if (value === undefined) {
      return undefined;
    }

    const { good, poor } = thresholds;
    const v = value;

    if (lowerIsBetter) {
      if (v <= good) return "good";
      if (v > poor) return "poor";
      return "needs-improvement";
    }

    // Future-proof for metrics where higher is better
    if (v >= good) return "good";
    if (v < poor) return "poor";
    return "needs-improvement";
  }

  /**
   * Derive overall rating from individual ratings
   */
  private deriveOverallRating(
    ratings: Partial<Record<string, WebVitalRating>>
  ): WebVitalRating | undefined {
    const values = Object.values(ratings);
    if (!values.length) {
      return undefined;
    }
    if (values.includes("poor")) return "poor";
    if (values.includes("needs-improvement")) return "needs-improvement";
    return "good";
  }

  /**
   * Resolve performance time origin across browser implementations
   */
  private getTimeOrigin(): number {
    const perf = performance as Performance & {
      timeOrigin?: number;
      timing?: PerformanceTiming;
    };
    if (typeof perf.timeOrigin === "number") {
      return perf.timeOrigin;
    }
    if (perf.timing && typeof perf.timing.navigationStart === "number") {
      return perf.timing.navigationStart;
    }
    return Date.now() - performance.now();
  }

  /**
   * Collect page performance metrics
   */
  async collectPageMetrics(): Promise<PageMetrics> {
    if (!this.isInPageContext()) {
      return {
        url: "unknown",
        title: "background",
        timestamp: Date.now(),
      };
    }

    this.initializePerformanceObservers();

    const navigation = performance.getEntriesByType("navigation")[0] as
      | PerformanceNavigationTiming
      | undefined;
    const paint = performance.getEntriesByType("paint");
    const marks = performance.getEntriesByType("mark") as PerformanceMark[];

    const pageMetrics: PageMetrics = {
      url: window.location.href,
      title: document.title,
      timestamp: Date.now(),
      ttfb: navigation?.responseStart
        ? navigation.responseStart - navigation.requestStart
        : undefined,
      fcp: paint.find((entry) => entry.name === "first-contentful-paint")
        ?.startTime,
      lcp: undefined, // Would require PerformanceObserver
      cls: undefined, // Would require PerformanceObserver
      fid: undefined, // Would require PerformanceObserver for first input
      tbt: undefined, // Would require long task tracking
      domContentLoaded: navigation?.domContentLoadedEventEnd
        ? navigation.domContentLoadedEventEnd -
          navigation.domContentLoadedEventStart
        : undefined,
      loadTime: navigation?.loadEventEnd
        ? navigation.loadEventEnd - navigation.loadEventStart
        : undefined,
    };

    // Incorporate Web Vitals observations
    const details: {
      lcpElement?: string;
      lcpTimestamp?: number;
      inpInteractionType?: string;
      inpTimestamp?: number;
    } = {};

    if (this.lcpData?.value !== undefined) {
      pageMetrics.lcp = this.lcpData.value;
      if (this.lcpData.element) {
        details.lcpElement = this.lcpData.element;
      }
      details.lcpTimestamp = this.lcpData.timestamp;
    }

    pageMetrics.cls = this.clsValue
      ? Number(this.clsValue.toFixed(4))
      : pageMetrics.cls;

    if (this.fidData?.value !== undefined) {
      pageMetrics.fid = this.fidData.value;
    }

    if (this.inpData?.value !== undefined) {
      pageMetrics.inp = this.inpData.value;
      if (this.inpData.type) {
        details.inpInteractionType = this.inpData.type;
      }
      details.inpTimestamp = this.inpData.timestamp;
    }

    if (Object.keys(details).length) {
      pageMetrics.details = details;
    }

    if (this.totalBlockingTime > 0) {
      pageMetrics.tbt = Math.round(this.totalBlockingTime);
    }

    const diagnostics: {
      longTaskCount?: number;
      totalBlockingTime?: number;
      maxInteractionLatency?: number;
    } = {};
    if (this.longTaskCount > 0) {
      diagnostics.longTaskCount = this.longTaskCount;
    }
    if (this.totalBlockingTime > 0) {
      diagnostics.totalBlockingTime = Math.round(this.totalBlockingTime);
    }
    if (this.maxInteractionLatency > 0) {
      diagnostics.maxInteractionLatency = Math.round(
        this.maxInteractionLatency
      );
    }
    if (Object.keys(diagnostics).length) {
      pageMetrics.diagnostics = diagnostics;
    }

    if (marks.length) {
      const customMarks: Record<string, number> = {};
      for (const mark of marks) {
        customMarks[mark.name] = mark.startTime;
      }
      pageMetrics.customMarks = customMarks;
    }

    const ratings: Partial<
      Record<"lcp" | "cls" | "inp" | "fid", WebVitalRating>
    > = {};
    const lcpRating = this.getWebVitalRating(pageMetrics.lcp, {
      good: 2500,
      poor: 4000,
    });
    if (lcpRating) ratings.lcp = lcpRating;

    const clsRating = this.getWebVitalRating(pageMetrics.cls, {
      good: 0.1,
      poor: 0.25,
    });
    if (clsRating) ratings.cls = clsRating;

    const inpRating = this.getWebVitalRating(pageMetrics.inp, {
      good: 200,
      poor: 500,
    });
    if (inpRating) ratings.inp = inpRating;

    const fidRating = this.getWebVitalRating(pageMetrics.fid, {
      good: 100,
      poor: 300,
    });
    if (fidRating) ratings.fid = fidRating;

    if (Object.keys(ratings).length) {
      pageMetrics.ratings = ratings;
      pageMetrics.overallRating = this.deriveOverallRating(ratings);
    }

    // Fallback LCP from paint entries if observers unavailable
    if (pageMetrics.lcp === undefined) {
      const lcpEntries = performance.getEntriesByName(
        "largest-contentful-paint"
      );
      const lcpFallback = lcpEntries[lcpEntries.length - 1];
      if (lcpFallback) {
        pageMetrics.lcp = lcpFallback.startTime;
      }
    }

    return pageMetrics;
  }

  /**
   * Guarded debug logging for optional console availability
   */
  private debug(message: string, error: unknown): void {
    if (typeof console !== "undefined" && typeof console.debug === "function") {
      console.debug(message, error);
    }
  }

  /**
   * Collect navigation metrics
   */
  async collectNavigationMetrics(): Promise<NavigationMetrics> {
    if (!this.isInPageContext()) {
      return {
        type: "navigate",
        to: "unknown",
        timestamp: Date.now(),
      };
    }

    const navigation = performance.getEntriesByType("navigation")[0] as
      | PerformanceNavigationTiming
      | undefined;

    if (!navigation) {
      return {
        type: "navigate",
        to: window.location.href,
        timestamp: Date.now(),
      };
    }

    return {
      type: navigation.type as
        | "navigate"
        | "reload"
        | "back_forward"
        | "prerender",
      to: window.location.href,
      timestamp: Date.now(),
      duration: navigation.loadEventEnd - navigation.fetchStart,
    };
  }

  /**
   * Collect resource timing metrics
   */
  async collectResourceMetrics(): Promise<ResourceMetrics[]> {
    if (!this.isInPageContext()) {
      return [];
    }

    const resources = performance.getEntriesByType(
      "resource"
    ) as PerformanceResourceTiming[];

    return resources.map((resource) => ({
      url: resource.name,
      pageUrl: window.location.href,
      timestamp: Date.now(),
      type: this.getResourceType(resource),
      size: resource.transferSize,
      duration: resource.duration,
      startTime: resource.startTime,
      cached: resource.transferSize === 0 && resource.decodedBodySize > 0,
      protocol: resource.nextHopProtocol,
    }));
  }

  /**
   * Collect user interaction metrics
   */
  async collectUserInteractionMetrics(): Promise<InteractionMetrics[]> {
    if (!this.isInPageContext()) {
      return [];
    }

    return [
      {
        type: "scroll",
        timestamp: Date.now(),
        url: window.location.href,
        data: {
          scrollDepth: this.getScrollDepth(),
          timeOnPage: performance.now(),
        },
      },
    ];
  }

  /**
   * Collect tab-related metrics
   */
  async collectTabMetrics(): Promise<TabMetrics[]> {
    // Tab metrics require browser.tabs API (background script context)
    // Return empty array in content script context
    return [];
  }

  /**
   * Get resource type from PerformanceResourceTiming
   */
  private getResourceType(
    resource: PerformanceResourceTiming
  ): "script" | "stylesheet" | "image" | "font" | "fetch" | "xhr" | "other" {
    const initiatorType = resource.initiatorType;

    if (initiatorType === "script") return "script";
    if (initiatorType === "link" || initiatorType === "css")
      return "stylesheet";
    if (initiatorType === "img") return "image";
    if (initiatorType === "fetch") return "fetch";
    if (initiatorType === "xmlhttprequest") return "xhr";
    if (resource.name.match(/\.(woff|woff2|ttf|otf|eot)$/i)) return "font";

    return "other";
  }

  /**
   * Get current scroll depth
   */
  private getScrollDepth(): number {
    if (!this.isInPageContext()) {
      return 0;
    }

    const windowHeight = window.innerHeight;
    const documentHeight = document.documentElement.scrollHeight;
    const scrollTop = window.scrollY || document.documentElement.scrollTop;

    if (documentHeight <= windowHeight) {
      return 100;
    }

    return Math.round(((scrollTop + windowHeight) / documentHeight) * 100);
  }
}

/**
 * Batch Page Metrics Collector
 *
 * Collects all types of metrics in a single batch operation.
 *
 * @example
 * ```typescript
 * const collector = new BatchPageMetricsCollector();
 * const allMetrics = await collector.collectAll();
 * ```
 */
export class BatchPageMetricsCollector
  extends PageMetricsCollector
  implements BatchMetricCollector
{
  private autoCollectIntervalId?: ReturnType<typeof setInterval>;

  /**
   * Collect all metrics types in a batch
   */
  async collectAll(): Promise<{
    page?: PageMetrics;
    navigation?: NavigationMetrics;
    resources: ResourceMetrics[];
    interactions: InteractionMetrics[];
    tabs: TabMetrics[];
  }> {
    const [page, navigation, resources, interactions, tabs] = await Promise.all(
      [
        this.collectPageMetrics(),
        this.collectNavigationMetrics(),
        this.collectResourceMetrics(),
        this.collectUserInteractionMetrics(),
        this.collectTabMetrics(),
      ]
    );

    return {
      page,
      navigation,
      resources,
      interactions,
      tabs,
    };
  }

  /**
   * Start automatic metric collection
   */
  startAutoCollect(
    intervalMs: number,
    callback: (metrics: unknown) => void | Promise<void>
  ): void {
    this.stopAutoCollect(); // Clear any existing interval

    this.autoCollectIntervalId = setInterval(() => {
      void this.collectAll().then(callback);
    }, intervalMs);
  }

  /**
   * Stop automatic metric collection
   */
  stopAutoCollect(): void {
    if (this.autoCollectIntervalId) {
      clearInterval(this.autoCollectIntervalId);
      this.autoCollectIntervalId = undefined;
    }
  }
}
