/**
 * Canvas Performance Monitor
 * 
 * Utility for measuring and tracking performance metrics of canvas operations.
 * Used to identify bottlenecks and ensure smooth UX with large canvases.
 * 
 * @doc.type service
 * @doc.purpose Performance monitoring and benchmarking
 * @doc.layer product
 * @doc.pattern Singleton/Service
 */

export interface PerformanceStats {
    count: number;
    avg: number;
    min: number;
    max: number;
    p95: number;
    last: number;
}

export class CanvasPerformanceMonitor {
    private static instance: CanvasPerformanceMonitor;
    private metrics: Map<string, number[]> = new Map();
    private enabled: boolean = true;

    private constructor() { }

    public static getInstance(): CanvasPerformanceMonitor {
        if (!CanvasPerformanceMonitor.instance) {
            CanvasPerformanceMonitor.instance = new CanvasPerformanceMonitor();
        }
        return CanvasPerformanceMonitor.instance;
    }

    /**
     * Enable or disable monitoring
     */
    public setEnabled(enabled: boolean): void {
        this.enabled = enabled;
    }

    /**
     * Measure execution time of a synchronous function
     */
    public measure<T>(operation: string, fn: () => T): T {
        if (!this.enabled) return fn();

        const start = performance.now();
        try {
            return fn();
        } finally {
            const duration = performance.now() - start;
            this.record(operation, duration);
        }
    }

    /**
     * Measure execution time of an asynchronous function
     */
    public async measureAsync<T>(operation: string, fn: () => Promise<T>): Promise<T> {
        if (!this.enabled) return fn();

        const start = performance.now();
        try {
            return await fn();
        } finally {
            const duration = performance.now() - start;
            this.record(operation, duration);
        }
    }

    /**
     * Record a metric manually
     */
    public record(operation: string, duration: number): void {
        if (!this.metrics.has(operation)) {
            this.metrics.set(operation, []);
        }
        this.metrics.get(operation)!.push(duration);

        // Keep only last 1000 samples to prevent memory leaks
        const samples = this.metrics.get(operation)!;
        if (samples.length > 1000) {
            samples.shift();
        }
    }

    /**
     * Get statistics for an operation
     */
    public getStats(operation: string): PerformanceStats | null {
        const times = this.metrics.get(operation) || [];
        if (times.length === 0) return null;

        const sorted = [...times].sort((a, b) => a - b);
        const sum = sorted.reduce((a, b) => a + b, 0);

        return {
            count: times.length,
            avg: sum / times.length,
            min: sorted[0],
            max: sorted[sorted.length - 1],
            p95: this.percentile(sorted, 95),
            last: times[times.length - 1],
        };
    }

    /**
     * Get all statistics
     */
    public getAllStats(): Record<string, PerformanceStats> {
        const result: Record<string, PerformanceStats> = {};
        for (const operation of this.metrics.keys()) {
            const stats = this.getStats(operation);
            if (stats) {
                result[operation] = stats;
            }
        }
        return result;
    }

    /**
     * Clear all metrics
     */
    public clear(): void {
        this.metrics.clear();
    }

    private percentile(sortedArr: number[], p: number): number {
        if (sortedArr.length === 0) return 0;
        const index = Math.ceil((p / 100) * sortedArr.length) - 1;
        return sortedArr[Math.max(0, Math.min(index, sortedArr.length - 1))];
    }
}

export const performanceMonitor = CanvasPerformanceMonitor.getInstance();
