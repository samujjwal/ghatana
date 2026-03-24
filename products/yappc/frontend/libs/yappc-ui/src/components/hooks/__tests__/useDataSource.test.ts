/**
 * @jest-environment jsdom
 */

import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import { useDataSource } from './useDataSource';

// Mock fetch
global.fetch = vi.fn();

describe.skip('useDataSource', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('REST Data Fetching', () => {
    it('fetches data successfully with GET request', async () => {
      const mockData = { id: 1, name: 'Test User' };
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => mockData,
      });

      const { result } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          method: 'GET',
        })
      );

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBe(null);

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.data).toEqual(mockData);
      expect(result.current.error).toBe(null);
    });

    it('fetches data with POST request and body', async () => {
      const requestBody = { name: 'New User' };
      const responseData = { id: 2, ...requestBody };

      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => responseData,
      });

      const { result } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users',
          method: 'POST',
          body: requestBody,
        })
      );

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.data).toEqual(responseData);
      expect(global.fetch).toHaveBeenCalledWith(
        'https://api.example.com/users',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify(requestBody),
        })
      );
    });

    it('handles fetch errors', async () => {
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      });

      const { result } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/999',
        })
      );

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.error).toBeTruthy();
      expect(result.current.error?.message).toContain('404');
      expect(result.current.data).toBe(null);
    });

    it('includes custom headers', async () => {
      const mockData = { success: true };
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => mockData,
      });

      renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/protected',
          headers: {
            Authorization: 'Bearer token123',
            'X-Custom-Header': 'value',
          },
        })
      );

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalled();
      });

      expect(global.fetch).toHaveBeenCalledWith(
        'https://api.example.com/protected',
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: 'Bearer token123',
            'X-Custom-Header': 'value',
          }),
        })
      );
    });
  });

  describe('GraphQL Data Fetching', () => {
    it('fetches GraphQL data successfully', async () => {
      const mockData = { user: { id: 1, name: 'Test User' } };
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => ({ data: mockData }),
      });

      const { result } = renderHook(() =>
        useDataSource({
          type: 'graphql',
          url: 'https://api.example.com/graphql',
          query: 'query GetUser($id: ID!) { user(id: $id) { id name } }',
          variables: { id: '1' },
        })
      );

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.data).toEqual(mockData);
      expect(global.fetch).toHaveBeenCalledWith(
        'https://api.example.com/graphql',
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('GetUser'),
        })
      );
    });

    it('handles GraphQL errors', async () => {
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          errors: [{ message: 'User not found' }],
        }),
      });

      const { result } = renderHook(() =>
        useDataSource({
          type: 'graphql',
          url: 'https://api.example.com/graphql',
          query: 'query GetUser { user { id } }',
        })
      );

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.error).toBeTruthy();
      expect(result.current.error?.message).toBe('User not found');
    });
  });

  describe('Static Data', () => {
    it('uses static data immediately', async () => {
      const staticData = { items: [1, 2, 3] };

      const { result } = renderHook(() =>
        useDataSource({
          type: 'static',
          data: staticData,
        })
      );

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.data).toEqual(staticData);
      expect(result.current.error).toBe(null);
      expect(global.fetch).not.toHaveBeenCalled();
    });
  });

  describe('Caching', () => {
    it('caches data and returns from cache on second call', async () => {
      const mockData = { id: 1, name: 'Cached User' };
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => mockData,
      });

      const { result: result1, unmount } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          cache: true,
          cacheKey: 'user-1',
        })
      );

      await waitFor(() => {
        expect(result1.current.isLoading).toBe(false);
      });

      expect(result1.current.data).toEqual(mockData);
      expect(global.fetch).toHaveBeenCalledTimes(1);

      unmount();

      // Second hook with same cache key
      const { result: result2 } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          cache: true,
          cacheKey: 'user-1',
        })
      );

      await waitFor(() => {
        expect(result2.current.isLoading).toBe(false);
      });

      expect(result2.current.data).toEqual(mockData);
      // Should not fetch again (still 1 call)
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });

    it('respects cache TTL', async () => {
      const mockData = { id: 1 };
      (global.fetch as unknown).mockResolvedValue({
        ok: true,
        json: async () => mockData,
      });

      const { result, unmount } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          cache: true,
          cacheTTL: 1000, // 1 second
          cacheKey: 'ttl-test',
        })
      );

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      unmount();

      // Advance time past TTL
      vi.advanceTimersByTime(1100);

      // Second hook should fetch again
      renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          cache: true,
          cacheTTL: 1000,
          cacheKey: 'ttl-test',
        })
      );

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(2);
      });
    });

    it('disables caching when cache=false', async () => {
      const mockData = { id: 1 };
      (global.fetch as unknown).mockResolvedValue({
        ok: true,
        json: async () => mockData,
      });

      const { unmount } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          cache: false,
        })
      );

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(1);
      });

      unmount();

      renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          cache: false,
        })
      );

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('Refetch', () => {
    it('manually refetches data', async () => {
      const mockData1 = { version: 1 };
      const mockData2 = { version: 2 };

      (global.fetch as unknown)
        .mockResolvedValueOnce({
          ok: true,
          json: async () => mockData1,
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => mockData2,
        });

      const { result } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/data',
        })
      );

      await waitFor(() => {
        expect(result.current.data).toEqual(mockData1);
      });

      // Refetch
      await result.current.refetch();

      await waitFor(() => {
        expect(result.current.data).toEqual(mockData2);
      });

      expect(global.fetch).toHaveBeenCalledTimes(2);
    });

    it('refetches on interval', async () => {
      const mockData = { id: 1 };
      (global.fetch as unknown).mockResolvedValue({
        ok: true,
        json: async () => mockData,
      });

      renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/data',
          refetchInterval: 5000,
        })
      );

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(1);
      });

      // Advance time by 5 seconds
      vi.advanceTimersByTime(5000);

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(2);
      });

      // Advance another 5 seconds
      vi.advanceTimersByTime(5000);

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(3);
      });
    });
  });

  describe('Mutate', () => {
    it('mutates data locally without refetching', async () => {
      const mockData = { count: 0 };
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => mockData,
      });

      const { result } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/counter',
        })
      );

      await waitFor(() => {
        expect(result.current.data).toEqual(mockData);
      });

      // Mutate with new data
      result.current.mutate({ count: 5 });

      expect(result.current.data).toEqual({ count: 5 });
      // Should not trigger new fetch
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });

    it('mutates data with function', async () => {
      const mockData = { count: 0 };
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => mockData,
      });

      const { result } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/counter',
        })
      );

      await waitFor(() => {
        expect(result.current.data).toEqual(mockData);
      });

      // Mutate with function
      result.current.mutate((current) => ({
        count: (current?.count || 0) + 1,
      }));

      expect(result.current.data).toEqual({ count: 1 });
    });
  });

  describe('Transform Response', () => {
    it('transforms response data', async () => {
      const mockData = { user: { id: 1, first_name: 'John', last_name: 'Doe' } };
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => mockData,
      });

      const { result } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          transformResponse: (data) => ({
            id: data.user.id,
            fullName: `${data.user.first_name} ${data.user.last_name}`,
          }),
        })
      );

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.data).toEqual({
        id: 1,
        fullName: 'John Doe',
      });
    });
  });

  describe('Callbacks', () => {
    it('calls onSuccess callback', async () => {
      const mockData = { id: 1 };
      const onSuccess = vi.fn();

      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => mockData,
      });

      renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/data',
          onSuccess,
        })
      );

      await waitFor(() => {
        expect(onSuccess).toHaveBeenCalledWith(mockData);
      });
    });

    it('calls onError callback', async () => {
      const onError = vi.fn();

      (global.fetch as unknown).mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      });

      renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/data',
          onError,
        })
      );

      await waitFor(() => {
        expect(onError).toHaveBeenCalled();
        expect(onError.mock.calls[0][0]).toBeInstanceOf(Error);
      });
    });
  });

  describe('Request Deduplication', () => {
    it('deduplicates concurrent requests', async () => {
      const mockData = { id: 1 };
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => mockData,
      });

      // Render two hooks with same URL simultaneously
      const { result: result1 } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          dedupe: true,
        })
      );

      const { result: result2 } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          dedupe: true,
        })
      );

      await waitFor(() => {
        expect(result1.current.isLoading).toBe(false);
        expect(result2.current.isLoading).toBe(false);
      });

      expect(result1.current.data).toEqual(mockData);
      expect(result2.current.data).toEqual(mockData);
      // Should only fetch once due to deduplication
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });
  });

  describe('Clear Cache', () => {
    it('clears cache for specific key', async () => {
      const mockData = { id: 1 };
      (global.fetch as unknown).mockResolvedValue({
        ok: true,
        json: async () => mockData,
      });

      const { result, unmount } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          cache: true,
          cacheKey: 'clear-test',
        })
      );

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Clear cache
      result.current.clearCache();

      unmount();

      // Should fetch again since cache was cleared
      renderHook(() =>
        useDataSource({
          type: 'rest',
          url: 'https://api.example.com/users/1',
          cache: true,
          cacheKey: 'clear-test',
        })
      );

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(2);
      });
    });
  });
});
