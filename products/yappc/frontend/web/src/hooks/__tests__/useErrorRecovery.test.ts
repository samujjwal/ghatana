/**
 * useErrorRecovery Hook Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useErrorRecovery } from '../useErrorRecovery';

describe('useErrorRecovery', () => {
  beforeEach(() => {
    // Reset online status
    Object.defineProperty(navigator, 'onLine', { value: true, writable: true });
  });

  it('should return initial state', () => {
    const { result } = renderHook(() => useErrorRecovery());

    expect(result.current.error).toBeNull();
    expect(result.current.errorInfo).toBeNull();
    expect(result.current.isOnline).toBe(true);
    expect(result.current.isRetrying).toBe(false);
    expect(result.current.retryCount).toBe(0);
  });

  it('should execute with retry on success', async () => {
    const { result } = renderHook(() => useErrorRecovery());

    const mockFn = vi.fn().mockResolvedValue('success');

    await act(async () => {
      const response = await result.current.executeWithRetry(mockFn);
      expect(response).toBe('success');
    });

    expect(mockFn).toHaveBeenCalledTimes(1);
  });

  it('should retry on retryable errors', async () => {
    const { result } = renderHook(() => useErrorRecovery({
      retryConfig: {
        maxAttempts: 2,
        initialDelay: 10,
        maxDelay: 100,
      },
    }));

    const mockFn = vi.fn()
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValue('success');

    await act(async () => {
      const response = await result.current.executeWithRetry(mockFn);
      expect(response).toBe('success');
    });

    expect(mockFn).toHaveBeenCalledTimes(2);
  });

  it('should not retry on non-retryable errors', async () => {
    const { result } = renderHook(() => useErrorRecovery());

    const mockFn = vi.fn().mockRejectedValue(new Error('Unauthorized'));

    await act(async () => {
      await expect(result.current.executeWithRetry(mockFn)).rejects.toThrow('Unauthorized');
    });

    expect(mockFn).toHaveBeenCalledTimes(1);
  });

  it('should set error state on failure', async () => {
    const { result } = renderHook(() => useErrorRecovery());

    const mockFn = vi.fn().mockRejectedValue(new Error('Test error'));

    await act(async () => {
      await expect(result.current.executeWithRetry(mockFn)).rejects.toThrow();
    });

    expect(result.current.error).not.toBeNull();
    expect(result.current.errorInfo).not.toBeNull();
    expect(result.current.errorInfo?.category).toBe('unknown');
  });

  it('should clear error on clearError call', async () => {
    const { result } = renderHook(() => useErrorRecovery());

    const mockFn = vi.fn().mockRejectedValue(new Error('Test error'));

    await act(async () => {
      await expect(result.current.executeWithRetry(mockFn)).rejects.toThrow();
    });

    expect(result.current.error).not.toBeNull();

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
    expect(result.current.errorInfo).toBeNull();
  });

  it('should track retry count', async () => {
    const { result } = renderHook(() => useErrorRecovery({
      retryConfig: {
        maxAttempts: 3,
        initialDelay: 10,
        maxDelay: 100,
      },
    }));

    const mockFn = vi.fn()
      .mockRejectedValueOnce(new Error('Network error'))
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValue('success');

    await act(async () => {
      await result.current.executeWithRetry(mockFn);
    });

    expect(result.current.retryCount).toBe(0); // Should reset after success
  });

  it('should detect offline status', () => {
    Object.defineProperty(navigator, 'onLine', { value: false, writable: true });

    const { result } = renderHook(() => useErrorRecovery());

    expect(result.current.isOnline).toBe(false);

    // Reset
    Object.defineProperty(navigator, 'onLine', { value: true, writable: true });
  });
});
