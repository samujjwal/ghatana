/**
 * API mocks for testing
 */

import { vi } from 'vitest';

/**
 * Mock API client for testing
 */
export const mockApiClient = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
  request: vi.fn(),
};

/**
 * Reset all API mock functions
 */
export function resetApiMocks() {
  mockApiClient.get.mockReset();
  mockApiClient.post.mockReset();
  mockApiClient.put.mockReset();
  mockApiClient.patch.mockReset();
  mockApiClient.delete.mockReset();
  mockApiClient.request.mockReset();
}

/**
 * Configure API mock to return success response
 * 
 * @param method - HTTP method to mock
 * @param path - API path to mock
 * @param response - Response data
 * @param status - HTTP status code
 */
export function mockApiSuccess(
  method: 'get' | 'post' | 'put' | 'patch' | 'delete' | 'request',
  path: string,
  response: unknown,
  status = 200
) {
  mockApiClient[method].mockImplementation((url: string, ...args: unknown[]) => {
    if (url === path || url.includes(path)) {
      return Promise.resolve({
        data: response,
        status,
        statusText: 'OK',
        headers: {},
        config: {},
      });
    }
    return Promise.reject(new Error(`No mock found for ${method.toUpperCase()} ${url}`));
  });
}

/**
 * Configure API mock to return error response
 * 
 * @param method - HTTP method to mock
 * @param path - API path to mock
 * @param error - Error object or message
 * @param status - HTTP status code
 */
export function mockApiError(
  method: 'get' | 'post' | 'put' | 'patch' | 'delete' | 'request',
  path: string,
  error: unknown,
  status = 400
) {
  mockApiClient[method].mockImplementation((url: string, ...args: unknown[]) => {
    if (url === path || url.includes(path)) {
      const errorObj = typeof error === 'string' ? { message: error } : error;
      return Promise.reject({
        response: {
          data: errorObj,
          status,
          statusText: 'Error',
          headers: {},
          config: {},
        },
      });
    }
    return Promise.reject(new Error(`No mock found for ${method.toUpperCase()} ${url}`));
  });
}

/**
 * Mock API response for a specific endpoint
 */
export function mockEndpoint(config: {
  method: 'get' | 'post' | 'put' | 'patch' | 'delete' | 'request';
  path: string;
  response?: unknown;
  error?: unknown;
  status?: number;
}) {
  const { method, path, response, error, status = error ? 400 : 200 } = config;
  
  if (error) {
    mockApiError(method, path, error, status);
  } else {
    mockApiSuccess(method, path, response, status);
  }
}

/**
 * Mock multiple API endpoints at once
 */
export function mockEndpoints(configs: Array<{
  method: 'get' | 'post' | 'put' | 'patch' | 'delete' | 'request';
  path: string;
  response?: unknown;
  error?: unknown;
  status?: number;
}>) {
  configs.forEach(mockEndpoint);
}
