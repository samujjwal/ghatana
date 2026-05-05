/**
 * P1-030: Tests for useMutationError hook.
 *
 * @doc.type test
 * @doc.purpose Unit tests for mutation error handling (P1-030)
 * @doc.layer test
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useMutationError, ApiError } from '../useMutationError';

describe('P1-030: useMutationError', () => {
  const mockMutationFn = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should initialize with correct default state', () => {
    const { result } = renderHook(() => useMutationError(mockMutationFn));

    expect(result.current.state.data).toBeNull();
    expect(result.current.state.isLoading).toBe(false);
    expect(result.current.state.error).toBeNull();
    expect(result.current.state.isSuccess).toBe(false);
  });

  it('should set loading state during execution', async () => {
    mockMutationFn.mockImplementation(() => new Promise(resolve => setTimeout(() => resolve('data'), 100)));

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    act(() => {
      result.current.execute('arg1');
    });

    expect(result.current.state.isLoading).toBe(true);

    await waitFor(() => expect(result.current.state.isLoading).toBe(false));
  });

  it('should set data and success on successful execution', async () => {
    mockMutationFn.mockResolvedValue('success-data');

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.data).toBe('success-data');
    expect(result.current.state.isSuccess).toBe(true);
    expect(result.current.state.error).toBeNull();
  });

  it('should categorize 401 as AUTH error with no retry', async () => {
    const authError = { status: 401, code: 'UNAUTHORIZED', message: 'Session expired' };
    mockMutationFn.mockRejectedValue(authError);

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.error?.code).toBe('AUTH');
    expect(result.current.state.error?.retryable).toBe(false);
    expect(result.current.state.error?.message).toContain('session has expired');
  });

  it('should categorize 403 as FORBIDDEN error with no retry', async () => {
    const forbiddenError = { status: 403, code: 'FORBIDDEN', message: 'Access denied' };
    mockMutationFn.mockRejectedValue(forbiddenError);

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.error?.code).toBe('FORBIDDEN');
    expect(result.current.state.error?.retryable).toBe(false);
  });

  it('should categorize 409 as CONFLICT error with retry allowed', async () => {
    const conflictError = { status: 409, code: 'CONFLICT', message: 'Concurrent modification' };
    mockMutationFn.mockRejectedValue(conflictError);

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.error?.code).toBe('CONFLICT');
    expect(result.current.state.error?.retryable).toBe(true);
    expect(result.current.state.error?.message).toContain('concurrent change');
  });

  it('should categorize 429 as retryable error', async () => {
    const rateLimitError = { status: 429, code: 'RATE_LIMITED', message: 'Too many requests' };
    mockMutationFn.mockRejectedValue(rateLimitError);

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.error?.code).toBe('RETRYABLE');
    expect(result.current.state.error?.retryable).toBe(true);
  });

  it('should categorize 5xx as SERVER error with no retry', async () => {
    const serverError = { status: 500, code: 'INTERNAL_ERROR', message: 'Server error' };
    mockMutationFn.mockRejectedValue(serverError);

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.error?.code).toBe('SERVER');
    expect(result.current.state.error?.retryable).toBe(false);
  });

  it('should include correlation ID in error', async () => {
    const error = { status: 500, code: 'ERROR', message: 'Failed', correlationId: 'corr-123-456' };
    mockMutationFn.mockRejectedValue(error);

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.error?.correlationId).toBe('corr-123-456');
  });

  it('should call onSuccess callback when provided', async () => {
    const onSuccess = vi.fn();
    mockMutationFn.mockResolvedValue('data');

    const { result } = renderHook(() => useMutationError(mockMutationFn, { onSuccess }));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(onSuccess).toHaveBeenCalledWith('data');
  });

  it('should call onError callback when provided', async () => {
    const onError = vi.fn();
    const error = { status: 400, code: 'VALIDATION', message: 'Invalid input' };
    mockMutationFn.mockRejectedValue(error);

    const { result } = renderHook(() => useMutationError(mockMutationFn, { onError }));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(onError).toHaveBeenCalled();
    expect(onError.mock.calls[0][0].code).toBe('VALIDATION');
  });

  it('should reset state correctly', async () => {
    mockMutationFn.mockResolvedValue('data');

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.data).toBe('data');

    act(() => {
      result.current.reset();
    });

    expect(result.current.state.data).toBeNull();
    expect(result.current.state.error).toBeNull();
    expect(result.current.state.isSuccess).toBe(false);
  });

  it('should support retry with same arguments', async () => {
    mockMutationFn.mockRejectedValueOnce({ status: 503, message: 'Service unavailable' })
      .mockResolvedValueOnce('success');

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1', 'arg2');
    });

    expect(result.current.state.error).not.toBeNull();

    await act(async () => {
      await result.current.retry();
    });

    expect(result.current.state.data).toBe('success');
    expect(mockMutationFn).toHaveBeenCalledTimes(2);
    expect(mockMutationFn).toHaveBeenNthCalledWith(1, 'arg1', 'arg2');
    expect(mockMutationFn).toHaveBeenNthCalledWith(2, 'arg1', 'arg2');
  });

  it('should handle network errors', async () => {
    const networkError = new TypeError('Failed to fetch');
    networkError.name = 'TypeError';
    mockMutationFn.mockRejectedValue(networkError);

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.error?.code).toBe('NETWORK_ERROR');
    expect(result.current.state.error?.retryable).toBe(true);
    expect(result.current.state.error?.message).toContain('internet connection');
  });

  it('should handle unknown errors gracefully', async () => {
    mockMutationFn.mockRejectedValue('string error');

    const { result } = renderHook(() => useMutationError(mockMutationFn));

    await act(async () => {
      await result.current.execute('arg1');
    });

    expect(result.current.state.error).not.toBeNull();
    expect(result.current.state.error?.status).toBe(500);
  });
});
