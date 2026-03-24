/**
 * Standard async patterns for YAPPC App Creator.
 *
 * Provides standardized patterns for async operations:
 * - Sequential execution with proper error handling
 * - Parallel execution with result aggregation
 * - Retry with exponential backoff
 * - Debounce and throttle
 * - Timeout handling
 * - Result type integration with Promises
 *
 * @doc.type module
 * @doc.purpose Standardize async patterns across YAPPC frontend
 * @doc.layer infrastructure
 * @doc.pattern Async Programming
 */

import { Result, success, failure, ResultError } from '../results/result.js';

// ============================================================================
// Sequential Execution
// ============================================================================

/**
 * Execute async operations sequentially, stopping on first failure.
 */
export async function sequential<T>(
    operations: (() => Promise<T>)[]
): Promise<T[]> {
    const results: T[] = [];
    for (const op of operations) {
        results.push(await op());
    }
    return results;
}

/**
 * Execute operations sequentially with Result type.
 */
export async function sequentialResults<T, E>(
    operations: (() => Promise<Result<T, E>>)[]
): Promise<Result<T, E>[]> {
    const results: Result<T, E>[] = [];
    for (const op of operations) {
        results.push(await op());
    }
    return results;
}

// ============================================================================
// Parallel Execution
// ============================================================================

/**
 * Execute operations in parallel.
 */
export async function parallel<T>(
    operations: (() => Promise<T>)[]
): Promise<T[]> {
    return Promise.all(operations.map(op => op()));
}

/**
 * Execute operations in parallel with a limit on concurrency.
 */
export async function parallelLimited<T>(
    maxConcurrency: number,
    operations: (() => Promise<T>)[]
): Promise<T[]> {
    const results: T[] = [];
    const executing: Promise<void>[] = [];

    for (const op of operations) {
        const promise = op().then(result => {
            results.push(result);
        });
        executing.push(promise);

        if (executing.length >= maxConcurrency) {
            await Promise.race(executing);
            executing.splice(
                0,
                executing.length,
                ...executing.filter(p => p !== promise)
            );
        }
    }

    await Promise.all(executing);
    return results;
}

// ============================================================================
// Retry Pattern
// ============================================================================

export interface RetryOptions {
    maxRetries: number;
    initialDelayMs: number;
    maxDelayMs: number;
    backoffMultiplier?: number;
}

const defaultRetryOptions: RetryOptions = {
    maxRetries: 3,
    initialDelayMs: 1000,
    maxDelayMs: 30000,
    backoffMultiplier: 2,
};

/**
 * Retry an operation with exponential backoff.
 */
export async function withRetry<T>(
    operation: () => Promise<T>,
    options: Partial<RetryOptions> = {}
): Promise<T> {
    const opts = { ...defaultRetryOptions, ...options };

    let lastError: unknown;
    let delay = opts.initialDelayMs;

    for (let attempt = 0; attempt <= opts.maxRetries; attempt++) {
        try {
            return await operation();
        } catch (error) {
            lastError = error;

            if (attempt < opts.maxRetries) {
                await sleep(delay);
                delay = Math.min(
                    delay * (opts.backoffMultiplier || 2),
                    opts.maxDelayMs
                );
            }
        }
    }

    throw lastError;
}

/**
 * Retry with Result type.
 */
export async function withRetryResult<T, E extends ResultError>(
    operation: () => Promise<Result<T, E>>,
    options: Partial<RetryOptions> = {}
): Promise<Result<T, E>> {
    const opts = { ...defaultRetryOptions, ...options };

    let lastResult: Result<T, E> = failure({
        message: 'All retries exhausted',
        code: 'RETRY_EXHAUSTED',
    } as E);
    let delay = opts.initialDelayMs;

    for (let attempt = 0; attempt <= opts.maxRetries; attempt++) {
        const result = await operation();

        if (result.kind === 'success') {
            return result;
        }

        lastResult = result;

        if (attempt < opts.maxRetries) {
            await sleep(delay);
            delay = Math.min(
                delay * (opts.backoffMultiplier || 2),
                opts.maxDelayMs
            );
        }
    }

    return lastResult;
}

// ============================================================================
// Debounce and Throttle
// ============================================================================

/**
 * Debounce a function.
 */
export function debounce<T extends (...args: unknown[]) => unknown>(
    fn: T,
    delayMs: number
): (...args: Parameters<T>) => void {
    let timeoutId: ReturnType<typeof setTimeout> | null = null;

    return (...args: Parameters<T>) => {
        if (timeoutId) {
            clearTimeout(timeoutId);
        }
        timeoutId = setTimeout(() => fn(...args), delayMs);
    };
}

