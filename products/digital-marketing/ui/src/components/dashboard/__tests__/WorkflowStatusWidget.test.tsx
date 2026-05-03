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
import type { ApprovalRequest } from '@/types/approval';

const PENDING_CAMPAIGN: ApprovalRequest = {
  requestId: 'req-1',
  workspaceId: 'ws-1',
  tenantId: 'tenant-1',
  targetType: 'CAMPAIGN',
  targetId: 'c-1',
  description: 'Q4 Email Campaign',
  riskLevel: 5,
  status: 'PENDING',
  requiredApproverRole: 'admin',
  submittedAt: '2026-05-01T10:00:00Z',
  decidedAt: null,
  decidedBy: null,
  comment: null,
};

const APPROVED_CAMPAIGN: ApprovalRequest = {
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
    expect(list).toHaveTextContent('CAMPAIGN');
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
    const manyApprovals: ApprovalRequest[] = Array.from(
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
