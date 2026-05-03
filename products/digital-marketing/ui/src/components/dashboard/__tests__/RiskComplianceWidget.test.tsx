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
import type { ApprovalRequest } from '@/types/approval';

const LOW_RISK: ApprovalRequest = {
  requestId: 'req-1',
  workspaceId: 'ws-1',
  tenantId: 'tenant-1',
  targetType: 'CAMPAIGN',
  targetId: 'c-1',
  description: 'Low risk campaign',
  riskLevel: 3,
  status: 'PENDING',
  requiredApproverRole: 'editor',
  submittedAt: '2026-05-01T10:00:00Z',
  decidedAt: null,
  decidedBy: null,
  comment: null,
};

const HIGH_RISK: ApprovalRequest = {
  ...LOW_RISK,
  requestId: 'req-2',
  riskLevel: 8,
  description: 'High risk budget plan',
  targetType: 'BUDGET_PLAN',
};

describe('RiskComplianceWidget', () => {
  it('shows no-violation state when no approvals', () => {
    render(<RiskComplianceWidget />);
    expect(screen.getByTestId('risk-compliance-ok')).toBeInTheDocument();
    expect(screen.getByTestId('risk-compliance-ok')).toHaveTextContent(
      'No active violations',
    );
  });

  it('shows no-violation state when all approvals are low risk', () => {
    render(<RiskComplianceWidget approvals={[LOW_RISK]} />);
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

  it('shows alert when high-risk pending approvals exist', () => {
    render(<RiskComplianceWidget approvals={[HIGH_RISK]} />);
    expect(screen.getByTestId('risk-compliance-alert')).toBeInTheDocument();
    expect(screen.getByTestId('risk-compliance-alert')).toHaveTextContent(
      '1 high-risk item pending review',
    );
  });

  it('lists high-risk items with risk score and type', () => {
    render(<RiskComplianceWidget approvals={[HIGH_RISK]} />);
    expect(screen.getByTestId('risk-compliance-list')).toBeInTheDocument();
    expect(screen.getByText(/Risk 8\/10/)).toBeInTheDocument();
    expect(screen.getByText(/BUDGET_PLAN/)).toBeInTheDocument();
  });

  it('excludes approved high-risk items from violations', () => {
    const approvedHighRisk: ApprovalRequest = { ...HIGH_RISK, status: 'APPROVED' };
    render(<RiskComplianceWidget approvals={[approvedHighRisk]} />);
    expect(screen.getByTestId('risk-compliance-ok')).toBeInTheDocument();
  });

  it('uses threshold of 7 — riskLevel 7 is flagged, 6 is not', () => {
    const borderAbove: ApprovalRequest = { ...LOW_RISK, requestId: 'r-a', riskLevel: 7 };
    const borderBelow: ApprovalRequest = { ...LOW_RISK, requestId: 'r-b', riskLevel: 6 };
    const { rerender } = render(<RiskComplianceWidget approvals={[borderAbove]} />);
    expect(screen.getByTestId('risk-compliance-alert')).toBeInTheDocument();

    rerender(<RiskComplianceWidget approvals={[borderBelow]} />);
    expect(screen.getByTestId('risk-compliance-ok')).toBeInTheDocument();
  });

  it('shows plural text for multiple high-risk items', () => {
    const items: ApprovalRequest[] = [
      { ...HIGH_RISK, requestId: 'r1' },
      { ...HIGH_RISK, requestId: 'r2' },
    ];
    render(<RiskComplianceWidget approvals={items} />);
    expect(screen.getByTestId('risk-compliance-alert')).toHaveTextContent(
      '2 high-risk items pending review',
    );
  });

  it('renders the article with correct aria label', () => {
    render(<RiskComplianceWidget />);
    expect(
      screen.getByRole('article', { name: /risk & compliance/i }),
    ).toBeInTheDocument();
  });
});
