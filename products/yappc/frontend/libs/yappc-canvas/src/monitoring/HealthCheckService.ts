/**
 * Health Check System
 * Provides comprehensive system health monitoring and endpoints
 */

/**
 *
 */
export interface HealthStatus {
  status: 'healthy' | 'degraded' | 'unhealthy';
  timestamp: number;
  uptime: number;
  version: string;
  environment: string;
}

/**
 *
 */
export interface ComponentHealth {
  name: string;
  status: 'healthy' | 'degraded' | 'unhealthy';
  latency?: number;
  errorRate?: number;
  lastCheck: number;
  details?: Record<string, unknown>;
  dependencies?: ComponentHealth[];
}

/**
 *
 */
export interface HealthCheckResult extends HealthStatus {
  components: ComponentHealth[];
  metrics: {
    memory: {
      used: number;
      total: number;
      percentage: number;
    };
    performance: {
      fps: number;
      renderTime: number;
      elementCount: number;
    };
    storage: {
      localStorage: number;
      sessionStorage: number;
      indexedDB?: number;
    };
    network: {
      online: boolean;
      effectiveType?: string;
      downlink?: number;
    };
  };
  alerts: Array<{
    level: 'info' | 'warning' | 'error' | 'critical';
    message: string;
    component?: string;
    timestamp: number;
  }>;
}

/**
 *
 */
export class HealthCheckService {
  private startTime: number = Date.now();
  private healthChecks: Map<string, () => Promise<ComponentHealth>> = new Map();
  private cachedResult: HealthCheckResult | null = null;
  private cacheExpiry: number = 0;
  private isRunning: boolean = false;

  /**
   *
   */
  constructor() {
    this.registerDefaultHealthChecks();
  }

  /**
   * Register a custom health check
   */
  registerHealthCheck(name: string, checkFn: () => Promise<ComponentHealth>): void {
    this.healthChecks.set(name, checkFn);
    this.invalidateCache();
  }

  /**
   * Perform comprehensive health check
   */
  async performHealthCheck(useCache: boolean = true): Promise<HealthCheckResult> {
    const now = Date.now();
    
    if (useCache && this.cachedResult && now < this.cacheExpiry) {
      return this.cachedResult;
    }

    if (this.isRunning) {
      // Return cached result if check is already running
      return this.cachedResult || this.createEmptyResult();
    }

    this.isRunning = true;

    try {
      const components: ComponentHealth[] = [];
      const alerts: HealthCheckResult['alerts'] = [];

      // Run all registered health checks
      for (const [name, checkFn] of this.healthChecks) {
        try {
          const result = await Promise.race([
            checkFn(),
            this.timeoutPromise(5000, name) // 5 second timeout
          ]);
          components.push(result);

          // Generate alerts based on component health
          if (result.status === 'unhealthy') {
            alerts.push({
              level: 'error',
              message: `Component ${result.name} is unhealthy`,
              component: result.name,
              timestamp: now,
            });
          } else if (result.status === 'degraded') {
            alerts.push({
              level: 'warning',
              message: `Component ${result.name} is degraded`,
              component: result.name,
              timestamp: now,
            });
          }
        } catch (error) {
          components.push({
            name,
            status: 'unhealthy',
            lastCheck: now,
            details: { error: String(error) },
          });

          alerts.push({
            level: 'critical',
            message: `Health check failed for ${name}: ${error}`,
            component: name,
            timestamp: now,
          });
        }
      }

      // Calculate overall system status
      const overallStatus = this.calculateOverallStatus(components);
      
      // Collect system metrics
      const metrics = await this.collectSystemMetrics();

      const result: HealthCheckResult = {
        status: overallStatus,
        timestamp: now,
        uptime: now - this.startTime,
        version: this.getVersion(),
        environment: this.getEnvironment(),
        components,
        metrics,
        alerts,
      };

      // Cache result for 30 seconds
      this.cachedResult = result;
      this.cacheExpiry = now + 30000;

      return result;
    } finally {
      this.isRunning = false;
    }
  }

