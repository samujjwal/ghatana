/**
 * usePhaseGate Hook Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import type { ItemSummary } from '@/shared/types/phase-gates';

// ─────────────────────────────────────────────────────────────────────────────
// Mocks
// ─────────────────────────────────────────────────────────────────────────────

vi.mock('react-router', () => ({
  useParams: () => ({ projectId: 'proj-1' }),
}));

const mockValidatePhaseTransition = vi.hoisted(() =>
  vi.fn().mockReturnValue({ gateStatus: undefined })
);

vi.mock('@/shared/types/phase-gates', () => ({
  validatePhaseTransition: mockValidatePhaseTransition,
}));

const mockFetch = vi.hoisted(() => vi.fn());
vi.stubGlobal('fetch', mockFetch);

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function makeArtifact(overrides: Partial<ItemSummary> = {}): ItemSummary {
  return {
    id: 'item-1',
    title: 'ADR',
    artifactKind: 'adr',
    status: 'complete',
    lastUpdated: '2026-04-27T00:00:00.000Z',
    ...overrides,
  };
}

function makeWrapper(): React.FC<{ children: React.ReactNode }> {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }) =>
    React.createElement(QueryClientProvider, { client }, children);
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

describe('usePhaseGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns isLoading true while fetching artifacts', () => {
    mockFetch.mockReturnValue(new Promise(() => {})); // never resolves

    // Lazy import to avoid module hoisting issues
    const { usePhaseGate } = vi.importActual('../usePhaseGate') as typeof import('../usePhaseGate');
    // Re-import fresh after mocks are set
    const { result } = renderHook(() => usePhaseGate('shape'), {
      wrapper: makeWrapper(),
    });

    expect(result.current.isLoading).toBe(true);
  });

  it('returns canAdvance false when gate fails', async () => {
    const { usePhaseGate } = await import('../usePhaseGate');

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([makeArtifact({ status: 'draft', artifactKind: 'adr' })]),
    });

    mockValidatePhaseTransition.mockReturnValue({
      gateStatus: {
        gateId: 'shape-gate',
        status: 'failed',
        validationResults: [
          { ruleId: 'adr-required', valid: false, errors: ['adr'], warnings: [] },
        ],
      },
    });

    const { result } = renderHook(() => usePhaseGate('shape'), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => !result.current.isLoading);

    expect(result.current.canAdvance).toBe(false);
  });

  it('returns canAdvance true when gate passes', async () => {
    const { usePhaseGate } = await import('../usePhaseGate');

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([makeArtifact({ status: 'complete', artifactKind: 'adr' })]),
    });

    mockValidatePhaseTransition.mockReturnValue({
      gateStatus: {
        gateId: 'shape-gate',
        status: 'passed',
        validationResults: [],
      },
    });

    const { result } = renderHook(() => usePhaseGate('shape'), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => !result.current.isLoading);

    expect(result.current.canAdvance).toBe(true);
  });

  it('requestAiAssessment sets isAiAssessing true then resolves', async () => {
    const { usePhaseGate } = await import('../usePhaseGate');

    // Artifact fetch
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve([makeArtifact()]),
      })
      // AI assessment fetch
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            score: 85,
            missingItems: [],
            nextSteps: ['Nothing needed'],
            rationale: 'All good.',
          }),
      });

    mockValidatePhaseTransition.mockReturnValue({
      gateStatus: { gateId: 'g', status: 'passed', validationResults: [] },
    });

    const { result } = renderHook(() => usePhaseGate('shape'), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => !result.current.isLoading);

    result.current.requestAiAssessment();

    await waitFor(() => result.current.aiAssessment !== undefined);

    expect(result.current.aiAssessment?.score).toBe(85);
    expect(result.current.aiAssessment?.source).toBe('MODEL');
  });
});
