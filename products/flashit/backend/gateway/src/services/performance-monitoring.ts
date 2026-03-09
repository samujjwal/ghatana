/**
 * Performance Monitoring Service
 * 
 * Provides comprehensive performance monitoring, metrics collection,
 * and optimization recommendations for the Flashit platform
 * 
 * @doc.type service
 * @doc.purpose Performance monitoring and optimization
 * @doc.layer infrastructure
 * @doc.pattern Service
 */

import { systemLogger } from '../logger';

export interface PerformanceMetrics {
  api: {
    responseTime: {
      p50: number;
      p95: number;
      p99: number;
      average: number;
    };
    throughput: {
      requestsPerSecond: number;
      requestsPerMinute: number;
    };
    errorRate: {
      percentage: number;
      totalErrors: number;
      totalRequests: number;
    };
    statusCodes: Record<number, number>;
  };
  database: {
    queryTime: {
      p50: number;
      p95: number;
      p99: number;
      average: number;
    };
    connectionPool: {
      active: number;
      idle: number;
      total: number;
    };
    slowQueries: Array<{
      query: string;
      duration: number;
      timestamp: Date;
      params: any;
    }>;
  };
  ai: {
    agentResponseTime: {
      p50: number;
      p95: number;
      p99: number;
      average: number;
    };
    agentAvailability: {
      classification: number;
      embedding: number;
      reflection: number;
      transcription: number;
      nlp: number;
    };
    fallbackRate: {
      classification: number;
      embedding: number;
      reflection: number;
      transcription: number;
      nlp: number;
    };
  };
  memory: {
    heapUsed: number;
    heapTotal: number;
    external: number;
    rss: number;
  };
  cpu: {
    usage: number;
    loadAverage: number[];
  };
}

export interface PerformanceAlert {
  type: 'response_time' | 'error_rate' | 'memory' | 'cpu' | 'database' | 'ai_service';
  severity: 'low' | 'medium' | 'high' | 'critical';
  message: string;
  value: number;
  threshold: number;
  timestamp: Date;
  recommendations: string[];
}

export interface PerformanceBudget {
  api: {
    responseTime: {
      p50: number;
      p95: number;
      p99: number;
    };
    errorRate: number;
    throughput: number;
  };
  database: {
    queryTime: {
      p95: number;
    };
    connectionPool: {
      maxConnections: number;
    };
  };
  ai: {
    agentResponseTime: {
      p95: number;
    };
    agentAvailability: number;
  };
  memory: {
    maxHeapUsed: number;
  };
  cpu: {
    maxUsage: number;
  };
}

/**
 * Performance Monitoring Service
 */
export class PerformanceMonitoringService {
  private static instance: PerformanceMonitoringService;
  private metrics: PerformanceMetrics;
  private alerts: PerformanceAlert[] = [];
  private budgets: PerformanceBudget;
  private metricsHistory: Array<{
    timestamp: Date;
    metrics: PerformanceMetrics;
  }> = [];
  private alertCallbacks: Array<(alert: PerformanceAlert) => void> = [];

  private constructor() {
    this.metrics = this.initializeMetrics();
    this.budgets = this.initializeBudgets();
    this.startMonitoring();
  }

  static getInstance(): PerformanceMonitoringService {
    if (!PerformanceMonitoringService.instance) {
      PerformanceMonitoringService.instance = new PerformanceMonitoringService();
    }
    return PerformanceMonitoringService.instance;
  }

  private initializeMetrics(): PerformanceMetrics {
    return {
      api: {
        responseTime: { p50: 0, p95: 0, p99: 0, average: 0 },
        throughput: { requestsPerSecond: 0, requestsPerMinute: 0 },
        errorRate: { percentage: 0, totalErrors: 0, totalRequests: 0 },
        statusCodes: {},
      },
      database: {
        queryTime: { p50: 0, p95: 0, p99: 0, average: 0 },
        connectionPool: { active: 0, idle: 0, total: 0 },
        slowQueries: [],
      },
      ai: {
        agentResponseTime: { p50: 0, p95: 0, p99: 0, average: 0 },
        agentAvailability: {
          classification: 0,
          embedding: 0,
          reflection: 0,
          transcription: 0,
          nlp: 0,
        },
        fallbackRate: {
          classification: 0,
          embedding: 0,
          reflection: 0,
          transcription: 0,
          nlp: 0,
        },
      },
      memory: {
        heapUsed: 0,
        heapTotal: 0,
        external: 0,
        rss: 0,
      },
      cpu: {
        usage: 0,
        loadAverage: [],
      },
    };
  }

