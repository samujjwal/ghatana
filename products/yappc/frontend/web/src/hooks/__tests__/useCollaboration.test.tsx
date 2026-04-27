/**
 * useCollaboration Hook Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

// ---------------------------------------------------------------------------
// Mock @yappc/collab
// ---------------------------------------------------------------------------
const mockManagerInstance = vi.hoisted(() => ({
  connect: vi.fn().mockResolvedValue(true),
  disconnect: vi.fn(),
  onEvent: vi.fn().mockReturnValue(() => {}), // returns unsubscribe fn
  getState: vi.fn().mockReturnValue({
    provider: 'none',
    status: 'disconnected',
    reconnectAttempts: 0,
    failoverActive: false,
  }),
}));

const MockProviderManager = vi.hoisted(() =>
  vi.fn(function MockProviderManagerCtor() {
    return mockManagerInstance;
  }),
);

vi.mock('@yappc/collab', () => ({
  ProviderManager: MockProviderManager,
}));

import { useCollaboration } from '../useCollaboration';

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------
const defaultOptions = {
  projectId: 'project-123',
  getToken: () => 'test-token',
};

describe('useCollaboration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockManagerInstance.connect.mockResolvedValue(true);
    mockManagerInstance.disconnect.mockReturnValue(undefined);
    mockManagerInstance.onEvent.mockReturnValue(() => {});
    mockManagerInstance.getState.mockReturnValue({
      provider: 'none',
      status: 'disconnected',
      reconnectAttempts: 0,
      failoverActive: false,
    });
  });

  it('returns disconnected initial state', () => {
    const { result } = renderHook(() => useCollaboration(defaultOptions));

    expect(result.current.status).toBe('disconnected');
    expect(result.current.isConnected).toBe(false);
    expect(result.current.presence).toBeInstanceOf(Map);
  });

  it('exposes connect and disconnect functions', () => {
    const { result } = renderHook(() => useCollaboration(defaultOptions));

    expect(typeof result.current.connect).toBe('function');
    expect(typeof result.current.disconnect).toBe('function');
  });

  it('creates ProviderManager when enabled', () => {
    renderHook(() => useCollaboration({ ...defaultOptions, enabled: true }));

    expect(MockProviderManager).toHaveBeenCalledTimes(1);
    expect(mockManagerInstance.onEvent).toHaveBeenCalled();
  });

  it('does not create ProviderManager when disabled', () => {
    renderHook(() => useCollaboration({ ...defaultOptions, enabled: false }));

    expect(MockProviderManager).not.toHaveBeenCalled();
  });

  it('does not auto-connect when currentUser is absent', () => {
    renderHook(() =>
      useCollaboration({ ...defaultOptions, currentUser: null }),
    );

    expect(mockManagerInstance.connect).not.toHaveBeenCalled();
  });

  it('does not auto-connect when token is missing', () => {
    renderHook(() =>
      useCollaboration({
        ...defaultOptions,
        getToken: () => null,
        currentUser: { id: 'user-1', name: 'Alice', color: '#0f0', avatar: null },
      }),
    );

    expect(mockManagerInstance.connect).not.toHaveBeenCalled();
  });

  it('auto-connects when currentUser and token are available', async () => {
    renderHook(() =>
      useCollaboration({
        ...defaultOptions,
        currentUser: { id: 'user-1', name: 'Alice', color: '#0f0', avatar: null },
      }),
    );

    await waitFor(() => {
      expect(mockManagerInstance.connect).toHaveBeenCalledWith('project-123');
    });
  });

  it('connect() calls ProviderManager.connect with projectId', async () => {
    const { result } = renderHook(() => useCollaboration(defaultOptions));

    await act(async () => {
      await result.current.connect();
    });

    expect(mockManagerInstance.connect).toHaveBeenCalledWith('project-123');
  });

  it('connect() skips when no token', async () => {
    const { result } = renderHook(() =>
      useCollaboration({ ...defaultOptions, getToken: () => null }),
    );

    await act(async () => {
      await result.current.connect();
    });

    expect(mockManagerInstance.connect).not.toHaveBeenCalled();
  });

  it('disconnect() calls ProviderManager.disconnect', () => {
    const { result } = renderHook(() => useCollaboration(defaultOptions));

    act(() => {
      result.current.disconnect();
    });

    expect(mockManagerInstance.disconnect).toHaveBeenCalled();
  });

  it('calls unsubscribe and disconnect on unmount', () => {
    const unsubscribeFn = vi.fn();
    mockManagerInstance.onEvent.mockReturnValue(unsubscribeFn);

    const { unmount } = renderHook(() => useCollaboration(defaultOptions));
    unmount();

    expect(unsubscribeFn).toHaveBeenCalled();
    expect(mockManagerInstance.disconnect).toHaveBeenCalled();
  });

  it('isConnected is true when status is connected', async () => {
    mockManagerInstance.getState.mockReturnValue({
      provider: 'websocket',
      status: 'connected',
      reconnectAttempts: 0,
      failoverActive: false,
    });

    // Simulate onEvent callback firing with connected state
    let eventCallback: (() => void) | null = null;
    mockManagerInstance.onEvent.mockImplementation((cb: () => void) => {
      eventCallback = cb;
      return () => {};
    });

    const { result } = renderHook(() => useCollaboration(defaultOptions));

    act(() => {
      eventCallback?.();
    });

    expect(result.current.isConnected).toBe(true);
    expect(result.current.status).toBe('connected');
  });
});
