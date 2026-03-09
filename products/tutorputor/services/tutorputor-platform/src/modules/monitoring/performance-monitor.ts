/**
 * @doc.type service
 * @doc.purpose Performance monitoring and metrics collection
 * @doc.layer product
 * @doc.pattern Monitoring Service
 */

import { EventEmitter } from 'events';

export interface PerformanceMetrics {
    timestamp: number;
    cpu: {
        usage: number;
        loadAverage: number[];
    };
    memory: {
        used: number;
        total: number;
        percentage: number;
        heapUsed: number;
        heapTotal: number;
    };
    responseTime: {
        avg: number;
        p50: number;
        p95: number;
        p99: number;
    };
    throughput: {
        requestsPerSecond: number;
        errorRate: number;
    };
    database: {
        connectionPool: {
            active: number;
            idle: number;
            total: number;
        };
        queryTime: {
            avg: number;
            slow: number;
        };
    };
    cache: {
        hitRate: number;
        missRate: number;
        size: number;
    };
}

export interface PerformanceAlert {
    id: string;
    type: 'cpu' | 'memory' | 'response_time' | 'throughput' | 'database' | 'cache';
    severity: 'low' | 'medium' | 'high' | 'critical';
    message: string;
    value: number;
    threshold: number;
    timestamp: number;
    resolved: boolean;
}

export interface PerformanceReport {
    period: string;
    metrics: PerformanceMetrics;
    alerts: PerformanceAlert[];
    trends: {
        cpu: number[];
        memory: number[];
        responseTime: number[];
        throughput: number[];
    };
}

/**
 * Performance Monitor Service
 * Collects and analyzes system performance metrics
 */
export class PerformanceMonitor extends EventEmitter {
    private metrics: PerformanceMetrics[] = [];
    private alerts: Map<string, PerformanceAlert> = new Map();
    private responseTimes: number[] = [];
    private cpuHistory: number[] = [];
    private memoryHistory: number[] = [];
    private throughputHistory: number[] = [];
    private monitoringInterval: NodeJS.Timeout | null = null;
    private readonly HISTORY_SIZE = 1000;
    private readonly ALERT_RETENTION = 24 * 60 * 60 * 1000; // 24 hours

    // Thresholds for alerts
    private readonly THRESHOLDS = {
        cpu: {
            warning: 70,
            critical: 90,
        },
        memory: {
            warning: 80,
            critical: 95,
        },
        responseTime: {
            warning: 1000, // 1 second
            critical: 5000, // 5 seconds
        },
        throughput: {
            warning: 100,
            critical: 50,
        },
        errorRate: {
            warning: 5,
            critical: 10,
        },
    };

    constructor() {
        super();
        this.startMonitoring();
    }

    /**
     * Start performance monitoring
     */
    startMonitoring(): void {
        this.monitoringInterval = setInterval(() => {
            void this.collectMetrics();
            this.checkAlerts();
            this.cleanupOldData();
        }, 5000); // Collect metrics every 5 seconds
    }

    /**
     * Stop performance monitoring
     */
    stopMonitoring(): void {
        if (this.monitoringInterval) {
            clearInterval(this.monitoringInterval);
            this.monitoringInterval = null;
        }
    }

    /**
     * Record response time
     */
    recordResponseTime(duration: number): void {
        this.responseTimes.push(duration);
        if (this.responseTimes.length > this.HISTORY_SIZE) {
            this.responseTimes.shift();
        }
    }

    /**
     * Record request
     */
    recordRequest(success: boolean): void {
        const now = Date.now();
        const recentRequests = this.throughputHistory.filter(
            time => now - time < 60000 // Last minute
        );

        this.throughputHistory.push(now);

        // Keep only last minute of throughput data
        if (this.throughputHistory.length > this.HISTORY_SIZE) {
            this.throughputHistory = this.throughputHistory.slice(-this.HISTORY_SIZE);
        }
    }

    /**
     * Get current metrics
     */
    async getCurrentMetrics(): Promise<PerformanceMetrics> {
        const metrics = await this.collectSystemMetrics();
        metrics.responseTime = this.calculateResponseTimeMetrics();
        metrics.throughput = this.calculateThroughputMetrics();

        return metrics;
    }

    /**
     * Get performance report
     */
    async getReport(period: 'hour' | 'day' | 'week' = 'hour'): Promise<PerformanceReport> {
        const now = Date.now();
        const periodMs = this.getPeriodMs(period);
        const cutoffTime = now - periodMs;

        const recentMetrics = this.metrics.filter(m => m.timestamp >= cutoffTime);

        if (recentMetrics.length === 0) {
            throw new Error(`No metrics available for ${period} period`);
        }

        const latestMetrics = recentMetrics[recentMetrics.length - 1];
        if (!latestMetrics) {
            throw new Error(`No metrics available for ${period} period`);
        }
        const activeAlerts = Array.from(this.alerts.values()).filter(
            alert => !alert.resolved && alert.timestamp >= cutoffTime
        );

        return {
            period,
            metrics: latestMetrics,
            alerts: activeAlerts,
            trends: {
                cpu: this.calculateTrend(recentMetrics, m => m.cpu.usage),
                memory: this.calculateTrend(recentMetrics, m => m.memory.percentage),
                responseTime: this.calculateTrend(recentMetrics, m => m.responseTime.avg),
                throughput: this.calculateTrend(recentMetrics, m => m.throughput.requestsPerSecond),
            },
        };
    }