  private initializeBudgets(): PerformanceBudget {
    return {
      api: {
        responseTime: { p50: 200, p95: 500, p99: 1000 },
        errorRate: 0.01, // 1%
        throughput: 1000, // requests per second
      },
      database: {
        queryTime: { p95: 100 },
        connectionPool: { maxConnections: 20 },
      },
      ai: {
        agentResponseTime: { p95: 2000 },
        agentAvailability: 0.95, // 95%
      },
      memory: {
        maxHeapUsed: 512 * 1024 * 1024, // 512MB
      },
      cpu: {
        maxUsage: 0.8, // 80%
      },
    };
  }

  private startMonitoring(): void {
    // Collect system metrics every 30 seconds
    setInterval(() => {
      this.collectSystemMetrics();
    }, 30000);

    // Check for alerts every minute
    setInterval(() => {
      this.checkAlerts();
    }, 60000);

    // Clean old metrics history every hour
    setInterval(() => {
      this.cleanupMetricsHistory();
    }, 3600000);
  }

  /**
   * Record API request metrics
   */
  recordApiRequest(duration: number, statusCode: number, error?: Error): void {
    this.metrics.api.responseTime.average = 
      (this.metrics.api.responseTime.average * 0.9) + (duration * 0.1);
    
    // Update percentiles (simplified)
    if (duration > this.metrics.api.responseTime.p99) {
      this.metrics.api.responseTime.p99 = duration;
    }
    if (duration > this.metrics.api.responseTime.p95) {
      this.metrics.api.responseTime.p95 = duration;
    }
    if (duration > this.metrics.api.responseTime.p50) {
      this.metrics.api.responseTime.p50 = duration;
    }

    // Update status codes
    this.metrics.api.statusCodes[statusCode] = 
      (this.metrics.api.statusCodes[statusCode] || 0) + 1;

    // Update error rate
    this.metrics.api.errorRate.totalRequests++;
    if (error || statusCode >= 400) {
      this.metrics.api.errorRate.totalErrors++;
    }
    this.metrics.api.errorRate.percentage = 
      this.metrics.api.errorRate.totalErrors / this.metrics.api.errorRate.totalRequests;

    // Check for response time alert
    if (duration > this.budgets.api.responseTime.p99) {
      this.createAlert({
        type: 'response_time',
        severity: 'high',
        message: `API response time exceeded budget: ${duration}ms`,
        value: duration,
        threshold: this.budgets.api.responseTime.p99,
        timestamp: new Date(),
        recommendations: [
          'Check database query performance',
          'Optimize business logic',
          'Consider caching frequent requests',
        ],
      });
    }
  }

  /**
   * Record database query metrics
   */
  recordDatabaseQuery(duration: number, query: string, params?: any): void {
    this.metrics.database.queryTime.average = 
      (this.metrics.database.queryTime.average * 0.9) + (duration * 0.1);

    if (duration > this.metrics.database.queryTime.p99) {
      this.metrics.database.queryTime.p99 = duration;
    }
    if (duration > this.metrics.database.queryTime.p95) {
      this.metrics.database.queryTime.p95 = duration;
    }
    if (duration > this.metrics.database.queryTime.p50) {
      this.metrics.database.queryTime.p50 = duration;
    }

    // Track slow queries
    if (duration > this.budgets.database.queryTime.p95) {
      this.metrics.database.slowQueries.push({
        query,
        duration,
        timestamp: new Date(),
        params,
      });

      // Keep only last 100 slow queries
      if (this.metrics.database.slowQueries.length > 100) {
        this.metrics.database.slowQueries = this.metrics.database.slowQueries.slice(-100);
      }

      this.createAlert({
        type: 'database',
        severity: 'medium',
        message: `Slow database query detected: ${duration}ms`,
        value: duration,
        threshold: this.budgets.database.queryTime.p95,
        timestamp: new Date(),
        recommendations: [
          'Add database index',
          'Optimize query structure',
          'Consider query caching',
        ],
      });
    }
  }

