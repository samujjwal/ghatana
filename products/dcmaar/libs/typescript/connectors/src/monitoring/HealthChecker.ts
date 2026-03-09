import { EventEmitter } from 'events';

/**
 * @fileoverview Health monitoring with async checks and aggregated status.
 *
 * Coordinates periodic health checks, applies per-check configuration
 * (interval, timeout, criticality), emits lifecycle events, and produces
 * consolidated system health snapshots.
 */

/** Overall health status levels. */
export type HealthStatus = 'healthy' | 'degraded' | 'unhealthy';

/** Configuration for registering a health check. */
export interface HealthCheck {
  /** Unique health check name. */
  name: string;
  /** Async function that returns `HealthCheckResult`. */
  check: () => Promise<HealthCheckResult>;
  /** Optional execution interval in ms (default 30s). */
  interval?: number;
  /** Optional timeout in ms (default 5s). */
  timeout?: number;
  /** If true, unhealthy result marks system unhealthy. */
  critical?: boolean;
}

/** Result payload produced by a health check execution. */
export interface HealthCheckResult {
  status: HealthStatus;
  message?: string;
  details?: Record<string, any>;
  timestamp: number;
  duration: number;
}

/** Aggregated system health snapshot. */
export interface SystemHealth {
  status: HealthStatus;
  checks: Record<string, HealthCheckResult>;
  timestamp: number;
}

/**
 * Health checker orchestrating registered health checks.
 */
export class HealthChecker extends EventEmitter {
  private checks: Map<string, HealthCheck> = new Map();
  private results: Map<string, HealthCheckResult> = new Map();
  private intervals: Map<string, NodeJS.Timeout> = new Map();
  private isRunning: boolean = false;

  /**
   * Registers a new health check and schedules if running.
   *
   * @param {HealthCheck} check - Check configuration
   * @throws {Error} If duplicate name registered
   * @fires HealthChecker#checkRegistered
   */
  registerCheck(check: HealthCheck): void {
    if (this.checks.has(check.name)) {
      throw new Error(`Health check '${check.name}' already registered`);
    }

    this.checks.set(check.name, {
      interval: 30000, // Default 30 seconds
      timeout: 5000,   // Default 5 seconds
      critical: false,
      ...check,
    });

    // If already running, start this check immediately
    if (this.isRunning) {
      this._startCheck(check.name);
    }

    this.emit('checkRegistered', { name: check.name });
  }

  /**
   * Unregisters a health check and clears state.
   *
   * @param {string} name - Check name
   * @returns {boolean} True if removed
   * @fires HealthChecker#checkUnregistered
   */
  unregisterCheck(name: string): boolean {
    if (!this.checks.has(name)) {
      return false;
    }

    this._stopCheck(name);
    this.checks.delete(name);
    this.results.delete(name);

    this.emit('checkUnregistered', { name });
    return true;
  }

  /**
   * Starts scheduling for all registered checks.
   *
   * @fires HealthChecker#started
   */
  start(): void {
    if (this.isRunning) {
      return;
    }

    this.isRunning = true;

    for (const name of this.checks.keys()) {
      this._startCheck(name);
    }

    this.emit('started');
  }

  /**
   * Stops all scheduled checks and timers.
   *
   * @fires HealthChecker#stopped
   */
  stop(): void {
    if (!this.isRunning) {
      return;
    }

    this.isRunning = false;

    for (const name of this.checks.keys()) {
      this._stopCheck(name);
    }

    this.emit('stopped');
  }

  /**
   * Executes specific check on demand.
   *
   * @param {string} name - Check name
   * @returns {Promise<HealthCheckResult>}
   * @throws {Error} If check not found
   */
  async runCheck(name: string): Promise<HealthCheckResult> {
    const check = this.checks.get(name);
    if (!check) {
      throw new Error(`Health check '${name}' not found`);
    }

    return this._executeCheck(name, check);
  }

  /**
   * Executes all checks immediately and returns aggregated health.
   *
   * @returns {Promise<SystemHealth>}
   */
  async runAllChecks(): Promise<SystemHealth> {
    await Promise.all(
      Array.from(this.checks.entries()).map(([name, check]) =>
        this._executeCheck(name, check)
      )
    );

    return this.getHealth();
  }

  /**
   * Computes overall system health from latest results.
   *
   * @returns {SystemHealth}
   */
  getHealth(): SystemHealth {
    const checks: Record<string, HealthCheckResult> = {};
    let overallStatus: HealthStatus = 'healthy';

    for (const [name, result] of this.results.entries()) {
      checks[name] = result;

      const check = this.checks.get(name);
      if (!check) continue;

      // Determine overall status
      if (result.status === 'unhealthy' && check.critical) {
        overallStatus = 'unhealthy';
      } else if (result.status === 'unhealthy' || result.status === 'degraded') {
        if (overallStatus !== 'unhealthy') {
          overallStatus = 'degraded';
        }
      }
    }

    return {
      status: overallStatus,
      checks,
      timestamp: Date.now(),
    };
  }

