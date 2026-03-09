/**
 * @fileoverview Throttle Utility
 *
 * Provides throttling functionality to limit the rate at which functions can be called.
 * Essential for handling high-frequency events like scroll, resize, and mouse movement.
 *
 * @module utils/throttle
 */

/**
 * Throttle options
 */
export interface ThrottleOptions {
  /**
   * Invoke on the leading edge of the timeout
   * @default true
   */
  leading?: boolean;

  /**
   * Invoke on the trailing edge of the timeout
   * @default true
   */
  trailing?: boolean;
}

/**
 * Throttled function interface
 */
export interface ThrottledFunction<T extends (...args: any[]) => any> {
  (...args: Parameters<T>): ReturnType<T> | undefined;
  cancel(): void;
  flush(): void;
}

/**
 * Creates a throttled function that only invokes `func` at most once per every `wait` milliseconds.
 *
 * @param func - The function to throttle
 * @param wait - The number of milliseconds to throttle invocations to
 * @param options - Throttle options
 * @returns The throttled function
 *
 * @example
 * ```typescript
 * const handleScroll = throttle((event) => {
 *   console.log('Scroll event', event);
 * }, 100);
 *
 * window.addEventListener('scroll', handleScroll);
 * ```
 */
export function throttle<T extends (...args: any[]) => any>(
  func: T,
  wait: number,
  options: ThrottleOptions = {}
): ThrottledFunction<T> {
  const { leading = true, trailing = true } = options;

  let timeout: NodeJS.Timeout | number | null = null;
  let previous = 0;
  let result: ReturnType<T> | undefined;
  let args: Parameters<T> | null = null;
  let context: any = null;

  const later = () => {
    previous = leading === false ? 0 : Date.now();
    timeout = null;
    result = func.apply(context, args!);
    if (!timeout) {
      context = args = null;
    }
  };

  const throttled = function (this: any, ...newArgs: Parameters<T>): ReturnType<T> | undefined {
    const now = Date.now();
    if (!previous && leading === false) {
      previous = now;
    }

    const remaining = wait - (now - previous);
    context = this;
    args = newArgs;

    if (remaining <= 0 || remaining > wait) {
      if (timeout) {
        clearTimeout(timeout as NodeJS.Timeout);
        timeout = null;
      }
      previous = now;
      result = func.apply(context, args);
      if (!timeout) {
        context = args = null;
      }
    } else if (!timeout && trailing !== false) {
      timeout = setTimeout(later, remaining);
    }

    return result;
  };

  throttled.cancel = () => {
    if (timeout) {
      clearTimeout(timeout as NodeJS.Timeout);
      timeout = null;
    }
    previous = 0;
    context = args = null;
  };

  throttled.flush = () => {
    if (timeout) {
      result = func.apply(context, args!);
      clearTimeout(timeout as NodeJS.Timeout);
      timeout = null;
      previous = Date.now();
      context = args = null;
    }
  };

  return throttled;
}

/**
 * Creates a debounced function that delays invoking `func` until after `wait` milliseconds
 * have elapsed since the last time the debounced function was invoked.
 *
 * @param func - The function to debounce
 * @param wait - The number of milliseconds to delay
 * @returns The debounced function
 *
 * @example
 * ```typescript
 * const handleResize = debounce(() => {
 *   console.log('Window resized');
 * }, 250);
 *
 * window.addEventListener('resize', handleResize);
 * ```
 */
export function debounce<T extends (...args: any[]) => any>(
  func: T,
  wait: number
): ThrottledFunction<T> {
  let timeout: NodeJS.Timeout | number | null = null;
  let result: ReturnType<T> | undefined;

  const debounced = function (this: any, ...args: Parameters<T>): ReturnType<T> | undefined {
    const context = this;

    if (timeout) {
      clearTimeout(timeout as NodeJS.Timeout);
    }

    timeout = setTimeout(() => {
      timeout = null;
      result = func.apply(context, args);
    }, wait);

    return result;
  };

  debounced.cancel = () => {
    if (timeout) {
      clearTimeout(timeout as NodeJS.Timeout);
      timeout = null;
    }
  };

  debounced.flush = () => {
    if (timeout) {
      clearTimeout(timeout as NodeJS.Timeout);
      timeout = null;
    }
  };

  return debounced;
}

/**
 * Rate limiter for controlling event frequency
 */
export class RateLimiter {
  private tokens: number;
  private lastRefill: number;
  private readonly maxTokens: number;
  private readonly refillRate: number; // tokens per second

  constructor(maxTokens: number, refillRate: number) {
    this.maxTokens = maxTokens;
    this.tokens = maxTokens;
    this.lastRefill = Date.now();
    this.refillRate = refillRate;
  }

  /**
   * Try to consume a token. Returns true if successful, false if rate limited.
   */
  tryConsume(tokens = 1): boolean {
    this.refill();

    if (this.tokens >= tokens) {
      this.tokens -= tokens;
      return true;
    }

    return false;
  }

  /**
   * Refill tokens based on time elapsed
   */
  private refill(): void {
    const now = Date.now();
    const elapsed = (now - this.lastRefill) / 1000; // Convert to seconds
    const tokensToAdd = elapsed * this.refillRate;

    this.tokens = Math.min(this.maxTokens, this.tokens + tokensToAdd);
    this.lastRefill = now;
  }

  /**
   * Get current available tokens
   */
  getAvailableTokens(): number {
    this.refill();
    return Math.floor(this.tokens);
  }

  /**
   * Reset the rate limiter
   */
  reset(): void {
    this.tokens = this.maxTokens;
    this.lastRefill = Date.now();
  }
}
