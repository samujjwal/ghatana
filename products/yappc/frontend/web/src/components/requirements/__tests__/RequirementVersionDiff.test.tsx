import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { RequirementVersionDiff } from '../RequirementVersionDiff';
import type { RequirementVersionFull } from '../RequirementVersionDiff';

const fromVersion: RequirementVersionFull = {
  id: 'v-1',
  version: 1,
  summary: 'Initial draft of the requirement',
  createdBy: 'agent-1',
  createdAt: '2026-04-26T10:00:00.000Z',
  content: { title: 'Auth guardrail', description: 'Enforce auth policies' },
  acceptanceCriteria: { criteria: ['Must block unauthenticated requests'] },
};

const toVersion: RequirementVersionFull = {
  id: 'v-2',
  version: 2,
  summary: 'Revised draft after review — added edge cases',
  createdBy: 'user-alice',
  createdAt: '2026-04-26T12:00:00.000Z',
  content: {
    title: 'Auth guardrail v2',
    description: 'Enforce auth policies with rate limiting',
  },
  acceptanceCriteria: {
    criteria: [
      'Must block unauthenticated requests',
      'Must rate-limit burst requests',
    ],
  },
};

describe('RequirementVersionDiff', () => {
  it('renders version badges for from and to versions', () => {
    render(<RequirementVersionDiff fromVersion={fromVersion} toVersion={toVersion} />);
    expect(screen.getByText('v1')).toBeInTheDocument();
    expect(screen.getByText('v2')).toBeInTheDocument();
  });

  it('shows changed chip for summary field that differs', () => {
    render(<RequirementVersionDiff fromVersion={fromVersion} toVersion={toVersion} />);
    const changedChips = screen.getAllByText('Changed');
    // summary and content and criteria all changed → at least one "Changed" chip
    expect(changedChips.length).toBeGreaterThan(0);
  });

  it('shows "No changes" badge when versions are identical', () => {
    render(<RequirementVersionDiff fromVersion={fromVersion} toVersion={fromVersion} />);
    expect(screen.getByText('No changes')).toBeInTheDocument();
  });

  it('shows author names for both versions', () => {
    render(<RequirementVersionDiff fromVersion={fromVersion} toVersion={toVersion} />);
    expect(screen.getByText('by agent-1')).toBeInTheDocument();
    expect(screen.getByText('by user-alice')).toBeInTheDocument();
  });

  it('renders content and acceptance-criteria diff sections when data is present', () => {
    render(<RequirementVersionDiff fromVersion={fromVersion} toVersion={toVersion} />);
    expect(screen.getByTestId('diff-field-content')).toBeInTheDocument();
    expect(screen.getByTestId('diff-field-acceptance criteria')).toBeInTheDocument();
  });

  it('omits content section when both versions have no content', () => {
    const noContent: RequirementVersionFull = { ...fromVersion, content: null };
    const noContent2: RequirementVersionFull = { ...toVersion, content: null };
    render(<RequirementVersionDiff fromVersion={noContent} toVersion={noContent2} />);
    expect(screen.queryByTestId('diff-field-content')).not.toBeInTheDocument();
  });

  it('shows insertion/deletion counts in summary badges', () => {
    render(<RequirementVersionDiff fromVersion={fromVersion} toVersion={toVersion} />);
    // There are insertions (new lines) so a "+N" chip should exist
    const addedChip = screen.queryByLabelText(/added lines/);
    expect(addedChip).not.toBeNull();
  });
});