  /**
   * Record AI agent metrics
   */
  recordAIAgentCall(agent: string, duration: number, success: boolean): void {
    const agentKey = agent as keyof typeof this.metrics.ai.agentResponseTime;
    
    if (this.metrics.ai.agentResponseTime[agentKey]) {
      this.metrics.ai.agentResponseTime[agentKey].average = 
        (this.metrics.ai.agentResponseTime[agentKey].average * 0.9) + (duration * 0.1);
      
      if (duration > this.metrics.ai.agentResponseTime[agentKey].p99) {
        this.metrics.ai.agentResponseTime[agentKey].p99 = duration;
      }
      if (duration > this.metrics.ai.agentResponseTime[agentKey].p95) {
        this.metrics.ai.agentResponseTime[agentKey].p95 = duration;
      }
      if (duration > this.metrics.ai.agentResponseTime[agentKey].p50) {
        this.metrics.ai.agentResponseTime[agentKey].p50 = duration;
      }
    }

    // Update availability and fallback rates
    const availabilityKey = agent as keyof typeof this.metrics.ai.agentAvailability;
    const fallbackKey = agent as keyof typeof this.metrics.ai.fallbackRate;

    if (success) {
      this.metrics.ai.agentAvailability[availabilityKey] = 
        (this.metrics.ai.agentAvailability[availabilityKey] * 0.95) + 0.05;
    } else {
      this.metrics.ai.fallbackRate[fallbackKey] = 
        (this.metrics.ai.fallbackRate[fallbackKey] * 0.95) + 0.05;
      this.metrics.ai.agentAvailability[availabilityKey] = 
        (this.metrics.ai.agentAvailability[availabilityKey] * 0.95);
    }

    // Check for AI performance alerts
    if (duration > this.budgets.ai.agentResponseTime.p95) {
      this.createAlert({
        type: 'ai_service',
        severity: 'medium',
        message: `AI agent ${agent} response time exceeded budget: ${duration}ms`,
        value: duration,
        threshold: this.budgets.ai.agentResponseTime.p95,
        timeout: new Date(),
        recommendations: [
          'Check Java agent service health',
          'Optimize AI model parameters',
          'Consider request batching',
        ],
      });
    }

    if (this.metrics.ai.agentAvailability[availabilityKey] < this.budgets.ai.agentAvailability) {
      this.createAlert({
        type: 'ai_service',
        severity: 'high',
        message: `AI agent ${agent} availability below threshold: ${this.metrics.ai.agentAvailability[availabilityKey]}`,
        value: this.metrics.ai.agentAvailability[availabilityKey],
        threshold: this.budgets.ai.agentAvailability,
        timestamp: new Date(),
        recommendations: [
          'Check Java agent service status',
          'Restart agent service if needed',
          'Monitor agent service logs',
        ],
      });
    }
  }

  /**
   * Collect system metrics
   */
  private collectSystemMetrics(): void {
    const memUsage = process.memoryUsage();
    
    this.metrics.memory = {
      heapUsed: memUsage.heapUsed,
      heapTotal: memUsage.heapTotal,
      external: memUsage.external,
      rss: memUsage.rss,
    };

    // CPU usage (simplified)
    const cpuUsage = process.cpuUsage();
    this.metrics.cpu.usage = cpuUsage.user / cpuUsage.system;

    // Check for memory alerts
    if (this.metrics.memory.heapUsed > this.budgets.memory.maxHeapUsed) {
      this.createAlert({
        type: 'memory',
        severity: 'high',
        message: `Memory usage exceeded budget: ${Math.round(this.metrics.memory.heapUsed / 1024 / 1024)}MB`,
        value: this.metrics.memory.heapUsed,
        threshold: this.budgets.memory.maxHeapUsed,
        timestamp: new Date(),
        recommendations: [
          'Check for memory leaks',
          'Optimize data structures',
          'Consider increasing memory limit',
        ],
      });
    }

    // Check for CPU alerts
    if (this.metrics.cpu.usage > this.budgets.cpu.maxUsage) {
      this.createAlert({
        type: 'cpu',
        severity: 'medium',
        message: `CPU usage exceeded budget: ${Math.round(this.metrics.cpu.usage * 100)}%`,
        value: this.metrics.cpu.usage,
        threshold: this.budgets.cpu.maxUsage,
        timestamp: new Date(),
        recommendations: [
          'Optimize CPU-intensive operations',
          'Consider load balancing',
          'Profile application performance',
        ],
      });
    }
  }

  /**
   * Check for performance alerts
   */
  private checkAlerts(): void {
    // Check error rate
    if (this.metrics.api.errorRate.percentage > this.budgets.api.errorRate) {
      this.createAlert({
        type: 'error_rate',
        severity: 'critical',
        message: `API error rate exceeded budget: ${Math.round(this.metrics.api.errorRate.percentage * 100)}%`,
        value: this.metrics.api.errorRate.percentage,
        threshold: this.budgets.api.errorRate,
        timestamp: new Date(),
        recommendations: [
          'Investigate recent code changes',
          'Check external service dependencies',
          'Review error logs',
        ],
      });
    }
  }

  /**
   * Create performance alert
   */
  private createAlert(alert: Omit<PerformanceAlert, 'timeout'>): void {
    this.alerts.push(alert as PerformanceAlert);
    
    // Keep only last 100 alerts
    if (this.alerts.length > 100) {
      this.alerts = this.alerts.slice(-100);
    }

    // Notify callbacks
    this.alertCallbacks.forEach(callback => {
      try {
        callback(alert as PerformanceAlert);
      } catch (error) {
        systemLogger.error('Error in performance alert callback', error);
      }
    });

    // Log alert
    systemLogger.warn(`Performance Alert [${alert.severity.toUpperCase()}]: ${alert.message}`, {
      type: alert.type,
      value: alert.value,
      threshold: alert.threshold,
      recommendations: alert.recommendations,
    });
  }

