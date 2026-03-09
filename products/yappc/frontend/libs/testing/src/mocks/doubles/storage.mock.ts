/**
 * Storage mocks for testing
 */

import { vi } from 'vitest';

/**
 * Mock storage service for testing
 */
export const mockStorageService = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
  key: vi.fn(),
  length: 0,
};

/**
 * Reset all storage mock functions
 */
export function resetStorageMocks() {
  mockStorageService.getItem.mockReset();
  mockStorageService.setItem.mockReset();
  mockStorageService.removeItem.mockReset();
  mockStorageService.clear.mockReset();
  mockStorageService.key.mockReset();
  mockStorageService.length = 0;
}

/**
 * Mock storage data
 */
export interface MockStorageData {
  [key: string]: string;
}

/**
 * Initialize mock storage with data
 * 
 * @param data - Storage data to initialize
 */
export function initMockStorage(data: MockStorageData = {}) {
  const storageData = { ...data };
  mockStorageService.length = Object.keys(storageData).length;
  
  mockStorageService.getItem.mockImplementation((key: string) => {
    return storageData[key] || null;
  });
  
  mockStorageService.setItem.mockImplementation((key: string, value: string) => {
    storageData[key] = value;
    mockStorageService.length = Object.keys(storageData).length;
  });
  
  mockStorageService.removeItem.mockImplementation((key: string) => {
    delete storageData[key];
    mockStorageService.length = Object.keys(storageData).length;
  });
  
  mockStorageService.clear.mockImplementation(() => {
    Object.keys(storageData).forEach(key => {
      delete storageData[key];
    });
    mockStorageService.length = 0;
  });
  
  mockStorageService.key.mockImplementation((index: number) => {
    return Object.keys(storageData)[index] || null;
  });
  
  return storageData;
}

/**
 * Mock localStorage for testing
 */
export function mockLocalStorage() {
  const originalLocalStorage = global.localStorage;
  
  Object.defineProperty(window, 'localStorage', {
    value: mockStorageService,
    writable: true,
  });
  
  return () => {
    Object.defineProperty(window, 'localStorage', {
      value: originalLocalStorage,
      writable: true,
    });
  };
}

/**
 * Mock sessionStorage for testing
 */
export function mockSessionStorage() {
  const originalSessionStorage = global.sessionStorage;
  
  Object.defineProperty(window, 'sessionStorage', {
    value: mockStorageService,
    writable: true,
  });
  
  return () => {
    Object.defineProperty(window, 'sessionStorage', {
      value: originalSessionStorage,
      writable: true,
    });
  };
}
