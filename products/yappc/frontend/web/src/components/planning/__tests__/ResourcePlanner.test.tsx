/**
 * ResourcePlanner Component Tests
 */

import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ResourcePlanner } from '../ResourcePlanner';
import type { TeamMember, TaskRequirement } from '../../../services/ai/ResourceAllocationService';

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('ResourcePlanner', () => {
  it('should render capacity overview', () => {
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

    render(<ResourcePlanner tasks={[]} teamMembers={teamMembers} />, { wrapper: createWrapper() });

    expect(screen.getByText('Resource Planner')).toBeDefined();
    expect(screen.getByText('Capacity Overview')).toBeDefined();
  });

  it('should render team utilization', () => {
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

    render(<ResourcePlanner tasks={[]} teamMembers={teamMembers} />, { wrapper: createWrapper() });

    expect(screen.getByText('Team Utilization')).toBeDefined();
    expect(screen.getByText('John')).toBeDefined();
  });

  it('should render task allocations when available', () => {
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

    render(<ResourcePlanner tasks={tasks} teamMembers={teamMembers} />, { wrapper: createWrapper() });

    expect(screen.getByText('Task Allocations')).toBeDefined();
  });

  it('should show auto-allocate button', () => {
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

    render(<ResourcePlanner tasks={[]} teamMembers={teamMembers} />, { wrapper: createWrapper() });

    expect(screen.getByText('Auto-Allocate')).toBeDefined();
  });
});
