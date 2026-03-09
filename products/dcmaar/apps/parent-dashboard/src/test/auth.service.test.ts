import { describe, it, expect, beforeEach, vi } from 'vitest';
import { authService } from '../services/auth.service';

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value;
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
  };
})();

Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageMock,
});

// Mock the api client
vi.mock('../lib/api', () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

describe('Auth Service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorageMock.clear();
  });

  it('should save token after successful login', async () => {
    const mockToken = 'test-jwt-token';
    
    authService.saveToken(mockToken);
    
    expect(localStorageMock.getItem('token')).toBe(mockToken);
  });

  it('should check authentication status', () => {
    expect(authService.isAuthenticated()).toBe(false);
    
    authService.saveToken('test-token');
    
    expect(authService.isAuthenticated()).toBe(true);
  });
});
