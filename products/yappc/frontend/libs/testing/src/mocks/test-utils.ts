/**
 * Test Utilities and Helpers
 *
 * Common testing utilities for unit tests, component tests, and E2E tests
 */

/**
 * Wait for a promise to resolve with optional timeout
 *
 * @param promise - Promise to wait for
 * @param timeoutMs - Timeout in milliseconds (default: 5000)
 * @returns Promise that resolves with the value
 */
export async function waitFor<T>(promise: Promise<T>, timeoutMs = 5000): Promise<T> {
  let timeoutId: NodeJS.Timeout | null = null;
  const timeoutPromise = new Promise<never>((_, reject) => {
    timeoutId = setTimeout(() => reject(new Error(`Timeout after ${timeoutMs}ms`)), timeoutMs);
  });

  try {
    const result = await Promise.race([promise, timeoutPromise]);
    if (timeoutId) clearTimeout(timeoutId);
    return result;
  } catch (error) {
    if (timeoutId) clearTimeout(timeoutId);
    throw error;
  }
}

/**
 * Create a deferred promise
 *
 * @returns Object with promise, resolve, and reject
 */
export function createDeferred<T = void>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/**
 * Wait for a condition to be true
 *
 * @param condition - Function that returns boolean
 * @param timeoutMs - Timeout in milliseconds (default: 5000)
 * @param intervalMs - Check interval in milliseconds (default: 100)
 * @returns Promise that resolves when condition is true
 */
