declare module 'opossum' {
  import { EventEmitter } from 'events';

  interface CircuitBreakerOptions {
    timeout?: number;
    errorThresholdPercentage?: number;
    resetTimeout?: number;
    rollingCountTimeout?: number;
    rollingCountBuckets?: number;
    name?: string;
    group?: string;
    enabled?: boolean;
    allowWarmUp?: boolean;
    volumeThreshold?: number;
  }

  class CircuitBreaker<TI extends unknown[] = unknown[], TR = unknown> extends EventEmitter {
    constructor(action: (...args: TI) => Promise<TR>, options?: CircuitBreakerOptions);
    fire(...args: TI): Promise<TR>;
    fallback(callback: (...args: unknown[]) => unknown): this;
    on(event: string, listener: (...args: unknown[]) => void): this;
    opened: boolean;
    closed: boolean;
    halfOpen: boolean;
    stats: unknown;
  }

  export default CircuitBreaker;
}
