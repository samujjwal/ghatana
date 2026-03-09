/**
 * Mock API and storage utilities for testing
 * @module test/utils/mock-api
 */

import { vi } from 'vitest';

/**
 * Creates a mock HTTP response object
 *
 * @param body - Response body data
 * @param status - HTTP status code
 * @param headers - Response headers
 * @returns Mock response with fetch response methods
 *
 * @example
 * ```typescript
 * const response = createMockResponse({ data: 'test' }, 200);
 * const json = await response.json();
 * ```
 */
export function createMockResponse(body = {}, status = 200, headers = {}) {
  return {
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(JSON.stringify(body)),
    blob: () => Promise.resolve(new Blob()),
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
    formData: () => Promise.resolve(new FormData()),
    status,
    headers: new Headers(headers),
    ok: status >= 200 && status < 300,
  };
}

/**
 * Creates a mock fetch function that resolves with a response
 *
 * @param response - Response data
 * @param status - HTTP status code
 * @param headers - Response headers
 * @returns Mocked fetch function
 *
 * @example
 * ```typescript
 * const mockFetch = createMockFetch({ users: [] });
 * global.fetch = mockFetch;
 * ```
 */
export function createMockFetch(response = {}, status = 200, headers = {}) {
  return vi
    .fn()
    .mockResolvedValue(createMockResponse(response, status, headers));
}

/**
 * Creates a mock fetch function that rejects with an error
 *
 * @param error - Error to reject with
 * @returns Mocked fetch function
 *
 * @example
 * ```typescript
 * const mockFetch = createMockRejectedFetch(new Error('Network error'));
 * global.fetch = mockFetch;
 * ```
 */
export function createMockRejectedFetch(error: Error) {
  return vi.fn().mockRejectedValue(error);
}

/**
 * Creates a mock localStorage/sessionStorage implementation
 *
 * @returns Mock storage object with all storage methods
 *
 * @example
 * ```typescript
 * const storage = createMockStorage();
 * storage.setItem('key', 'value');
 * expect(storage.getItem('key')).toBe('value');
 * ```
 */
export function createMockStorage() {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => {
      // eslint-disable-next-line security/detect-object-injection
      return store[key] ?? null;
    }),
    setItem: vi.fn((key: string, value: string) => {
      // eslint-disable-next-line security/detect-object-injection
      store[key] = value.toString();
    }),
    removeItem: vi.fn((key: string) => {
      // eslint-disable-next-line security/detect-object-injection
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    key: vi.fn((index: number) => {
      // eslint-disable-next-line security/detect-object-injection
      return Object.keys(store)[index] ?? null;
    }),
    length: Object.keys(store).length,
  };
}

/**
 * Creates a mock console object with all methods mocked
 *
 * @returns Mock console with log, error, warn, info, debug
 *
 * @example
 * ```typescript
 * const console = createMockConsole();
 * console.log('test');
 * expect(console.log).toHaveBeenCalledWith('test');
 * ```
 */
export function createMockConsole() {
  return {
    log: vi.fn(),
    error: vi.fn(),
    warn: vi.fn(),
    info: vi.fn(),
    debug: vi.fn(),
  };
}

/**
 * Creates a mock function (returns vitest mock fn)
 *
 * @returns Mocked function
 *
 * @example
 * ```typescript
 * const mockHandler = createMockFn();
 * mockHandler('arg1');
 * expect(mockHandler).toHaveBeenCalledWith('arg1');
 * ```
 */
export function createMockFn() {
  return vi.fn();
}

/**
 * Creates a mock promise that resolves with a value
 *
 * @param value - Value to resolve with
 * @returns Resolved promise
 *
 * @example
 * ```typescript
 * const promise = createMockPromise('success');
 * expect(await promise).toBe('success');
 * ```
 */
export function createMockPromise<T>(value: T) {
  return Promise.resolve(value);
}

/**
 * Creates a mock promise that rejects with an error
 *
 * @param error - Error to reject with
 * @returns Rejected promise
 *
 * @example
 * ```typescript
 * const promise = createMockRejectedPromise(new Error('fail'));
 * await expect(promise).rejects.toThrow('fail');
 * ```
 */
export function createMockRejectedPromise(error: Error) {
  return Promise.reject(error);
}
