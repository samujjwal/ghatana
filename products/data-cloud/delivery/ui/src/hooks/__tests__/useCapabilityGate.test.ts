/**
 * DC-P1-006: Tests for useCapabilityGate and useCapabilitySignal hooks.
 *
 * Verifies that the hooks correctly translate runtime surface registry data
 * into boolean access signals for all three gate modes.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import type { CapabilityRegistrySnapshot, CapabilitySignal } from '../../api/surfaces.service';

// ---------------------------------------------------------------------------
// Hoist mocks before imports
// ---------------------------------------------------------------------------

const { mockUseCapabilityRegistry } = vi.hoisted(() => ({
  mockUseCapabilityRegistry: vi.fn<() => { data: CapabilityRegistrySnapshot | undefined; isLoading: boolean }>(),
}));

vi.mock('../../api/surfaces.service', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/surfaces.service')>();
  return {
    ...actual,
    useCapabilityRegistry: mockUseCapabilityRegistry,
  };
});

import { useCapabilityGate, useCapabilitySignal } from '../useCapabilityGate';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeSnapshot(
  entries: Array<{ key: string; status: CapabilitySignal['status'] }>,
): CapabilityRegistrySnapshot {
  return {
    capabilities: entries.map(({ key, status }) => ({
      key,
      label: key,
      status,
      summary: status.toUpperCase(),
      rawValue: status.toUpperCase(),
    })),
    generatedAt: '2026-01-01T00:00:00Z',
    requestId: 'test-req',
    tenantId: 'test-tenant',
  };
}

// ---------------------------------------------------------------------------
// useCapabilityGate — mode: 'active'
// ---------------------------------------------------------------------------

describe('useCapabilityGate — mode: active', () => {
  beforeEach(() => vi.clearAllMocks());

  it('returns true when the capability is active', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'analytics', status: 'active' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['analytics'], 'active'));
    expect(result.current).toBe(true);
  });

  it('returns false when the capability is degraded (strict active mode)', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'analytics', status: 'degraded' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['analytics'], 'active'));
    expect(result.current).toBe(false);
  });

  it('returns false when the capability is unavailable', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'analytics', status: 'unavailable' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['analytics'], 'active'));
    expect(result.current).toBe(false);
  });

  it('returns false when the capability key is not in the registry', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['missing-capability'], 'active'));
    expect(result.current).toBe(false);
  });

  it('returns true while registry is still loading (open-while-loading default)', () => {
    mockUseCapabilityRegistry.mockReturnValue({ data: undefined, isLoading: true });

    const { result } = renderHook(() => useCapabilityGate(['analytics'], 'active'));
    expect(result.current).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// useCapabilityGate — mode: 'activeOrDegraded'
// ---------------------------------------------------------------------------

describe('useCapabilityGate — mode: activeOrDegraded', () => {
  beforeEach(() => vi.clearAllMocks());

  it('returns true when the capability is active', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'ai.assist', status: 'active' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['ai.assist'], 'activeOrDegraded'));
    expect(result.current).toBe(true);
  });

  it('returns true when the capability is degraded', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'ai.assist', status: 'degraded' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['ai.assist'], 'activeOrDegraded'));
    expect(result.current).toBe(true);
  });

  it('returns false when the capability is unavailable', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'ai.assist', status: 'unavailable' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['ai.assist'], 'activeOrDegraded'));
    expect(result.current).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// useCapabilityGate — mode: 'notUnavailable'
// ---------------------------------------------------------------------------

describe('useCapabilityGate — mode: notUnavailable', () => {
  beforeEach(() => vi.clearAllMocks());

  it('returns true when the capability is active', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'data-fabric', status: 'active' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['data-fabric'], 'notUnavailable'));
    expect(result.current).toBe(true);
  });

  it('returns true when the capability is degraded', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'data-fabric', status: 'degraded' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['data-fabric'], 'notUnavailable'));
    expect(result.current).toBe(true);
  });

  it('returns false when the capability is unavailable', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'data-fabric', status: 'unavailable' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilityGate(['data-fabric'], 'notUnavailable'));
    expect(result.current).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// useCapabilityGate — alias resolution
// ---------------------------------------------------------------------------

describe('useCapabilityGate — alias fallback', () => {
  beforeEach(() => vi.clearAllMocks());

  it('matches on the second alias when the first is absent', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'ai_assist', status: 'active' }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(['ai.assist', 'ai_assist'], 'active'),
    );
    expect(result.current).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// useCapabilitySignal
// ---------------------------------------------------------------------------

describe('useCapabilitySignal', () => {
  beforeEach(() => vi.clearAllMocks());

  it('returns the capability signal for a known key', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'analytics', status: 'degraded' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilitySignal(['analytics']));
    expect(result.current?.key).toBe('analytics');
    expect(result.current?.status).toBe('degraded');
  });

  it('returns undefined when registry is not yet loaded', () => {
    mockUseCapabilityRegistry.mockReturnValue({ data: undefined, isLoading: true });

    const { result } = renderHook(() => useCapabilitySignal(['analytics']));
    expect(result.current).toBeUndefined();
  });

  it('returns undefined for unknown capability key', () => {
    mockUseCapabilityRegistry.mockReturnValue({
      data: makeSnapshot([{ key: 'analytics', status: 'active' }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilitySignal(['unknown-cap']));
    expect(result.current).toBeUndefined();
  });
});
