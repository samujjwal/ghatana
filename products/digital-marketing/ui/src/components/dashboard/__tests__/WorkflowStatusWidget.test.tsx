/**
 * WorkflowStatusWidget unit tests.
 *
 * @doc.type test
 * @doc.purpose Verify real data binding and rendering states of WorkflowStatusWidget
 * @doc.layer frontend
 */
import React from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WorkflowStatusWidget } from '../WorkflowStatusWidget';
import type { ApprovalRecordResponse } from '@/types/approval';

const PENDING_CAMPAIGN: ApprovalRecordResponse = {
  requestId: 'req-1',
  tenantId: 'tenant-1',
  workspaceId: 'ws-1',
  subjectId: 'c-1',
  requestedBy: 'user-1',
  action: 'campaign-launch',
  targetType: 'CAMPAIGN_LAUNCH',
  targetId: 'c-1',
  description: null,
  riskLevel: 2,
  requiredApproverRole: 'brand-manager',
  status: 'PENDING',
  submittedAt: '2026-05-01T10:00:00Z',
  submittedBy: 'user-1',
  requestedAt: '2026-05-01T10:00:00Z',
  expiresAt: null,
  decidedAt: null,
  decidedBy: null,
  reviewerId: null,
  reviewerNotes: null,
  comment: null,
  snapshotSummary: null,
  validationResultId: null,
  snapshotAt: null,
};

const APPROVED_CAMPAIGN: ApprovalRecordResponse = {
  ...PENDING_CAMPAIGN,
  requestId: 'req-2',
  status: 'APPROVED',
};

describe('WorkflowStatusWidget', () => {
  it('shows empty state when no approvals', () => {
    render(<WorkflowStatusWidget approvals={[]} />);
    expect(screen.getByTestId('workflow-status-empty')).toBeInTheDocument();
    expect(
      screen.queryByTestId('workflow-status-list'),
    ).not.toBeInTheDocument();
  });

  it('shows loading state when isLoading=true', () => {
    render(<WorkflowStatusWidget approvals={[]} isLoading />);
    expect(screen.getByTestId('workflow-status-loading')).toBeInTheDocument();
    expect(
      screen.queryByTestId('workflow-status-empty'),
    ).not.toBeInTheDocument();
  });

  it('shows error state when isError=true and not loading', () => {
    render(<WorkflowStatusWidget approvals={[]} isError />);
    expect(screen.getByTestId('workflow-status-error')).toBeInTheDocument();
  });

  it('shows pending approvals in the list', () => {
    render(<WorkflowStatusWidget approvals={[PENDING_CAMPAIGN]} />);
    const list = screen.getByTestId('workflow-status-list');
    expect(list).toBeInTheDocument();
    expect(list).toHaveTextContent('campaign-launch');
    expect(list).toHaveTextContent('Pending');
  });

  it('excludes non-pending approvals from the active list', () => {
    render(<WorkflowStatusWidget approvals={[APPROVED_CAMPAIGN]} />);
    expect(screen.getByTestId('workflow-status-empty')).toBeInTheDocument();
    expect(
      screen.queryByTestId('workflow-status-list'),
    ).not.toBeInTheDocument();
  });

  it('shows overflow indicator when more than 5 pending approvals exist', () => {
    const manyApprovals: ApprovalRecordResponse[] = Array.from(
      { length: 7 },
      (_, i) => ({ ...PENDING_CAMPAIGN, requestId: `req-${i}` }),
    );
    render(<WorkflowStatusWidget approvals={manyApprovals} />);
    expect(screen.getByTestId('workflow-status-overflow')).toBeInTheDocument();
    expect(
      screen.getByTestId('workflow-status-overflow'),
    ).toHaveTextContent('+2 more');
  });

  it('renders the article with correct aria label', () => {
    render(<WorkflowStatusWidget approvals={[]} />);
    expect(
      screen.getByRole('article', { name: /workflow status/i }),
    ).toBeInTheDocument();
  });
});