export async function waitUntil(
  condition: () => boolean,
  timeoutMs = 5000,
  intervalMs = 100
): Promise<void> {
  const startTime = Date.now();
  while (!condition()) {
    if (Date.now() - startTime > timeoutMs) {
      throw new Error(`Timeout waiting for condition after ${timeoutMs}ms`);
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
}

/**
 * Create a spy on a method that tracks calls
 *
 * @param object - Object containing the method
 * @param methodName - Name of the method to spy on
 * @returns Spy object with call tracking
 */
export function spyOn<T extends Record<string, unknown>, K extends keyof T>(
  object: T,
  methodName: K
) {
  const original = object[methodName];
  const calls: Array<{ args: unknown[]; result?: unknown; error?: unknown }> = [];

  const spy = function (...args: unknown[]) {
    try {
      const result = (original as unknown).apply(object, args);
      calls.push({ args, result });
      return result;
    } catch (error) {
      calls.push({ args, error });
      throw error;
    }
  };

  (object as unknown)[methodName] = spy;

  return {
    calls,
    calledWith(...expectedArgs: unknown[]) {
      return calls.some((call) => JSON.stringify(call.args) === JSON.stringify(expectedArgs));
    },
    callCount() {
      return calls.length;
    },
    restore() {
      (object as unknown)[methodName] = original;
    },
  };
}

/**
 * Deep clone an object
 *
 * @param obj - Object to clone
 * @returns Deep clone of the object
 */
export function deepClone<T>(obj: T): T {
  if (obj === null || typeof obj !== 'object') {
    return obj;
  }

  if (obj instanceof Date) {
    return new Date(obj.getTime()) as unknown;
  }

  if (obj instanceof Array) {
    return obj.map((item) => deepClone(item)) as unknown;
  }

  if (obj instanceof Object) {
    const cloned = {} as T;
    for (const key in obj) {
      if (obj.hasOwnProperty(key)) {
        cloned[key] = deepClone(obj[key]);
      }
    }
    return cloned;
  }

  return obj;
}

/**
 * Merge objects with deep merge
 *
 * @param target - Target object
 * @param source - Source object to merge
 * @returns Merged object
 */
export function deepMerge<T extends Record<string, unknown>>(target: T, source: Partial<T>): T {
  const result = deepClone(target);

  for (const key in source) {
    if (source.hasOwnProperty(key)) {
      const sourceValue = source[key] as unknown;
      const targetValue = result[key] as unknown;

      if (
        sourceValue &&
        typeof sourceValue === 'object' &&
        !Array.isArray(sourceValue) &&
        targetValue &&
        typeof targetValue === 'object' &&
        !Array.isArray(targetValue)
      ) {
        result[key] = deepMerge(targetValue, sourceValue) as unknown;
      } else {
        result[key] = deepClone(sourceValue) as unknown;
      }
    }
  }

  return result;
}

/**
 * Create an array of items with consistent data
 *
 * @param count - Number of items to create
 * @param factory - Factory function to create each item
 * @returns Array of created items
 */
export function createArray<T>(count: number, factory: (index: number) => T): T[] {
  return Array.from({ length: count }, (_, index) => factory(index));
}

/**
 * Delay execution for milliseconds
 *
 * @param ms - Milliseconds to delay
 * @returns Promise that resolves after delay
 */
export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Create a mock localStorage
 *
 * @returns Mock localStorage object
 */
export function createMockLocalStorage() {
  const store: Record<string, string> = {};

  return {
    getItem(key: string): string | null {
      return store[key] || null;
    },
    setItem(key: string, value: string): void {
      store[key] = value;
    },
    removeItem(key: string): void {
      delete store[key];
    },
    clear(): void {
      Object.keys(store).forEach((key) => delete store[key]);
    },
    key(index: number): string | null {
      const keys = Object.keys(store);
      return keys[index] || null;
    },
    get length(): number {
      return Object.keys(store).length;
    },
  };
}

/**
 * Create a mock sessionStorage
 *
 * @returns Mock sessionStorage object
 */
export function createMockSessionStorage() {
  return createMockLocalStorage();
}

/**
 * Compare two arrays for equality
 *
 * @param arr1 - First array
 * @param arr2 - Second array
 * @returns True if arrays are equal
 */
export function arraysEqual<T>(arr1: T[], arr2: T[]): boolean {
  if (arr1.length !== arr2.length) return false;
  return arr1.every((item, index) => item === arr2[index]);
}

/**
 * Create a random ID (for testing)
 *
 * @param prefix - Optional prefix for the ID
 * @returns Random ID string
 */
export function createRandomId(prefix = ''): string {
  const id = Math.random().toString(36).substring(2, 11);
  return prefix ? `${prefix}-${id}` : id;
}

/**
 * Mock IntersectionObserver (for component tests)
 */
export function mockIntersectionObserver() {
  global.IntersectionObserver = class IntersectionObserver {
    /**
     *
     */
    constructor() {}
    /**
     *
     */
    disconnect() {}
    /**
     *
     */
    observe() {}
    /**
     *
     */
    takeRecords() {
      return [];
    }
    /**
     *
     */
    unobserve() {}
  } as unknown;
}

/**
 * Mock ResizeObserver (for component tests)
 */
export function mockResizeObserver() {
  global.ResizeObserver = class ResizeObserver {
    /**
     *
     */
    constructor() {}
    /**
     *
     */
    disconnect() {}
    /**
     *
     */
    observe() {}
    /**
     *
     */
    unobserve() {}
  } as unknown;
}

/**
 * Create a mock fetch response
 *
 * @param data - Response data
 * @param options - Response options
 * @returns Mock Response object
 */
export function createMockFetchResponse(
  data: unknown,
  options?: { status?: number; headers?: Record<string, string> }
) {
  return {
    ok: (options?.status || 200) < 400,
    status: options?.status || 200,
    headers: options?.headers || {},
    json: async () => data,
    text: async () => JSON.stringify(data),
    blob: async () => new Blob([JSON.stringify(data)]),
    clone () {
      return this;
    },
  };
}

/**
 * Assert that two values are equal
 *
 * @param actual - Actual value
 * @param expected - Expected value
 * @param message - Optional assertion message
 */
export function assertEqual<T>(actual: T, expected: T, message?: string): void {
  if (actual !== expected) {
    throw new Error(
      message || `Expected ${JSON.stringify(expected)}, but got ${JSON.stringify(actual)}`
    );
  }
}

/**
 * Assert that a value is truthy
 *
 * @param value - Value to check
 * @param message - Optional assertion message
 */
export function assertTrue(value: unknown, message?: string): void {
  if (!value) {
    throw new Error(message || `Expected truthy value, but got ${JSON.stringify(value)}`);
  }
}

/**
 * Assert that a value is falsy
 *
 * @param value - Value to check
 * @param message - Optional assertion message
 */
export function assertFalse(value: unknown, message?: string): void {
  if (value) {
    throw new Error(message || `Expected falsy value, but got ${JSON.stringify(value)}`);
  }
}

/**
 * Assert that a function throws an error
 *
 * @param fn - Function to call
 * @param message - Optional assertion message
 * @returns The error that was thrown
 */
export function assertThrows(fn: () => void, message?: string): Error {
  try {
    fn();
    throw new Error(message || 'Expected function to throw, but it did not');
  } catch (error) {
    if (error instanceof Error && error.message.includes('Expected function to throw')) {
      throw error;
    }
    return error as Error;
  }
}
