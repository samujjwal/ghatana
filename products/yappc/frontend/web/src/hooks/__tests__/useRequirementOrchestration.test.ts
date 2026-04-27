/**
 * useRequirementOrchestration Hook Tests
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useRequirementOrchestration } from '../useRequirementOrchestration';
import * as AepClient from '../services/aep/AepOrchestrationClient';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const Wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  return Wrapper;
}

describe('useRequirementOrchestration', () => {
  const mockRunRef = {
    runId: 'run-42',
    agentId: 'requirement-orchestration-agent',
    status: 'QUEUED' as const,
    createdAt: '2026-01-01T00:00:00.000Z',
  };

  beforeEach(() => {
    vi.spyOn(AepClient, 'submitRequirementApproved').mockResolvedValue(
      mockRunRef,
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('exposes submitApproved, runRef, isSubmitting, and error', () => {
    const { result } = renderHook(() => useRequirementOrchestration(), {
      wrapper: createWrapper(),
    });

    expect(typeof result.current.submitApproved).toBe('function');
    expect(result.current.runRef).toBeUndefined();
    expect(result.current.isSubmitting).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('calls submitRequirementApproved with correct params', async () => {
    const { result } = renderHook(() => useRequirementOrchestration(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      result.current.submitApproved({
        projectId: 'proj-1',
        requirementId: 'req-1',
        approvalId: 'appr-1',
      });
    });

    expect(AepClient.submitRequirementApproved).toHaveBeenCalledWith({
      projectId: 'proj-1',
      requirementId: 'req-1',
      approvalId: 'appr-1',
    });
  });

  it('exposes the run reference after successful submission', async () => {
    const { result } = renderHook(() => useRequirementOrchestration(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      result.current.submitApproved({
        projectId: 'proj-1',
        requirementId: 'req-1',
        approvalId: 'appr-1',
      });
    });

    expect(result.current.runRef?.runId).toBe('run-42');
  });

  it('surfaces errors without throwing', async () => {
    vi.spyOn(AepClient, 'submitRequirementApproved').mockRejectedValue(
      new Error('network failure'),
    );

    const { result } = renderHook(() => useRequirementOrchestration(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      result.current.submitApproved({
        projectId: 'proj-1',
        requirementId: 'req-1',
        approvalId: 'appr-1',
      });
    });

    expect(result.current.error?.message).toBe('network failure');
  });
});
