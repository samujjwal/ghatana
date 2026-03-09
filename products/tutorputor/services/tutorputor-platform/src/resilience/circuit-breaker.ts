/**
 * Circuit Breaker and Resilience Framework
 * 
 * Provides comprehensive resilience patterns including circuit breakers,
 * bulkheads, retry mechanisms, and timeout handling.
 */

import { EventEmitter } from 'events';
import { createLogger } from '../utils/logger.js';

const logger = createLogger('resilience');

// ============================================================================
// Circuit Breaker Implementation
// ============================================================================

export enum CircuitBreakerState {
  CLOSED = 'CLOSED',
  OPEN = 'OPEN', 
  HALF_OPEN = 'HALF_OPEN'
}

export interface CircuitBreakerOptions {
  failureThreshold?: number; // Number of failures before opening
  resetTimeout?: number; // Time in ms to wait before trying half-open
  monitoringPeriod?: number; // Time in ms to monitor for failure rate
  expectedRecoveryTime?: number; // Expected time for service recovery
  name?: string;
}

export interface CircuitBreakerMetrics {
  state: CircuitBreakerState;
  failures: number;
  successes: number;
  totalRequests: number;
  failureRate: number;
  lastFailureTime?: Date;
  lastSuccessTime?: Date;
  nextAttemptTime?: Date;
}

export class CircuitBreaker extends EventEmitter {
  private state: CircuitBreakerState = CircuitBreakerState.CLOSED;
  private failures = 0;
  private successes = 0;
  private totalRequests = 0;
  private lastFailureTime?: Date;
  private lastSuccessTime?: Date;
  private nextAttemptTime?: Date;
  private failureThreshold: number;
  private resetTimeout: number;
  private monitoringPeriod: number;
  private name: string;

  constructor(options: CircuitBreakerOptions = {}) {
    super();
    this.failureThreshold = options.failureThreshold || 5;
    this.resetTimeout = options.resetTimeout || 60000; // 1 minute
    this.monitoringPeriod = options.monitoringPeriod || 10000; // 10 seconds
    this.name = options.name || 'unnamed';
  }

  getMetrics(): CircuitBreakerMetrics {
    return {
      state: this.state,
      failures: this.failures,
      successes: this.successes,
      totalRequests: this.totalRequests,
      failureRate: this.totalRequests > 0 ? this.failures / this.totalRequests : 0,
      lastFailureTime: this.lastFailureTime,
      lastSuccessTime: this.lastSuccessTime,
      nextAttemptTime: this.nextAttemptTime,
    };
  }

  private shouldAllowRequest(): boolean {
    switch (this.state) {
      case CircuitBreakerState.CLOSED:
        return true;
      case CircuitBreakerState.OPEN:
        return Date.now() >= (this.nextAttemptTime || 0);
      case CircuitBreakerState.HALF_OPEN:
        return true;
      default:
        return false;
    }
  }

  private onSuccess(): void {
    this.successes++;
    this.totalRequests++;
    this.lastSuccessTime = new Date();

    if (this.state === CircuitBreakerState.HALF_OPEN) {
      this.reset();
    }

    this.emit('success', this.getMetrics());
  }

  private onFailure(): void {
    this.failures++;
    this.totalRequests++;
    this.lastFailureTime = new Date();

    if (this.state === CircuitBreakerState.HALF_OPEN) {
      this.trip();
    } else if (this.state === CircuitBreakerState.CLOSED) {
      if (this.failures >= this.failureThreshold) {
        this.trip();
      }
    }

    this.emit('failure', this.getMetrics());
  }

  private trip(): void {
    this.state = CircuitBreakerState.OPEN;
    this.nextAttemptTime = new Date(Date.now() + this.resetTimeout);
    
    logger.warn({
      circuitBreaker: this.name,
      failures: this.failures,
      totalRequests: this.totalRequests,
      failureRate: this.failures / this.totalRequests,
    }, 'Circuit breaker tripped');

    this.emit('state_change', CircuitBreakerState.OPEN, this.getMetrics());
  }

