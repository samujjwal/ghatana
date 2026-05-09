/**
 * RiskComplianceWidget unit tests.
 *
 * @doc.type test
 * @doc.purpose Verify real data binding and rendering states of RiskComplianceWidget
 * @doc.layer frontend
 */
import React from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RiskComplianceWidget } from '../RiskComplianceWidget';
import type { ApprovalRecordResponse } from '@/types/approval';

const BASE_PENDING: ApprovalRecordResponse = {
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

function makePending(id: string, action = 'campaign-launch'): ApprovalRecordResponse {
  return { ...BASE_PENDING, requestId: id, action };
}

function fiveOrMorePending(): ApprovalRecordResponse[] {
  return Array.from({ length: 5 }, (_, i) => makePending(`req-${i}`));
}

describe('RiskComplianceWidget', () => {
  it('shows no-violation state when no approvals', () => {
    render(<RiskComplianceWidget />);
    expect(screen.getByTestId('risk-compliance-ok')).toBeInTheDocument();
    expect(screen.getByTestId('risk-compliance-ok')).toHaveTextContent(
      'No active violations',
    );
  });

  it('shows no-violation state when fewer than 5 pending approvals', () => {
    render(<RiskComplianceWidget approvals={[makePending('req-1')]} />);
    expect(screen.getByTestId('risk-compliance-ok')).toBeInTheDocument();
    expect(
      screen.queryByTestId('risk-compliance-alert'),
    ).not.toBeInTheDocument();
  });

  it('shows loading state when isLoading=true', () => {
    render(<RiskComplianceWidget isLoading />);
    expect(screen.getByTestId('risk-compliance-loading')).toBeInTheDocument();
    expect(
      screen.queryByTestId('risk-compliance-ok'),
    ).not.toBeInTheDocument();
  });

  it('shows error state when isError=true and not loading', () => {
    render(<RiskComplianceWidget isError />);
    expect(screen.getByTestId('risk-compliance-error')).toBeInTheDocument();
  });

  it('shows alert when 5 or more pending approvals exist', () => {
    render(<RiskComplianceWidget approvals={fiveOrMorePending()} />);
    expect(screen.getByTestId('risk-compliance-alert')).toBeInTheDocument();
    expect(screen.getByTestId('risk-compliance-alert')).toHaveTextContent(
      '5 approvals pending review',
    );
  });

  it('lists pending items with action and subjectId', () => {
    render(<RiskComplianceWidget approvals={fiveOrMorePending()} />);
    expect(screen.getByTestId('risk-compliance-list')).toBeInTheDocument();
    expect(screen.getAllByText('campaign-launch').length).toBeGreaterThan(0);
  });

  it('excludes non-PENDING items from compliance count', () => {
    const approved: ApprovalRecordResponse = { ...BASE_PENDING, requestId: 'a-1', status: 'APPROVED' };
    render(<RiskComplianceWidget approvals={[approved]} />);
    expect(screen.getByTestId('risk-compliance-ok')).toBeInTheDocument();
  });

  it('threshold boundary — 4 pending is ok, 5 triggers alert', () => {
    const fourItems = Array.from({ length: 4 }, (_, i) => makePending(`r-${i}`));
    const fiveItems = Array.from({ length: 5 }, (_, i) => makePending(`r-${i}`));
    const { rerender } = render(<RiskComplianceWidget approvals={fourItems} />);
    expect(screen.getByTestId('risk-compliance-ok')).toBeInTheDocument();

    rerender(<RiskComplianceWidget approvals={fiveItems} />);
    expect(screen.getByTestId('risk-compliance-alert')).toBeInTheDocument();
  });

  it('shows plural text for multiple pending approvals', () => {
    const items = Array.from({ length: 6 }, (_, i) => makePending(`r${i}`));
    render(<RiskComplianceWidget approvals={items} />);
    expect(screen.getByTestId('risk-compliance-alert')).toHaveTextContent(
      '6 approvals pending review',
    );
  });

  it('renders the widget container with data-testid', () => {
    render(<RiskComplianceWidget />);
    const widget = screen.getByTestId('risk-compliance-widget');
    expect(widget).toBeInTheDocument();
    expect(screen.getByText('Risk & Compliance')).toBeInTheDocument();
  });
});