  /**
   * Retrieves last result for specific check.
   */
  getCheckResult(name: string): HealthCheckResult | undefined {
    return this.results.get(name);
  }

  private _startCheck(name: string): void {
    const check = this.checks.get(name);
    if (!check) return;

    // Run immediately
    this._executeCheck(name, check).catch(() => {});

    // Set up interval if specified
    if (check.interval && check.interval > 0) {
      const interval = setInterval(() => {
        this._executeCheck(name, check).catch(() => {});
      }, check.interval);

      this.intervals.set(name, interval);
    }
  }

  private _stopCheck(name: string): void {
    const interval = this.intervals.get(name);
    if (interval) {
      clearInterval(interval);
      this.intervals.delete(name);
    }
  }

  /**
   * Executes health check with timeout handling.
   */
  private async _executeCheck(name: string, check: HealthCheck): Promise<HealthCheckResult> {
    const startTime = Date.now();

    try {
      // Create a timeout promise
      const timeoutPromise = new Promise<never>((_, reject) => {
        setTimeout(() => {
          reject(new Error(`Health check '${name}' timed out after ${check.timeout}ms`));
        }, check.timeout);
      });

      // Race between the check and the timeout
      const result = await Promise.race([
        check.check(),
        timeoutPromise,
      ]);

      const duration = Date.now() - startTime;
      const finalResult: HealthCheckResult = {
        ...result,
        timestamp: startTime,
        duration,
      };

      this.results.set(name, finalResult);
      this.emit('checkCompleted', { name, result: finalResult });

      // Emit status change if different from previous
      const previousResult = this.results.get(name);
      if (!previousResult || previousResult.status !== finalResult.status) {
        this.emit('statusChanged', { name, result: finalResult });
      }

      return finalResult;
    } catch (error) {
      const duration = Date.now() - startTime;
      const result: HealthCheckResult = {
        status: 'unhealthy',
        message: error instanceof Error ? error.message : 'Unknown error',
        timestamp: startTime,
        duration,
      };

      this.results.set(name, result);
      this.emit('checkFailed', { name, error, result });

      return result;
    }
  }

  /**
   * Clears stored results map.
   *
   * @fires HealthChecker#resultsCleared
   */
  clearResults(): void {
    this.results.clear();
    this.emit('resultsCleared');
  }

  /**
   * Lists names of registered checks.
   */
  getChecks(): string[] {
    return Array.from(this.checks.keys());
  }

  /**
   * Stops checks, clears state, and removes listeners.
   */
  destroy(): void {
    this.stop();
    this.checks.clear();
    this.results.clear();
    this.removeAllListeners();
  }
}

// Built-in health checks

/**
 * Memory usage health check
 */
export function createMemoryHealthCheck(
  thresholds: { warning: number; critical: number } = { warning: 0.8, critical: 0.9 }
): HealthCheck {
  return {
    name: 'memory',
    async check(): Promise<HealthCheckResult> {
      const usage = process.memoryUsage();
      const heapUsedPercent = usage.heapUsed / usage.heapTotal;

      let status: HealthStatus = 'healthy';
      let message = `Heap usage: ${(heapUsedPercent * 100).toFixed(2)}%`;

      if (heapUsedPercent >= thresholds.critical) {
        status = 'unhealthy';
        message = `Critical: ${message}`;
      } else if (heapUsedPercent >= thresholds.warning) {
        status = 'degraded';
        message = `Warning: ${message}`;
      }

      return {
        status,
        message,
        details: {
          heapUsed: usage.heapUsed,
          heapTotal: usage.heapTotal,
          rss: usage.rss,
          external: usage.external,
        },
        timestamp: Date.now(),
        duration: 0,
      };
    },
    interval: 30000,
    critical: true,
  };
}

/**
 * Event loop lag health check
 */
export function createEventLoopHealthCheck(
  thresholds: { warning: number; critical: number } = { warning: 100, critical: 500 }
): HealthCheck {
  return {
    name: 'event_loop',
    async check(): Promise<HealthCheckResult> {
      const start = Date.now();
      
      await new Promise(resolve => setImmediate(resolve));
      
      const lag = Date.now() - start;

      let status: HealthStatus = 'healthy';
      let message = `Event loop lag: ${lag}ms`;

      if (lag >= thresholds.critical) {
        status = 'unhealthy';
        message = `Critical: ${message}`;
      } else if (lag >= thresholds.warning) {
        status = 'degraded';
        message = `Warning: ${message}`;
      }

      return {
        status,
        message,
        details: { lag },
        timestamp: Date.now(),
        duration: lag,
      };
    },
    interval: 10000,
    critical: true,
  };
}