  private reset(): void {
    this.state = CircuitBreakerState.CLOSED;
    this.failures = 0;
    this.successes = 0;
    this.totalRequests = 0;
    this.lastFailureTime = undefined;
    this.lastSuccessTime = undefined;
    this.nextAttemptTime = undefined;

    logger.info({
      circuitBreaker: this.name,
    }, 'Circuit breaker reset');

    this.emit('state_change', CircuitBreakerState.CLOSED, this.getMetrics());
  }

  async execute<T>(operation: () => Promise<T>): Promise<T> {
    if (!this.shouldAllowRequest()) {
      const error = new Error(`Circuit breaker '${this.name}' is ${this.state}`);
      this.emit('rejected', this.getMetrics());
      throw error;
    }

    try {
      const result = await operation();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      throw error;
    }
  }

  forceOpen(): void {
    this.trip();
  }

  forceClose(): void {
    this.reset();
  }
}

// ============================================================================
// Bulkhead Pattern Implementation
// ============================================================================

export interface BulkheadOptions {
  maxConcurrent?: number;
  maxQueue?: number;
  name?: string;
}

export interface BulkheadMetrics {
  activeExecutions: number;
  queuedExecutions: number;
  rejectedExecutions: number;
  totalExecutions: number;
}

export class Bulkhead extends EventEmitter {
  private activeExecutions = 0;
  private queuedExecutions = 0;
  private rejectedExecutions = 0;
  private totalExecutions = 0;
  private queue: Array<{ resolve: Function; reject: Function; operation: Function }> = [];
  private maxConcurrent: number;
  private maxQueue: number;
  private name: string;

  constructor(options: BulkheadOptions = {}) {
    super();
    this.maxConcurrent = options.maxConcurrent || 10;
    this.maxQueue = options.maxQueue || 100;
    this.name = options.name || 'unnamed';
  }

  getMetrics(): BulkheadMetrics {
    return {
      activeExecutions: this.activeExecutions,
      queuedExecutions: this.queuedExecutions,
      rejectedExecutions: this.rejectedExecutions,
      totalExecutions: this.totalExecutions,
    };
  }

  async execute<T>(operation: () => Promise<T>): Promise<T> {
    return new Promise((resolve, reject) => {
      this.totalExecutions++;

      if (this.activeExecutions < this.maxConcurrent) {
        this.executeImmediately(operation, resolve, reject);
      } else if (this.queue.length < this.maxQueue) {
        this.queue.push({ resolve, reject, operation });
        this.queuedExecutions++;
      } else {
        this.rejectedExecutions++;
        const error = new Error(`Bulkhead '${this.name}' queue is full`);
        this.emit('rejected', this.getMetrics());
        reject(error);
      }
    });
  }

  private async executeImmediately<T>(
    operation: () => Promise<T>,
    resolve: (value: T) => void,
    reject: (reason?: any) => void
  ): Promise<void> {
    this.activeExecutions++;
    this.emit('execution_started', this.getMetrics());

    try {
      const result = await operation();
      resolve(result);
      this.emit('execution_success', this.getMetrics());
    } catch (error) {
      reject(error);
      this.emit('execution_failure', this.getMetrics());
    } finally {
      this.activeExecutions--;
      this.processQueue();
    }
  }

  private processQueue(): void {
    if (this.queue.length > 0 && this.activeExecutions < this.maxConcurrent) {
      const { resolve, reject, operation } = this.queue.shift()!;
      this.queuedExecutions--;
      this.executeImmediately(operation, resolve, reject);
    }
  }
}

// ============================================================================
// Retry Implementation
// ============================================================================

export interface RetryOptions {
  maxAttempts?: number;
  baseDelay?: number;
  maxDelay?: number;
  backoffMultiplier?: number;
  jitter?: boolean;
  retryableErrors?: (error: any) => boolean;
  name?: string;
}

export class Retry {
  private maxAttempts: number;
  private baseDelay: number;
  private maxDelay: number;
  private backoffMultiplier: number;
  private jitter: boolean;
  private retryableErrors: (error: any) => boolean;
  private name: string;

  constructor(options: RetryOptions = {}) {
    this.maxAttempts = options.maxAttempts || 3;
    this.baseDelay = options.baseDelay || 1000;
    this.maxDelay = options.maxDelay || 30000;
    this.backoffMultiplier = options.backoffMultiplier || 2;
    this.jitter = options.jitter !== false;
    this.retryableErrors = options.retryableErrors || this.defaultRetryableErrors;
    this.name = options.name || 'unnamed';
  }

