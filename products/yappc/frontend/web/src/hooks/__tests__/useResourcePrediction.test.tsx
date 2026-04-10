/**
 * useResourcePrediction Hook Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useResourcePrediction, useResourceUtilization, useTeamAssignment } from '../useResourcePrediction';
import type { TeamMember, TaskRequirement } from '../../services/ai/ResourceAllocationService';

vi.mock('../../services/ai/ResourceAllocationService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/ai/ResourceAllocationService')>();
  return {
    ...actual,
    allocateResources: vi.fn().mockResolvedValue({
      allocations: [{ taskId: '1', memberId: 'm1', estimatedHours: 4, confidence: 0.9 }],
      capacityPlan: null,
    }),
  };
});

describe('useResourcePrediction', () => {
  it('should return initial state', () => {
    const queryClient = new QueryClient();

    const { result } = renderHook(
      () => useResourcePrediction([], []),
      {
        wrapper: ({ children }) => (
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        ),
      }
    );

    expect(result.current.allocations).toEqual([]);
    expect(result.current.capacityPlan).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });

  it('should allocate resources for tasks', async () => {
    const queryClient = new QueryClient();

    const tasks: TaskRequirement[] = [
      {
        taskId: '1',
        title: 'Test task',
        requiredSkills: ['javascript'],
        estimatedHours: 4,
        priority: 'high',
      },
    ];

    const teamMembers: TeamMember[] = [
      {
        id: 'm1',
        name: 'John',
        role: 'Developer',
        skills: ['javascript'],
        currentWorkload: 50,
        availability: 40,
        efficiency: 0.9,
      },
    ];

    const { result } = renderHook(
      () => useResourcePrediction(tasks, teamMembers),
      {
        wrapper: ({ children }) => (
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        ),
      }
    );

    await waitFor(() => {
      expect(result.current.allocations.length).toBe(1);
    });
  });
});

describe('useResourceUtilization', () => {
  it('should calculate utilization metrics', () => {
    const teamMembers: TeamMember[] = [
      {
        id: 'm1',
        name: 'John',
        role: 'Developer',
        skills: ['javascript'],
        currentWorkload: 50,
        availability: 40,
        efficiency: 0.9,
      },
      {
        id: 'm2',
        name: 'Jane',
        role: 'Developer',
        skills: ['python'],
        currentWorkload: 70,
        availability: 40,
        efficiency: 0.9,
      },
    ];

    const { result } = renderHook(() => useResourceUtilization(teamMembers));

    expect(result.current.averageUtilization).toBe(60);
    expect(result.current.overloadedMembers).toEqual([]);
    expect(result.current.underutilizedMembers).toEqual([]);
  });

  it('should identify overloaded members', () => {
    const teamMembers: TeamMember[] = [
      {
        id: 'm1',
        name: 'Overloaded',
        role: 'Developer',
        skills: ['javascript'],
        currentWorkload: 95,
        availability: 40,
        efficiency: 0.9,
      },
    ];

    const { result } = renderHook(() => useResourceUtilization(teamMembers));

    expect(result.current.overloadedMembers).toContain('m1');
  });
});

describe('useTeamAssignment', () => {
  it('should recommend team member based on skills', () => {
    const teamMembers: TeamMember[] = [
      {
        id: 'm1',
        name: 'React Dev',
        role: 'Developer',
        skills: ['react', 'javascript'],
        currentWorkload: 50,
        availability: 40,
        efficiency: 0.9,
      },
      {
        id: 'm2',
        name: 'Backend Dev',
        role: 'Developer',
        skills: ['python'],
        currentWorkload: 50,
        availability: 40,
        efficiency: 0.9,
      },
    ];

    const { result } = renderHook(() =>
      useTeamAssignment({
        taskId: '1',
        teamMembers,
        requiredSkills: ['react'],
        estimatedHours: 4,
      })
    );

    expect(result.current.recommendedMember).toBeDefined();
    expect(result.current.recommendedMember?.id).toBe('m1');
    expect(result.current.confidence).toBeGreaterThan(0);
    expect(result.current.reasoning).toBeDefined();
  });

  it('should handle empty team', () => {
    const { result } = renderHook(() =>
      useTeamAssignment({
        taskId: '1',
        teamMembers: [],
        requiredSkills: ['react'],
        estimatedHours: 4,
      })
    );

    expect(result.current.recommendedMember).toBeNull();
  });
});