  /**
   * Get simple health status (lighter weight)
   */
  async getSimpleHealth(): Promise<HealthStatus> {
    const overallStatus = this.cachedResult?.status || 'healthy';
    
    return {
      status: overallStatus,
      timestamp: Date.now(),
      uptime: Date.now() - this.startTime,
      version: this.getVersion(),
      environment: this.getEnvironment(),
    };
  }

  /**
   * Check if system is ready to serve requests
   */
  async isReady(): Promise<boolean> {
    try {
      const health = await this.performHealthCheck();
      return health.status !== 'unhealthy';
    } catch {
      return false;
    }
  }

  /**
   * Check if system is alive (basic liveness check)
   */
  isAlive(): boolean {
    return true; // If this code is running, the system is alive
  }

  /**
   * Get health check endpoints for external monitoring
   */
  getEndpoints() {
    return {
      '/health': () => this.performHealthCheck(),
      '/health/live': () => ({ alive: this.isAlive() }),
      '/health/ready': async () => ({ ready: await this.isReady() }),
      '/health/simple': () => this.getSimpleHealth(),
    };
  }

  /**
   *
   */
  private registerDefaultHealthChecks(): void {
    // Canvas System Health
    this.registerHealthCheck('canvas', async () => {
      const startTime = Date.now();
      
      try {
        // Check if canvas can create elements
        const testElement = document.createElement('div');
        testElement.style.display = 'none';
        document.body.appendChild(testElement);
        document.body.removeChild(testElement);

        // Check React Flow availability
        const reactFlowAvailable = typeof window !== 'undefined' && 
          document.querySelector('[data-testid="react-flow-wrapper"], [data-testid="rf__wrapper"]') !== null;

        const latency = Date.now() - startTime;

        return {
          name: 'canvas',
          status: reactFlowAvailable ? 'healthy' : 'degraded',
          latency,
          lastCheck: Date.now(),
          details: {
            reactFlowAvailable,
            domManipulation: true,
          },
        };
      } catch (error) {
        return {
          name: 'canvas',
          status: 'unhealthy',
          lastCheck: Date.now(),
          details: { error: String(error) },
        };
      }
    });

    // Storage Health
    this.registerHealthCheck('storage', async () => {
      const startTime = Date.now();
      
      try {
        // Test localStorage
        const testKey = 'health_check_test';
        localStorage.setItem(testKey, 'test');
        const retrieved = localStorage.getItem(testKey);
        localStorage.removeItem(testKey);

        // Test sessionStorage
        sessionStorage.setItem(testKey, 'test');
        sessionStorage.removeItem(testKey);

        const latency = Date.now() - startTime;

        return {
          name: 'storage',
          status: retrieved === 'test' ? 'healthy' : 'unhealthy',
          latency,
          lastCheck: Date.now(),
          details: {
            localStorage: retrieved === 'test',
            sessionStorage: true,
          },
        };
      } catch (error) {
        return {
          name: 'storage',
          status: 'unhealthy',
          lastCheck: Date.now(),
          details: { error: String(error) },
        };
      }
    });

    // Network Health
    this.registerHealthCheck('network', async () => {
      const startTime = Date.now();
      
      try {
        const online = navigator.onLine;
        const connection = (navigator as unknown).connection;

        return {
          name: 'network',
          status: online ? 'healthy' : 'degraded',
          latency: Date.now() - startTime,
          lastCheck: Date.now(),
          details: {
            online,
            effectiveType: connection?.effectiveType,
            downlink: connection?.downlink,
          },
        };
      } catch (error) {
        return {
          name: 'network',
          status: 'unhealthy',
          lastCheck: Date.now(),
          details: { error: String(error) },
        };
      }
    });

    // Performance Health
    this.registerHealthCheck('performance', async () => {
      const startTime = Date.now();
      
      try {
        const memory = (performance as unknown).memory;
        const elements = document.querySelectorAll('.react-flow__node, .react-flow__edge');
        
        // Simple performance test
        const iterations = 1000;
        const perfStart = performance.now();
        for (let i = 0; i < iterations; i++) {
          Math.random();
        }
        const perfTime = performance.now() - perfStart;

        const status = perfTime < 10 ? 'healthy' : perfTime < 50 ? 'degraded' : 'unhealthy';

        return {
          name: 'performance',
          status,
          latency: Date.now() - startTime,
          lastCheck: Date.now(),
          details: {
            memoryUsed: memory?.usedJSHeapSize || 0,
            memoryTotal: memory?.totalJSHeapSize || 0,
            elementCount: elements.length,
            jsPerformance: perfTime,
          },
        };
      } catch (error) {
        return {
          name: 'performance',
          status: 'unhealthy',
          lastCheck: Date.now(),
          details: { error: String(error) },
        };
      }
    });
  }