  private defaultRetryableErrors(error: any): boolean {
    if (!error) return false;
    
    // Network errors
    if (error.code === 'ECONNRESET' || error.code === 'ECONNREFUSED') return true;
    if (error.code === 'ETIMEDOUT' || error.code === 'ENOTFOUND') return true;
    
    // HTTP errors
    if (error.status >= 500 && error.status < 600) return true;
    if (error.status === 408 || error.status === 429) return true;
    
    // Database errors
    if (error.message?.includes('connection') || error.message?.includes('timeout')) return true;
    
    return false;
  }

  private calculateDelay(attempt: number): number {
    let delay = this.baseDelay * Math.pow(this.backoffMultiplier, attempt - 1);
    delay = Math.min(delay, this.maxDelay);
    
    if (this.jitter) {
      delay = delay * (0.5 + Math.random() * 0.5);
    }
    
    return Math.floor(delay);
  }

  async execute<T>(operation: () => Promise<T>): Promise<T> {
    let lastError: any;
    
    for (let attempt = 1; attempt <= this.maxAttempts; attempt++) {
      try {
        return await operation();
      } catch (error) {
        lastError = error;
        
        if (attempt === this.maxAttempts || !this.retryableErrors(error)) {
          logger.warn({
            retry: this.name,
            attempt,
            maxAttempts: this.maxAttempts,
            error: error.message,
            retryable: false,
          }, 'Retry failed - not retryable or max attempts reached');
          throw error;
        }
        
        const delay = this.calculateDelay(attempt);
        logger.info({
          retry: this.name,
          attempt,
          maxAttempts: this.maxAttempts,
          delay,
          error: error.message,
        }, 'Retrying operation after delay');
        
        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }
    
    throw lastError;
  }
}

// ============================================================================
// Timeout Implementation
// ============================================================================

export interface TimeoutOptions {
  timeout?: number;
  onTimeout?: () => Error;
  name?: string;
}

export class Timeout {
  private timeoutMs: number;
  private onTimeout: () => Error;
  private name: string;

  constructor(options: TimeoutOptions = {}) {
    this.timeoutMs = options.timeout || 30000; // 30 seconds default
    this.onTimeout = options.onTimeout || (() => new Error(`Operation timed out after ${this.timeoutMs}ms`));
    this.name = options.name || 'unnamed';
  }

  async execute<T>(operation: () => Promise<T>): Promise<T> {
    return Promise.race([
      operation(),
      new Promise<never>((_, reject) => {
        setTimeout(() => {
          logger.warn({
            timeout: this.name,
            timeoutMs: this.timeoutMs,
          }, 'Operation timed out');
          reject(this.onTimeout());
        }, this.timeoutMs);
      })
    ]);
  }
}

// ============================================================================
// Resilience Pipeline
// ============================================================================

export interface ResiliencePipelineOptions {
  circuitBreaker?: CircuitBreakerOptions;
  bulkhead?: BulkheadOptions;
  retry?: RetryOptions;
  timeout?: TimeoutOptions;
  name?: string;
}

export class ResiliencePipeline {
  private circuitBreaker?: CircuitBreaker;
  private bulkhead?: Bulkhead;
  private retry?: Retry;
  private timeout?: Timeout;
  private name: string;

  constructor(options: ResiliencePipelineOptions = {}) {
    this.name = options.name || 'unnamed';
    
    if (options.circuitBreaker) {
      this.circuitBreaker = new CircuitBreaker({ ...options.circuitBreaker, name: `${this.name}-circuit-breaker` });
    }
    
    if (options.bulkhead) {
      this.bulkhead = new Bulkhead({ ...options.bulkhead, name: `${this.name}-bulkhead` });
    }
    
    if (options.retry) {
      this.retry = new Retry({ ...options.retry, name: `${this.name}-retry` });
    }
    
    if (options.timeout) {
      this.timeout = new Timeout({ ...options.timeout, name: `${this.name}-timeout` });
    }
  }

