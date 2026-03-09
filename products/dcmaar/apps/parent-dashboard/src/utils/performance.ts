/**
 * Performance monitoring utilities
 * Tracks key metrics like bundle size, load time, and component render performance
 */

export interface PerformanceMetrics {
  // Load metrics
  timeToInteractive: number;
  firstContentfulPaint: number;
  largestContentfulPaint: number;
  
  // Bundle metrics
  totalJSSize: number;
  totalCSSSize: number;
  chunksCount: number;
  
  // Component metrics
  componentRenderTime: Map<string, number>;
}

class PerformanceMonitor {
  private metrics: Partial<PerformanceMetrics> = {
    componentRenderTime: new Map()
  };
  
  private observers: PerformanceObserver[] = [];

  constructor() {
    if (typeof window !== 'undefined' && 'PerformanceObserver' in window) {
      this.initializeObservers();
    }
  }

  private initializeObservers() {
    // Observe navigation timing
    if (PerformanceObserver.supportedEntryTypes?.includes('navigation')) {
      const navigationObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          const navEntry = entry as PerformanceNavigationTiming;
          this.metrics.timeToInteractive = navEntry.domInteractive - navEntry.fetchStart;
        }
      });
      navigationObserver.observe({ type: 'navigation', buffered: true });
      this.observers.push(navigationObserver);
    }

    // Observe paint timing
    if (PerformanceObserver.supportedEntryTypes?.includes('paint')) {
      const paintObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (entry.name === 'first-contentful-paint') {
            this.metrics.firstContentfulPaint = entry.startTime;
          }
        }
      });
      paintObserver.observe({ type: 'paint', buffered: true });
      this.observers.push(paintObserver);
    }

    // Observe largest contentful paint
    if (PerformanceObserver.supportedEntryTypes?.includes('largest-contentful-paint')) {
      const lcpObserver = new PerformanceObserver((list) => {
        const entries = list.getEntries();
        const lastEntry = entries[entries.length - 1];
        this.metrics.largestContentfulPaint = lastEntry.startTime;
      });
      lcpObserver.observe({ type: 'largest-contentful-paint', buffered: true });
      this.observers.push(lcpObserver);
    }

    // Observe resource timing for bundle analysis
    if (PerformanceObserver.supportedEntryTypes?.includes('resource')) {
      const resourceObserver = new PerformanceObserver((list) => {
        let totalJS = 0;
        let totalCSS = 0;
        let chunks = 0;

        for (const entry of list.getEntries()) {
          const resourceEntry = entry as PerformanceResourceTiming;
          const name = resourceEntry.name;

          if (name.endsWith('.js')) {
            totalJS += resourceEntry.encodedBodySize || 0;
            chunks++;
          } else if (name.endsWith('.css')) {
            totalCSS += resourceEntry.encodedBodySize || 0;
          }
        }

        this.metrics.totalJSSize = totalJS;
        this.metrics.totalCSSSize = totalCSS;
        this.metrics.chunksCount = chunks;
      });
      resourceObserver.observe({ type: 'resource', buffered: true });
      this.observers.push(resourceObserver);
    }
  }

  /**
   * Measure component render time
   */
  measureComponent(componentName: string, fn: () => void): void {
    const startTime = performance.now();
    fn();
    const endTime = performance.now();
    
    if (this.metrics.componentRenderTime) {
      this.metrics.componentRenderTime.set(componentName, endTime - startTime);
    }
  }

  /**
   * Get all collected metrics
   */
  getMetrics(): Partial<PerformanceMetrics> {
    return { ...this.metrics };
  }

  /**
   * Get a specific metric
   */
  getMetric(key: keyof PerformanceMetrics): unknown {
    return this.metrics[key];
  }

  /**
   * Log metrics to console (development only)
   */
  logMetrics(): void {
    if (import.meta.env.DEV) {
      console.group('⚡ Performance Metrics');
      console.log('Time to Interactive:', this.metrics.timeToInteractive?.toFixed(2), 'ms');
      console.log('First Contentful Paint:', this.metrics.firstContentfulPaint?.toFixed(2), 'ms');
      console.log('Largest Contentful Paint:', this.metrics.largestContentfulPaint?.toFixed(2), 'ms');
      console.log('Total JS Size:', (this.metrics.totalJSSize || 0) / 1024, 'KB');
      console.log('Total CSS Size:', (this.metrics.totalCSSSize || 0) / 1024, 'KB');
      console.log('JS Chunks:', this.metrics.chunksCount);
      
      if (this.metrics.componentRenderTime && this.metrics.componentRenderTime.size > 0) {
        console.log('Component Render Times:');
        this.metrics.componentRenderTime.forEach((time, component) => {
          console.log(`  ${component}:`, time.toFixed(2), 'ms');
        });
      }
      console.groupEnd();
    }
  }

  /**
   * Check if metrics meet performance budgets
   */
  checkBudget(budgets: Partial<PerformanceMetrics>): { passed: boolean; violations: string[] } {
    const violations: string[] = [];

    if (budgets.timeToInteractive && this.metrics.timeToInteractive && 
        this.metrics.timeToInteractive > budgets.timeToInteractive) {
      violations.push(`Time to Interactive: ${this.metrics.timeToInteractive.toFixed(2)}ms exceeds budget of ${budgets.timeToInteractive}ms`);
    }

    if (budgets.totalJSSize && this.metrics.totalJSSize && 
        this.metrics.totalJSSize > budgets.totalJSSize) {
      violations.push(`Total JS Size: ${(this.metrics.totalJSSize / 1024).toFixed(2)}KB exceeds budget of ${(budgets.totalJSSize / 1024).toFixed(2)}KB`);
    }

    if (budgets.largestContentfulPaint && this.metrics.largestContentfulPaint && 
        this.metrics.largestContentfulPaint > budgets.largestContentfulPaint) {
      violations.push(`Largest Contentful Paint: ${this.metrics.largestContentfulPaint.toFixed(2)}ms exceeds budget of ${budgets.largestContentfulPaint}ms`);
    }

    return {
      passed: violations.length === 0,
      violations
    };
  }

  /**
   * Clean up observers
   */
  disconnect(): void {
    this.observers.forEach(observer => observer.disconnect());
    this.observers = [];
  }
}

// Export singleton instance
export const performanceMonitor = new PerformanceMonitor();

// Export for testing
export { PerformanceMonitor };
