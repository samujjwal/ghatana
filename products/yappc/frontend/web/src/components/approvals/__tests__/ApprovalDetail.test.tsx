import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ApprovalDetail, EnrichmentSuggestion } from '../ApprovalDetail';

describe('ApprovalDetail', () => {
  it('renders approval metadata and policy decisions', () => {
    render(
      <ApprovalDetail
        approval={{
          id: 'apr-1',
          projectId: 'proj-1',
          requestedAction: 'Approve generated requirement version',
          status: 'PENDING',
          requesterId: 'agent:planner',
          createdAt: '2026-04-26T11:00:00.000Z',
        }}
        aiSummary="Model suggested this version after conflict resolution."
        confidence={0.87}
        originalContent="Old content"
        proposedContent="New content"
        policyDecisions={[
          {
            id: 'pol-1',
            status: 'REQUIRES_REVIEW',
            reason: 'High-impact scope detected',
            evaluatedAt: '2026-04-26T11:01:00.000Z',
          },
        ]}
      />
    );

    expect(screen.getByTestId('approval-detail')).toBeTruthy();
    expect(screen.getByText('Approve generated requirement version')).toBeTruthy();
    expect(screen.getByText('Confidence: 87%')).toBeTruthy();
    expect(screen.getByText('High-impact scope detected')).toBeTruthy();
  });

  it('invokes action callbacks for pending approvals', () => {
    const onApprove = vi.fn();
    const onReject = vi.fn();
    const onRequestChanges = vi.fn();

    render(
      <ApprovalDetail
        approval={{
          id: 'apr-2',
          projectId: 'proj-1',
          requestedAction: 'Review policy override',
          status: 'PENDING',
          requesterId: 'agent:compliance',
          createdAt: '2026-04-26T12:00:00.000Z',
        }}
        onApprove={onApprove}
        onReject={onReject}
        onRequestChanges={onRequestChanges}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Approve' }));
    fireEvent.click(screen.getByRole('button', { name: 'Reject' }));
    fireEvent.click(screen.getByRole('button', { name: 'Request changes' }));

    expect(onApprove).toHaveBeenCalledWith('apr-2');
    expect(onReject).toHaveBeenCalledWith('apr-2');
    expect(onRequestChanges).toHaveBeenCalledWith('apr-2');
  });

  it('hides action buttons when isAuthorizedApprover is false', () => {
    render(
      <ApprovalDetail
        approval={{
          id: 'apr-3',
          projectId: 'proj-1',
          requestedAction: 'Review sensitive policy override',
          status: 'PENDING',
          requesterId: 'agent:compliance',
          createdAt: '2026-04-26T12:00:00.000Z',
        }}
        isAuthorizedApprover={false}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onRequestChanges={vi.fn()}
      />
    );

    expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Reject' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Request changes' })).toBeNull();
  });

  it('renders enrichment suggestion panel with all fields', () => {
    const suggestion: EnrichmentSuggestion = {
      normalizedTitle: 'User can authenticate via SSO',
      acceptanceCriteria: ['Given a valid SSO token, the user is logged in', 'Invalid tokens return 401'],
      storyTrace: 'US-42',
      confidence: 0.87,
      rationale: 'High keyword density for authentication domain.',
    };

    render(
      <ApprovalDetail
        approval={{
          id: 'apr-4',
          projectId: 'proj-1',
          requestedAction: 'Approve enriched requirement',
          status: 'PENDING',
          requesterId: 'agent:enricher',
          createdAt: '2026-04-26T12:00:00.000Z',
        }}
        enrichmentSuggestion={suggestion}
      />
    );

    expect(screen.getByTestId('enrichment-suggestion')).toBeTruthy();
    expect(screen.getByText('User can authenticate via SSO')).toBeTruthy();
    expect(screen.getByText('Given a valid SSO token, the user is logged in')).toBeTruthy();
    expect(screen.getByText('Invalid tokens return 401')).toBeTruthy();
    expect(screen.getByText('US-42')).toBeTruthy();
    expect(screen.getByText('87%')).toBeTruthy();
    expect(screen.getByText('High keyword density for authentication domain.')).toBeTruthy();
  });

  it('shows green confidence chip for confidence >= 0.8', () => {
    const suggestion: EnrichmentSuggestion = {
      normalizedTitle: 'High confidence requirement',
      acceptanceCriteria: [],
      storyTrace: '',
      confidence: 0.9,
      rationale: '',
    };

    render(
      <ApprovalDetail
        approval={{
          id: 'apr-5',
          projectId: 'proj-1',
          requestedAction: 'Check confidence',
          status: 'PENDING',
          requesterId: 'agent:enricher',
          createdAt: '2026-04-26T12:00:00.000Z',
        }}
        enrichmentSuggestion={suggestion}
      />
    );

    const confidenceChip = screen.getByText('90%');
    expect(confidenceChip.className).toMatch(/emerald/);
  });

  it('shows amber confidence chip for 0.6 <= confidence < 0.8', () => {
    const suggestion: EnrichmentSuggestion = {
      normalizedTitle: 'Medium confidence',
      acceptanceCriteria: [],
      storyTrace: '',
      confidence: 0.7,
      rationale: '',
    };

    render(
      <ApprovalDetail
        approval={{
          id: 'apr-6',
          projectId: 'proj-1',
          requestedAction: 'Check amber confidence',
          status: 'PENDING',
          requesterId: 'agent:enricher',
          createdAt: '2026-04-26T12:00:00.000Z',
        }}
        enrichmentSuggestion={suggestion}
      />
    );

    const confidenceChip = screen.getByText('70%');
    expect(confidenceChip.className).toMatch(/amber/);
  });

  it('does not render enrichment panel when enrichmentSuggestion is undefined', () => {
    render(
      <ApprovalDetail
        approval={{
          id: 'apr-7',
          projectId: 'proj-1',
          requestedAction: 'No enrichment',
          status: 'PENDING',
          requesterId: 'agent:enricher',
          createdAt: '2026-04-26T12:00:00.000Z',
        }}
      />
    );

    expect(screen.queryByTestId('enrichment-suggestion')).toBeNull();
  });
});
