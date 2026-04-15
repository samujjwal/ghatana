import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

const { mockGovernanceService } = vi.hoisted(() => ({
  mockGovernanceService: {
    getPolicies: vi.fn(),
    getAuditLogs: vi.fn(),
    getComplianceReport: vi.fn(),
  },
}));

vi.mock('../../api/governance.service', () => ({
  governanceService: mockGovernanceService,
}));

import { TrustCenter } from '../../pages/TrustCenter';


describe('TrustCenter', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGovernanceService.getPolicies.mockResolvedValue([]);
    mockGovernanceService.getAuditLogs.mockResolvedValue([]);
    mockGovernanceService.getComplianceReport.mockResolvedValue({
      id: 'report-1',
      period: '30d',
      generatedAt: '2026-04-15T00:00:00Z',
      summary: {
        totalPolicies: 0,
        activePolicies: 0,
        violations: 0,
        remediations: 0,
        complianceScore: 0,
      },
      details: {
        piiScans: {
          totalDatasets: 0,
          datasetsWithPII: 0,
          violations: 0,
          remediated: 0,
        },
        accessAudits: {
          totalAccesses: 0,
          unauthorizedAttempts: 0,
          blockedAccesses: 0,
        },
        retentionCompliance: {
          datasetsCompliant: 0,
          datasetsViolating: 0,
        },
      },
    });
  });

  it('renders the trust-center shell with primary commands and quick-apply actions', async () => {
    render(<TrustCenter />, { wrapper: TestWrapper });

    expect(await screen.findByRole('heading', { name: 'Trust Center' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Add Policy/i })).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search policies or try/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Filter' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Refresh' }).length).toBeGreaterThan(0);
    expect(screen.getByText('Apply GDPR')).toBeInTheDocument();
    expect(screen.getByText('Enable PII Masking')).toBeInTheDocument();
    expect(screen.getByText('Run Compliance Scan')).toBeInTheDocument();
    expect(screen.getByText('Configure Access')).toBeInTheDocument();
  });

  it('loads the trust-center sections through the canonical governance service hooks', async () => {
    render(<TrustCenter />, { wrapper: TestWrapper });

    expect(await screen.findByText(/Overall Compliance Score/i)).toBeInTheDocument();
    expect(screen.getByText('Active Policies')).toBeInTheDocument();
    expect(screen.getByText('Audit Log')).toBeInTheDocument();
    expect(mockGovernanceService.getPolicies).toHaveBeenCalledTimes(1);
    expect(mockGovernanceService.getAuditLogs).toHaveBeenCalledWith(undefined, undefined, 10);
    expect(mockGovernanceService.getComplianceReport).toHaveBeenCalledWith('30d');
  });
});
