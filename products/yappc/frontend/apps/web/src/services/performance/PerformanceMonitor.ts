/**
 * Performance Monitor - Track canvas performance metrics
 */

/**
 *
 */
export interface PerformanceMetrics {
  fps: number;
  frameTime: number;
  renderTime: number;
  elementCount: number;
  visibleElements: number;
  memoryUsage?: number;
  timestamp: number;
}

/**
 *
 */
export interface PerformanceBudget {
  maxFrameTime: number; // ms
  minFPS: number;
  maxElements: number;
  maxRenderTime: number; // ms
}

/**
 *
 */
export class PerformanceMonitor {
  private metrics: PerformanceMetrics[] = [];
  private isMonitoring = false;
  private frameCount = 0;
  private lastTime = 0;
  private renderStartTime = 0;
  
  private budget: PerformanceBudget = {
    maxFrameTime: 16.67, // 60 FPS
    minFPS: 30,
    maxElements: 1000,
    maxRenderTime: 10,
  };

  private callbacks = new Set<(metrics: PerformanceMetrics) => void>();

  /**
   * Start performance monitoring
   */
  start(): void {
    if (this.isMonitoring) return;
    
    this.isMonitoring = true;
    this.lastTime = performance.now();
    this.frameCount = 0;
    
    this.measureFrame();
  }

  /**
   * Stop performance monitoring
   */
  stop(): void {
    this.isMonitoring = false;
  }

  /**
   * Mark start of render cycle
   */
  startRender(): void {
    this.renderStartTime = performance.now();
  }

  /**
   * Mark end of render cycle and record metrics
   */
  endRender(elementCount: number, visibleElements: number): void {
    if (!this.isMonitoring) return;

    const now = performance.now();
    const renderTime = now - this.renderStartTime;
    const frameTime = now - this.lastTime;
    const fps = 1000 / frameTime;

    const metrics: PerformanceMetrics = {
      fps: Math.round(fps * 10) / 10,
      frameTime: Math.round(frameTime * 100) / 100,
      renderTime: Math.round(renderTime * 100) / 100,
      elementCount,
      visibleElements,
      memoryUsage: this.getMemoryUsage(),
      timestamp: now,
    };

    this.metrics.push(metrics);
    
    // Keep only last 100 measurements
    if (this.metrics.length > 100) {
      this.metrics.shift();
    }

    // Notify callbacks
    this.callbacks.forEach(callback => callback(metrics));

    this.lastTime = now;
    this.frameCount++;
  }

  /**
   * Get current performance metrics
   */
  getCurrentMetrics(): PerformanceMetrics | null {
    return this.metrics[this.metrics.length - 1] || null;
  }

  /**
   * Get average metrics over last N frames
   */
  getAverageMetrics(frames: number = 30): Partial<PerformanceMetrics> | null {
    if (this.metrics.length === 0) return null;

    const recent = this.metrics.slice(-frames);
    const count = recent.length;

    return {
      fps: recent.reduce((sum, m) => sum + m.fps, 0) / count,
      frameTime: recent.reduce((sum, m) => sum + m.frameTime, 0) / count,
      renderTime: recent.reduce((sum, m) => sum + m.renderTime, 0) / count,
      elementCount: recent[recent.length - 1].elementCount,
      visibleElements: recent[recent.length - 1].visibleElements,
    };
  }

  /**
   * Check if performance is within budget
   */
  checkBudget(): { withinBudget: boolean; violations: string[] } {
    const current = this.getCurrentMetrics();
    if (!current) {
      return { withinBudget: true, violations: [] };
    }

    const violations: string[] = [];

    if (current.fps < this.budget.minFPS) {
      violations.push(`FPS too low: ${current.fps} < ${this.budget.minFPS}`);
    }

    if (current.frameTime > this.budget.maxFrameTime) {
      violations.push(`Frame time too high: ${current.frameTime}ms > ${this.budget.maxFrameTime}ms`);
    }

    if (current.elementCount > this.budget.maxElements) {
      violations.push(`Too many elements: ${current.elementCount} > ${this.budget.maxElements}`);
    }

    if (current.renderTime > this.budget.maxRenderTime) {
      violations.push(`Render time too high: ${current.renderTime}ms > ${this.budget.maxRenderTime}ms`);
    }

    return {
      withinBudget: violations.length === 0,
      violations,
    };
  }

  /**
   * Set performance budget
   */
  setBudget(budget: Partial<PerformanceBudget>): void {
    this.budget = { ...this.budget, ...budget };
  }

  /**
   * Get performance budget
   */
  getBudget(): PerformanceBudget {
    return { ...this.budget };
  }

  /**
   * Subscribe to performance updates
   */
  onMetrics(callback: (metrics: PerformanceMetrics) => void): () => void {
    this.callbacks.add(callback);
    return () => this.callbacks.delete(callback);
  }

  /**
   * Generate performance report
   */
  generateReport(): {
    summary: Partial<PerformanceMetrics>;
    budget: { withinBudget: boolean; violations: string[] };
    recommendations: string[];
  } {
    const summary = this.getAverageMetrics();
    const budget = this.checkBudget();
    const recommendations: string[] = [];

    if (summary) {
      if (summary.fps && summary.fps < 45) {
        recommendations.push('Consider reducing element count or enabling LOD rendering');
      }
      
      if (summary.renderTime && summary.renderTime > 8) {
        recommendations.push('Optimize rendering by batching updates or using virtualization');
      }
      
      if (summary.elementCount && summary.elementCount > 500) {
        recommendations.push('Use spatial indexing for better hit-testing performance');
      }
    }

    return {
      summary: summary || {},
      budget,
      recommendations,
    };
  }

  /**
   * Clear metrics history
   */
  clear(): void {
    this.metrics = [];
    this.frameCount = 0;
  }

  /**
   *
   */
  private measureFrame(): void {
    if (!this.isMonitoring) return;

    requestAnimationFrame(() => {
      this.measureFrame();
    });
  }

  /**
   *
   */
  private getMemoryUsage(): number | undefined {
    if ('memory' in performance) {
      const memory = (performance as unknown).memory;
      return Math.round(memory.usedJSHeapSize / 1024 / 1024 * 100) / 100; // MB
    }
    return undefined;
  }
}

// Singleton instance
export const performanceMonitor = new PerformanceMonitor();
