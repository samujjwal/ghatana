import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { RequirementLifecycleBoard } from '../RequirementLifecycleBoard';
import type { RequirementRecord } from '../types';

const requirements: RequirementRecord[] = [
  {
    id: 'req-1',
    title: 'Generate onboarding flow',
    description: 'Create adaptive onboarding based on user persona.',
    priority: 'HIGH',
    status: 'IN_REVIEW',
    tags: ['onboarding'],
    createdAt: '2026-04-26T10:00:00.000Z',
    updatedAt: '2026-04-26T11:00:00.000Z',
    versions: [
      {
        id: 'v-1',
        version: 1,
        summary: 'Initial draft',
        createdBy: 'agent-1',
        createdAt: '2026-04-26T10:10:00.000Z',
      },
    ],
  },
  {
    id: 'req-2',
    title: 'Enforce auth guardrails',
    description: 'Add explicit policy checks for privileged actions.',
    priority: 'CRITICAL',
    status: 'APPROVED',
    tags: ['security'],
    createdAt: '2026-04-26T10:20:00.000Z',
    updatedAt: '2026-04-26T11:20:00.000Z',
    versions: [],
  },
];

describe('RequirementLifecycleBoard', () => {
  it('renders counts and allows selecting requirement details', () => {
    render(<RequirementLifecycleBoard requirements={requirements} />);

    expect(screen.getByTestId('requirement-lifecycle-board')).toBeTruthy();
    expect(screen.getByText('IN_REVIEW: 1')).toBeTruthy();
    expect(screen.getByText('APPROVED: 1')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Enforce auth guardrails/i }));
    expect(screen.getByTestId('requirement-detail')).toBeTruthy();
    expect(screen.getAllByText('Enforce auth guardrails').length).toBeGreaterThan(0);
  });
});