    /**
     * Get active alerts
     */
    getActiveAlerts(): PerformanceAlert[] {
        return Array.from(this.alerts.values()).filter(alert => !alert.resolved);
    }

    /**
     * Resolve alert
     */
    resolveAlert(alertId: string): void {
        const alert = this.alerts.get(alertId);
        if (alert) {
            alert.resolved = true;
            this.emit('alertResolved', alert);
        }
    }

    /**
     * Create custom alert
     */
    createAlert(
        type: PerformanceAlert['type'],
        severity: PerformanceAlert['severity'],
        message: string,
        value: number,
        threshold: number
    ): PerformanceAlert {
        const alert: PerformanceAlert = {
            id: `alert_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            type,
            severity,
            message,
            value,
            threshold,
            timestamp: Date.now(),
            resolved: false,
        };

        this.alerts.set(alert.id, alert);
        this.emit('alert', alert);

        return alert;
    }

    /**
     * Collect system metrics
     */
    private async collectSystemMetrics(): Promise<PerformanceMetrics> {
        const timestamp = Date.now();

        // CPU metrics
        const cpuUsage = await this.getCpuUsage();
        const loadAverage = await this.getLoadAverage();

        // Memory metrics
        const memoryUsage = process.memoryUsage();
        const memoryPercentage = (memoryUsage.heapUsed / memoryUsage.heapTotal) * 100;

        // Database metrics
        const dbMetrics = await this.getDatabaseMetrics();

        // Cache metrics
        const cacheMetrics = await this.getCacheMetrics();

        const metrics: PerformanceMetrics = {
            timestamp,
            cpu: {
                usage: cpuUsage,
                loadAverage,
            },
            memory: {
                used: memoryUsage.heapUsed,
                total: memoryUsage.heapTotal,
                percentage: memoryPercentage,
                heapUsed: memoryUsage.heapUsed,
                heapTotal: memoryUsage.heapTotal,
            },
            responseTime: this.calculateResponseTimeMetrics(),
            throughput: this.calculateThroughputMetrics(),
            database: dbMetrics,
            cache: cacheMetrics,
        };

        this.metrics.push(metrics);
        this.cpuHistory.push(cpuUsage);
        this.memoryHistory.push(memoryPercentage);

        // Keep history size limited
        if (this.metrics.length > this.HISTORY_SIZE) {
            this.metrics.shift();
        }
        if (this.cpuHistory.length > this.HISTORY_SIZE) {
            this.cpuHistory.shift();
        }
        if (this.memoryHistory.length > this.HISTORY_SIZE) {
            this.memoryHistory.shift();
        }

        return metrics;
    }

    /**
     * Calculate response time metrics
     */
    private calculateResponseTimeMetrics(): PerformanceMetrics['responseTime'] {
        if (this.responseTimes.length === 0) {
            return { avg: 0, p50: 0, p95: 0, p99: 0 };
        }

        const sorted = [...this.responseTimes].sort((a, b) => a - b);
        const avg = sorted.reduce((sum, time) => sum + time, 0) / sorted.length;

        return {
            avg,
            p50: this.getPercentile(sorted, 50),
            p95: this.getPercentile(sorted, 95),
            p99: this.getPercentile(sorted, 99),
        };
    }

    /**
     * Calculate throughput metrics
     */
    private calculateThroughputMetrics(): PerformanceMetrics['throughput'] {
        const now = Date.now();
        const recentRequests = this.throughputHistory.filter(
            time => now - time < 60000 // Last minute
        );

        const requestsPerSecond = recentRequests.length / 60;

        // Calculate error rate (would need error tracking implementation)
        const errorRate = 0; // Placeholder - would be calculated from error logs

        return {
            requestsPerSecond,
            errorRate,
        };
    }

    /**
     * Get CPU usage
     */
    private async getCpuUsage(): Promise<number> {
        // In production, this would use system monitoring libraries
        // For now, return a mock value
        return Math.random() * 100;
    }

    /**
     * Get load average
     */
    private async getLoadAverage(): Promise<number[]> {
        // In production, this would use OS-specific APIs
        return [0.5, 0.3, 0.2]; // 1min, 5min, 15min averages
    }

    /**
     * Get database metrics
     */
    private async getDatabaseMetrics(): Promise<PerformanceMetrics['database']> {
        // In production, this would query the actual database connection pool
        return {
            connectionPool: {
                active: 5,
                idle: 10,
                total: 15,
            },
            queryTime: {
                avg: 50,
                slow: 2,
            },
        };
    }

    /**
     * Get cache metrics
     */
    private async getCacheMetrics(): Promise<PerformanceMetrics['cache']> {
        // In production, this would query Redis or other cache systems
        return {
            hitRate: 85,
            missRate: 15,
            size: 1000000, // bytes
        };
    }

    /**
     * Check for performance alerts
     */
    private checkAlerts(): void {
        const latestMetrics = this.metrics[this.metrics.length - 1];
        if (!latestMetrics) return;

        // CPU alerts
        if (latestMetrics.cpu.usage >= this.THRESHOLDS.cpu.critical) {
            this.createAlert(
                'cpu',
                'critical',
                `CPU usage critically high: ${latestMetrics.cpu.usage.toFixed(1)}%`,
                latestMetrics.cpu.usage,
                this.THRESHOLDS.cpu.critical
            );
        } else if (latestMetrics.cpu.usage >= this.THRESHOLDS.cpu.warning) {
            this.createAlert(
                'cpu',
                'medium',
                `CPU usage elevated: ${latestMetrics.cpu.usage.toFixed(1)}%`,
                latestMetrics.cpu.usage,
                this.THRESHOLDS.cpu.warning
            );
        }

        // Memory alerts
        if (latestMetrics.memory.percentage >= this.THRESHOLDS.memory.critical) {
            this.createAlert(
                'memory',
                'critical',
                `Memory usage critically high: ${latestMetrics.memory.percentage.toFixed(1)}%`,
                latestMetrics.memory.percentage,
                this.THRESHOLDS.memory.critical
            );
        } else if (latestMetrics.memory.percentage >= this.THRESHOLDS.memory.warning) {
            this.createAlert(
                'memory',
                'medium',
                `Memory usage elevated: ${latestMetrics.memory.percentage.toFixed(1)}%`,
                latestMetrics.memory.percentage,
                this.THRESHOLDS.memory.warning
            );
        }

        // Response time alerts
        if (latestMetrics.responseTime.p95 >= this.THRESHOLDS.responseTime.critical) {
            this.createAlert(
                'response_time',
                'critical',
                `95th percentile response time critically slow: ${latestMetrics.responseTime.p95}ms`,
                latestMetrics.responseTime.p95,
                this.THRESHOLDS.responseTime.critical
            );
        } else if (latestMetrics.responseTime.p95 >= this.THRESHOLDS.responseTime.warning) {
            this.createAlert(
                'response_time',
                'medium',
                `95th percentile response time slow: ${latestMetrics.responseTime.p95}ms`,
                latestMetrics.responseTime.p95,
                this.THRESHOLDS.responseTime.warning
            );
        }

        // Throughput alerts
        if (latestMetrics.throughput.requestsPerSecond <= this.THRESHOLDS.throughput.critical) {
            this.createAlert(
                'throughput',
                'critical',
                `Throughput critically low: ${latestMetrics.throughput.requestsPerSecond} req/s`,
                latestMetrics.throughput.requestsPerSecond,
                this.THRESHOLDS.throughput.critical
            );
        } else if (latestMetrics.throughput.requestsPerSecond <= this.THRESHOLDS.throughput.warning) {
            this.createAlert(
                'throughput',
                'medium',
                `Throughput low: ${latestMetrics.throughput.requestsPerSecond} req/s`,
                latestMetrics.throughput.requestsPerSecond,
                this.THRESHOLDS.throughput.warning
            );
        }
    }

    /**
     * Clean up old data
     */
    private cleanupOldData(): void {
        const now = Date.now();
        const cutoffTime = now - this.ALERT_RETENTION;

        // Clean old alerts
        for (const [id, alert] of this.alerts) {
            if (alert.timestamp < cutoffTime) {
                this.alerts.delete(id);
            }
        }
    }

    /**
     * Get percentile value
     */
    private getPercentile(sortedArray: number[], percentile: number): number {
        if (sortedArray.length === 0) return 0;
        const index = Math.ceil((percentile / 100) * sortedArray.length) - 1;
        return sortedArray[Math.max(0, index)];
    }

    /**
     * Calculate trend from metrics
     */
    private calculateTrend(
        metrics: PerformanceMetrics[],
        extractor: (m: PerformanceMetrics) => number
    ): number[] {
        return metrics.map(extractor);
    }

    /**
     * Get period in milliseconds
     */
    private getPeriodMs(period: string): number {
        switch (period) {
            case 'hour':
                return 60 * 60 * 1000;
            case 'day':
                return 24 * 60 * 60 * 1000;
            case 'week':
                return 7 * 24 * 60 * 60 * 1000;
            default:
                return 60 * 60 * 1000; // Default to hour
        }
    }

    /**
     * Cleanup resources
     */
    destroy(): void {
        this.stopMonitoring();
        this.metrics = [];
        this.alerts.clear();
        this.responseTimes = [];
        this.cpuHistory = [];
        this.memoryHistory = [];
        this.throughputHistory = [];
        this.removeAllListeners();
    }
}

/**
 * Singleton instance
 */
export const performanceMonitor = new PerformanceMonitor();