  /**
   *
   */
  private calculateOverallStatus(components: ComponentHealth[]): HealthStatus['status'] {
    const unhealthy = components.filter(c => c.status === 'unhealthy').length;
    const degraded = components.filter(c => c.status === 'degraded').length;

    if (unhealthy > 0) return 'unhealthy';
    if (degraded > 0) return 'degraded';
    return 'healthy';
  }

  /**
   *
   */
  private async collectSystemMetrics(): Promise<HealthCheckResult['metrics']> {
    const memory = (performance as unknown).memory;
    const connection = (navigator as unknown).connection;
    
    // Storage metrics
    const storageMetrics = this.getStorageMetrics();
    
    // Performance metrics
    const elements = document.querySelectorAll('.react-flow__node, .react-flow__edge');
    
    return {
      memory: {
        used: memory?.usedJSHeapSize || 0,
        total: memory?.totalJSHeapSize || 0,
        percentage: memory ? (memory.usedJSHeapSize / memory.totalJSHeapSize) * 100 : 0,
      },
      performance: {
        fps: 60, // Mock - would be calculated from actual measurements
        renderTime: 0, // Mock - would be calculated from actual measurements
        elementCount: elements.length,
      },
      storage: storageMetrics,
      network: {
        online: navigator.onLine,
        effectiveType: connection?.effectiveType,
        downlink: connection?.downlink,
      },
    };
  }

  /**
   *
   */
  private getStorageMetrics() {
    try {
      let localStorageSize = 0;
      let sessionStorageSize = 0;

      // Calculate localStorage size
      for (const key in localStorage) {
        if (localStorage.hasOwnProperty(key)) {
          localStorageSize += localStorage[key].length + key.length;
        }
      }

      // Calculate sessionStorage size
      for (const key in sessionStorage) {
        if (sessionStorage.hasOwnProperty(key)) {
          sessionStorageSize += sessionStorage[key].length + key.length;
        }
      }

      return {
        localStorage: localStorageSize,
        sessionStorage: sessionStorageSize,
      };
    } catch (error) {
      return {
        localStorage: -1,
        sessionStorage: -1,
      };
    }
  }

  /**
   *
   */
  private timeoutPromise(ms: number, componentName: string): Promise<ComponentHealth> {
    return new Promise((_, reject) => {
      setTimeout(() => reject(new Error(`Health check timeout for ${componentName}`)), ms);
    });
  }

  /**
   *
   */
  private createEmptyResult(): HealthCheckResult {
    return {
      status: 'unhealthy',
      timestamp: Date.now(),
      uptime: Date.now() - this.startTime,
      version: this.getVersion(),
      environment: this.getEnvironment(),
      components: [],
      metrics: {
        memory: { used: 0, total: 0, percentage: 0 },
        performance: { fps: 0, renderTime: 0, elementCount: 0 },
        storage: { localStorage: 0, sessionStorage: 0 },
        network: { online: false },
      },
      alerts: [],
    };
  }

  /**
   *
   */
  private getVersion(): string {
    return process.env.npm_package_version || '1.0.0';
  }

  /**
   *
   */
  private getEnvironment(): string {
    return process.env.NODE_ENV || 'development';
  }

  /**
   *
   */
  private invalidateCache(): void {
    this.cachedResult = null;
    this.cacheExpiry = 0;
  }
}

// Singleton instance
export const healthCheckService = new HealthCheckService();