  /**
   * Get current metrics
   */
  getMetrics(): PerformanceMetrics {
    return { ...this.metrics };
  }

  /**
   * Get recent alerts
   */
  getAlerts(limit: number = 50): PerformanceAlert[] {
    return this.alerts.slice(-limit);
  }

  /**
   * Get performance recommendations
   */
  getRecommendations(): string[] {
    const recommendations: string[] = [];

    // API recommendations
    if (this.metrics.api.responseTime.p95 > this.budgets.api.responseTime.p95) {
      recommendations.push('Consider implementing API response caching');
    }

    // Database recommendations
    if (this.metrics.database.slowQueries.length > 10) {
      recommendations.push('Review and optimize slow database queries');
    }

    // AI service recommendations
    const avgAvailability = Object.values(this.metrics.ai.agentAvailability)
      .reduce((sum, val) => sum + val, 0) / Object.keys(this.metrics.ai.agentAvailability).length;
    
    if (avgAvailability < this.budgets.ai.agentAvailability) {
      recommendations.push('Improve AI agent service reliability');
    }

    // Memory recommendations
    if (this.metrics.memory.heapUsed > this.budgets.memory.maxHeapUsed * 0.8) {
      recommendations.push('Monitor memory usage and optimize if needed');
    }

    return recommendations;
  }

  /**
   * Get performance score (0-100)
   */
  getPerformanceScore(): number {
    let score = 100;

    // API performance (40% weight)
    const apiScore = Math.max(0, 100 - (this.metrics.api.responseTime.p95 / this.budgets.api.responseTime.p95 - 1) * 100);
    score = score * 0.4 + apiScore * 0.6;

    // Error rate (30% weight)
    const errorScore = Math.max(0, 100 - (this.metrics.api.errorRate.percentage / this.budgets.api.errorRate - 1) * 100);
    score = score * 0.7 + errorScore * 0.3;

    // AI service performance (20% weight)
    const avgAvailability = Object.values(this.metrics.ai.agentAvailability)
      .reduce((sum, val) => sum + val, 0) / Object.keys(this.metrics.ai.agentAvailability).length;
    const aiScore = avgAvailability / this.budgets.ai.agentAvailability * 100;
    score = score * 0.8 + aiScore * 0.2;

    // System resources (10% weight)
    const memoryScore = Math.max(0, 100 - (this.metrics.memory.heapUsed / this.budgets.memory.maxHeapUsed - 1) * 100);
    const cpuScore = Math.max(0, 100 - (this.metrics.cpu.usage / this.budgets.cpu.maxUsage - 1) * 100);
    const resourceScore = (memoryScore + cpuScore) / 2;
    score = score * 0.9 + resourceScore * 0.1;

    return Math.round(score);
  }

  /**
   * Register alert callback
   */
  onAlert(callback: (alert: PerformanceAlert) => void): void {
    this.alertCallbacks.push(callback);
  }

  /**
   * Remove alert callback
   */
  removeAlertCallback(callback: (alert: PerformanceAlert) => void): void {
    const index = this.alertCallbacks.indexOf(callback);
    if (index > -1) {
      this.alertCallbacks.splice(index, 1);
    }
  }

  /**
   * Clean up old metrics history
   */
  private cleanupMetricsHistory(): void {
    const oneHourAgo = new Date(Date.now() - 3600000);
    this.metricsHistory = this.metricsHistory.filter(
      entry => entry.timestamp > oneHourAgo
    );
  }

  /**
   * Save metrics snapshot
   */
  saveMetricsSnapshot(): void {
    this.metricsHistory.push({
      timestamp: new Date(),
      metrics: { ...this.metrics },
    });

    // Keep only last 24 hours of data
    const oneDayAgo = new Date(Date.now() - 86400000);
    this.metricsHistory = this.metricsHistory.filter(
      entry => entry.timestamp > oneDayAgo
    );
  }
}

/**
 * Global performance monitoring instance
 */
export const performanceMonitor = PerformanceMonitoringService.getInstance();

/**
 * Performance monitoring middleware for Fastify
 */
export const performanceMiddleware = async (
  request: any,
  reply: any
) => {
  const startTime = Date.now();
  
  reply.addHook('onSend', () => {
    const duration = Date.now() - startTime;
    performanceMonitor.recordApiRequest(duration, reply.statusCode, request.err);
  });
};

export default {
  performanceMonitor,
  performanceMiddleware,
};