/**
 * Throttle a function.
 */
export function throttle<T extends (...args: unknown[]) => unknown>(
    fn: T,
    limitMs: number
): (...args: Parameters<T>) => void {
    let inThrottle = false;

    return (...args: Parameters<T>) => {
        if (!inThrottle) {
            fn(...args);
            inThrottle = true;
            setTimeout(() => {
                inThrottle = false;
            }, limitMs);
        }
    };
}

// ============================================================================
// Timeout Pattern
// ============================================================================

/**
 * Add timeout to a promise.
 */
export async function withTimeout<T>(
    promise: Promise<T>,
    timeoutMs: number,
    errorMessage = 'Operation timed out'
): Promise<T> {
    const timeoutPromise = new Promise<never>((_, reject) => {
        setTimeout(() => reject(new TimeoutError(errorMessage)), timeoutMs);
    });

    return Promise.race([promise, timeoutPromise]);
}

export class TimeoutError extends Error {
    constructor(message = 'Operation timed out') {
        super(message);
        this.name = 'TimeoutError';
    }
}

// ============================================================================
// Result Type Integration
// ============================================================================

/**
 * Convert a Promise to a Result.
 */
export async function toResult<T, E extends ResultError>(
    promise: Promise<T>,
    errorMapper: (error: unknown) => E
): Promise<Result<T, E>> {
    try {
        const value = await promise;
        return success(value);
    } catch (error) {
        return failure(errorMapper(error));
    }
}

/**
 * Wrap an async function to return Result.
 */
export function wrapResult<T extends (...args: unknown[]) => Promise<unknown>, E extends ResultError>(
    fn: T,
    errorMapper: (error: unknown) => E
): (...args: Parameters<T>) => Promise<Result<Awaited<ReturnType<T>>, E>> {
    return async (...args: Parameters<T>) => {
        try {
            const value = await fn(...args);
            return success(value as Awaited<ReturnType<T>>);
        } catch (error) {
            return failure(errorMapper(error));
        }
    };
}

// ============================================================================
// Common Patterns
// ============================================================================

/**
 * Execute with both timeout and retry.
 */
export async function withTimeoutAndRetry<T>(
    operation: () => Promise<T>,
    timeoutMs: number,
    retryOptions: Partial<RetryOptions> = {}
): Promise<T> {
    return withRetry(
        () => withTimeout(operation(), timeoutMs),
        retryOptions
    );
}

/**
 * Standard pattern for API calls.
 * Includes: timeout and retry.
 */
export async function apiCall<T>(
    operation: () => Promise<T>,
    endpoint: string,
    timeoutMs = 30000
): Promise<T> {
    return withTimeoutAndRetry(
        operation,
        timeoutMs,
        { maxRetries: 3, initialDelayMs: 1000 }
    ).catch(error => {
        throw new ApiError(
            `API call to ${endpoint} failed: ${error instanceof Error ? error.message : String(error)}`,
            endpoint,
            error
        );
    });
}

export class ApiError extends Error {
    constructor(
        message: string,
        public readonly endpoint: string,
        public readonly cause?: unknown
    ) {
        super(message);
        this.name = 'ApiError';
    }
}

// ============================================================================
// Utilities
// ============================================================================

/**
 * Sleep for a given duration.
 */
export function sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Create a cancellable promise.
 */
export function makeCancellable<T>(promise: Promise<T>): {
    promise: Promise<T>;
    cancel: () => void;
} {
    let isCancelled = false;

    const wrappedPromise = new Promise<T>((resolve, reject) => {
        promise
            .then(value => {
                if (!isCancelled) {
                    resolve(value);
                }
            })
            .catch(error => {
                if (!isCancelled) {
                    reject(error);
                }
            });
    });

    return {
        promise: wrappedPromise,
        cancel: () => {
            isCancelled = true;
        },
    };
}

/**
 * Race multiple promises and return the first success or all failures.
 */
export async function raceSuccess<T>(
    promises: Promise<T>[]
): Promise<T> {
    return Promise.race(promises);
}

/**
 * Execute with loading state management.
 */
export async function withLoadingState<T>(
    operation: () => Promise<T>,
    setLoading: (loading: boolean) => void
): Promise<T> {
    setLoading(true);
    try {
        return await operation();
    } finally {
        setLoading(false);
    }
}