  async execute<T>(operation: () => Promise<T>): Promise<T> {
    let wrappedOperation = operation;

    // Apply timeout first (outermost)
    if (this.timeout) {
      const timeout = this.timeout;
      wrappedOperation = () => timeout.execute(operation);
    }

    // Apply retry
    if (this.retry) {
      const retry = this.retry;
      const currentOperation = wrappedOperation;
      wrappedOperation = () => retry.execute(currentOperation);
    }

    // Apply bulkhead
    if (this.bulkhead) {
      const bulkhead = this.bulkhead;
      const currentOperation = wrappedOperation;
      wrappedOperation = () => bulkhead.execute(currentOperation);
    }

    // Apply circuit breaker (innermost)
    if (this.circuitBreaker) {
      const circuitBreaker = this.circuitBreaker;
      const currentOperation = wrappedOperation;
      wrappedOperation = () => circuitBreaker.execute(currentOperation);
    }

    return wrappedOperation();
  }

  getMetrics() {
    return {
      circuitBreaker: this.circuitBreaker?.getMetrics(),
      bulkhead: this.bulkhead?.getMetrics(),
    };
  }

  // Control methods
  tripCircuitBreaker(): void {
    this.circuitBreaker?.forceOpen();
  }

  resetCircuitBreaker(): void {
    this.circuitBreaker?.forceClose();
  }
}

// ============================================================================
// Resilience Manager
// ============================================================================

export class ResilienceManager {
  private pipelines = new Map<string, ResiliencePipeline>();

  createPipeline(name: string, options: ResiliencePipelineOptions): ResiliencePipeline {
    const pipeline = new ResiliencePipeline({ ...options, name });
    this.pipelines.set(name, pipeline);
    return pipeline;
  }

  getPipeline(name: string): ResiliencePipeline | undefined {
    return this.pipelines.get(name);
  }

  getAllMetrics(): Record<string, any> {
    const metrics: Record<string, any> = {};
    for (const [name, pipeline] of this.pipelines) {
      metrics[name] = pipeline.getMetrics();
    }
    return metrics;
  }

  tripAllCircuitBreakers(): void {
    for (const pipeline of this.pipelines.values()) {
      pipeline.tripCircuitBreaker();
    }
  }

  resetAllCircuitBreakers(): void {
    for (const pipeline of this.pipelines.values()) {
      pipeline.resetCircuitBreaker();
    }
  }
}

// Global instance
export const resilienceManager = new ResilienceManager();

// ============================================================================
// Predefined Pipeline Configurations
// ============================================================================

export const pipelineConfigs = {
  // Database operations - high reliability needed
  database: {
    circuitBreaker: {
      failureThreshold: 3,
      resetTimeout: 30000, // 30 seconds
    },
    bulkhead: {
      maxConcurrent: 20,
      maxQueue: 100,
    },
    retry: {
      maxAttempts: 3,
      baseDelay: 1000,
      maxDelay: 10000,
    },
    timeout: {
      timeout: 30000, // 30 seconds
    },
  },

  // External API calls - may be unreliable
  externalApi: {
    circuitBreaker: {
      failureThreshold: 5,
      resetTimeout: 60000, // 1 minute
    },
    bulkhead: {
      maxConcurrent: 10,
      maxQueue: 50,
    },
    retry: {
      maxAttempts: 5,
      baseDelay: 2000,
      maxDelay: 30000,
    },
    timeout: {
      timeout: 15000, // 15 seconds
    },
  },

  // File operations - can be slow
  fileOperations: {
    circuitBreaker: {
      failureThreshold: 3,
      resetTimeout: 10000, // 10 seconds
    },
    bulkhead: {
      maxConcurrent: 5,
      maxQueue: 20,
    },
    retry: {
      maxAttempts: 2,
      baseDelay: 500,
      maxDelay: 5000,
    },
    timeout: {
      timeout: 120000, // 2 minutes
    },
  },

  // AI/ML operations - can be expensive and slow
  aiOperations: {
    circuitBreaker: {
      failureThreshold: 2,
      resetTimeout: 120000, // 2 minutes
    },
    bulkhead: {
      maxConcurrent: 3,
      maxQueue: 10,
    },
    retry: {
      maxAttempts: 2,
      baseDelay: 5000,
      maxDelay: 60000,
    },
    timeout: {
      timeout: 300000, // 5 minutes
    },
  },
};
