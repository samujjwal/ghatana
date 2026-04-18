import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import { toast } from 'sonner';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';

const { mockGovernanceService } = vi.hoisted(() => ({
  mockGovernanceService: {
    getPolicies: vi.fn(),
    getAuditLogs: vi.fn(),
    getComplianceReport: vi.fn(),
    getRecommendations: vi.fn(),
    getLifecycleSurfaces: vi.fn(),
    classifyRetention: vi.fn(),
    redactEntity: vi.fn(),
    getRetentionPolicy: vi.fn(),
    purgeRetentionDryRun: vi.fn(),
    purgeRetentionExecute: vi.fn(),
    generateComplianceReport: vi.fn(),
  },
}));

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
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
    mockGovernanceService.getAuditLogs.mockResolvedValue([
      {
        id: 'audit-1',
        timestamp: '2026-04-15T08:00:00Z',
        userId: 'auditor-1',
        userName: 'Auditor',
        action: 'PII_SCAN',
        resourceType: 'governance',
        resourceId: TEST_TENANT_ID,
        outcome: 'SUCCESS',
        details: {},
      },
    ]);
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
    mockGovernanceService.getRecommendations.mockResolvedValue([
      {
        id: 'recommend-retention-classification',
        title: 'Classify unreviewed collections',
        summary: 'Retention coverage is incomplete.',
        priority: 'high',
        action: 'classify-retention',
        actionLabel: 'Open retention action',
        policyId: 'retention-classification',
        payload: {
          tier: 'compliance',
          reason: 'Review 3 unclassified collections',
          piiFields: ['email', 'ssn'],
        },
        evidence: [
          '3 collections still require retention classification.',
        ],
      },
    ]);
    mockGovernanceService.getLifecycleSurfaces.mockResolvedValue([
      {
        id: 'retention-operations',
        title: 'Retention Operations',
        status: 'live-action',
        summary: '3 collections still need retention classification before coverage is complete.',
        evidence: [
          '9 collections already classified',
          '2 policies expire within 30 days',
          'Launcher routes support classification lookup plus dry-run and execute purge flows',
        ],
        action: 'classify-retention',
        actionLabel: 'Classify retention',
      },
      {
        id: 'access-review',
        title: 'Access Review',
        status: 'derived-read-only',
        summary: 'Access review remains an operator visibility surface derived from audit and compliance summaries rather than an approval-mutation workflow.',
        evidence: [
          '1 authorization-related failures inform current review posture',
          'Last audit snapshot: 2026-04-15T08:00:00Z',
          'No launcher route currently accepts approve or reject access-review decisions',
        ],
        action: 'access-review',
        actionLabel: 'See read-only status',
      },
      {
        id: 'policy-lifecycle',
        title: 'Policy Lifecycle',
        status: 'unavailable',
        summary: 'General policy create, update, toggle, and delete flows are still outside the current launcher-backed governance contract.',
        evidence: [
          'Policy creation is not exposed by the current Data Cloud governance API.',
        ],
      },
    ]);
    mockGovernanceService.getRetentionPolicy.mockResolvedValue({
      collection: 'customers',
      tier: 'standard',
      retentionDays: 365,
      legalHolds: [],
      piiFields: ['email'],
      lastClassifiedAt: '1970-01-01T00:00:00Z',
      status: 'DEFAULT',
    });
    mockGovernanceService.purgeRetentionDryRun.mockResolvedValue({
      collection: 'customers',
      dryRun: true,
      status: 'DRY_RUN_COMPLETE',
      confirmationToken: 'confirm-123',
      tokenExpiresInSec: 900,
      estimatedRows: 24,
      sampleEntityIds: ['cust-1', 'cust-2'],
      requestId: 'req-dry-run',
    });
    mockGovernanceService.purgeRetentionExecute.mockResolvedValue({
      collection: 'customers',
      dryRun: false,
      status: 'PURGE_COMPLETED',
      deletedRows: 24,
      requestedRows: 24,
      failedRows: 0,
      deletedEntityIds: ['cust-1', 'cust-2'],
      completedAt: '2026-04-15T08:10:00Z',
      requestId: 'req-execute',
    });
    mockGovernanceService.generateComplianceReport.mockResolvedValue({ reportId: 'compliance-1', status: 'ready' });
  });

  it('renders the trust-center shell with lifecycle truth and action-center commands', async () => {
    render(<TrustCenter />, { wrapper: TestWrapper });

    expect(await screen.findByRole('heading', { name: 'Trust Center' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Open Live Action/i })).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search live safeguards/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Filter' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Refresh' }).length).toBeGreaterThan(0);
    expect(screen.getByText('Governance Lifecycle Truth')).toBeInTheDocument();
    expect(screen.getByText('Classify Retention')).toBeInTheDocument();
    expect(screen.getByText('Redact PII')).toBeInTheDocument();
    expect(screen.getByText('Refresh Compliance')).toBeInTheDocument();
    expect(screen.getByText('Dry Run Purge')).toBeInTheDocument();
    expect(screen.getByText('Access Review Status')).toBeInTheDocument();
  });

  it('loads the trust-center sections through the canonical governance service hooks', async () => {
    render(<TrustCenter />, { wrapper: TestWrapper });

    expect(await screen.findByText(/Overall Compliance Score/i)).toBeInTheDocument();
    expect(await screen.findByText(/1 event/i)).toBeInTheDocument();
    expect(screen.getByText('Derived Policy Coverage')).toBeInTheDocument();
    expect(screen.getByText('Audit Timeline')).toBeInTheDocument();
    expect(screen.getByText((content) => content.includes('Last updated by Auditor'))).toBeInTheDocument();
    expect(screen.getAllByText(new RegExp(`governance:${TEST_TENANT_ID}`, 'i')).length).toBeGreaterThan(0);
    expect(mockGovernanceService.getPolicies).toHaveBeenCalledTimes(1);
    expect(mockGovernanceService.getAuditLogs).toHaveBeenCalledWith(undefined, undefined, 10);
    expect(mockGovernanceService.getComplianceReport).toHaveBeenCalledWith('30d');
    expect(mockGovernanceService.getRecommendations).toHaveBeenCalledTimes(1);
    expect(mockGovernanceService.getLifecycleSurfaces).toHaveBeenCalledTimes(1);
  });

  it('renders lifecycle truth cards with live, read-only, and unavailable governance posture', async () => {
    render(<TrustCenter />, { wrapper: TestWrapper });

    expect(await screen.findByTestId('trust-lifecycle-retention-operations')).toBeInTheDocument();
    expect(screen.getByTestId('trust-lifecycle-access-review')).toBeInTheDocument();
    expect(screen.getByTestId('trust-lifecycle-policy-lifecycle')).toHaveTextContent(/outside the current launcher-backed governance contract/i);
  });

  it('renders governance recommendations and prefills the retention action from them', async () => {
    render(<TrustCenter />, { wrapper: TestWrapper });

    expect(await screen.findByTestId('trust-recommendation-recommend-retention-classification')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('trust-recommendation-apply-recommend-retention-classification'));

    expect(await screen.findByTestId('trust-quick-action-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('trust-retention-tier')).toHaveValue('compliance');
    expect(screen.getByTestId('trust-retention-reason')).toHaveValue('Review 3 unclassified collections');
    expect(screen.getByTestId('trust-retention-pii-fields')).toHaveValue('email, ssn');
  });

  it('refreshes compliance state through the launcher-backed summary action', async () => {
    render(<TrustCenter />, { wrapper: TestWrapper });

    fireEvent.click(await screen.findByTestId('trust-quick-action-refresh-compliance'));

    await waitFor(() => {
      expect(mockGovernanceService.generateComplianceReport).toHaveBeenCalledWith('30d');
      expect(vi.mocked(toast.success)).toHaveBeenCalledWith('Compliance summary refreshed: compliance-1');
      expect(screen.getByTestId('trust-action-summary')).toHaveTextContent(/Compliance summary refreshed/i);
    });
  });

  it('discloses access review as a read-only launcher boundary', async () => {
    render(<TrustCenter />, { wrapper: TestWrapper });

    fireEvent.click(await screen.findByTestId('trust-quick-action-access-review'));

    expect(vi.mocked(toast.info)).toHaveBeenCalledWith(
      'Access review remains derived from audit and compliance summary data in this deployment.',
    );
    expect(screen.getByTestId('trust-action-summary')).toHaveTextContent(/Access review remains read-only/i);
    expect(screen.getByTestId('trust-action-summary')).toHaveTextContent(/not yet available/i);
  });

  it('submits a real retention classification quick action', async () => {
    mockGovernanceService.classifyRetention.mockResolvedValue({
      collection: 'customers',
      tier: 'compliance',
      retentionDays: 2555,
      classifiedAt: '2026-04-15T08:05:00Z',
      classifiedBy: 'tenant-a',
      reason: 'GDPR Article 17 review',
      piiFields: ['email'],
      status: 'CLASSIFIED',
    });

    render(<TrustCenter />, { wrapper: TestWrapper });

    fireEvent.click(screen.getByRole('button', { name: /Classify Retention/i }));
    fireEvent.change(screen.getByLabelText('Collection'), { target: { value: 'customers' } });
    fireEvent.change(screen.getByLabelText('PII Fields'), { target: { value: 'email' } });
    fireEvent.click(screen.getByTestId('trust-quick-action-submit'));

    await waitFor(() => {
      expect(mockGovernanceService.classifyRetention).toHaveBeenCalledWith({
        collection: 'customers',
        tier: 'compliance',
        reason: 'GDPR Article 17 review',
        piiFields: ['email'],
      });
      expect(vi.mocked(toast.success)).toHaveBeenCalledWith('Retention tier applied to customers');
      expect(screen.getByTestId('trust-action-summary')).toHaveTextContent('Retention policy applied');
    });
  });

  it('submits a real PII redaction quick action', async () => {
    mockGovernanceService.redactEntity.mockResolvedValue({
      collection: 'customers',
      entityId: 'ent-123',
      redactedFields: ['email'],
      requestedFields: ['email'],
      reason: 'Customer privacy request',
      status: 'REDACTED',
      redactedAt: '2026-04-15T08:05:00Z',
    });

    render(<TrustCenter />, { wrapper: TestWrapper });

    fireEvent.click(screen.getByRole('button', { name: /Redact PII/i }));
    fireEvent.change(screen.getByLabelText('Collection'), { target: { value: 'customers' } });
    fireEvent.change(screen.getByLabelText('Entity ID'), { target: { value: 'ent-123' } });
    fireEvent.change(screen.getByLabelText('Fields'), { target: { value: 'email' } });
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Customer privacy request' } });
    fireEvent.click(screen.getByTestId('trust-quick-action-submit'));

    await waitFor(() => {
      expect(mockGovernanceService.redactEntity).toHaveBeenCalledWith({
        collection: 'customers',
        entityId: 'ent-123',
        fields: ['email'],
        reason: 'Customer privacy request',
      });
      expect(vi.mocked(toast.success)).toHaveBeenCalledWith('Redacted 1 field');
      expect(screen.getByTestId('trust-action-summary')).toHaveTextContent('PII redaction completed');
    });
  });

  it('runs retention purge as a dry run before executing with the returned token', async () => {
    render(<TrustCenter />, { wrapper: TestWrapper });

    fireEvent.click(screen.getByRole('button', { name: /Dry Run Purge/i }));
    fireEvent.change(screen.getByLabelText('Collection'), { target: { value: 'customers' } });
    fireEvent.click(screen.getByTestId('trust-quick-action-submit'));

    await waitFor(() => {
      expect(mockGovernanceService.getRetentionPolicy).toHaveBeenCalledWith('customers');
      expect(mockGovernanceService.purgeRetentionDryRun).toHaveBeenCalledWith({ collection: 'customers' });
      expect(vi.mocked(toast.success)).toHaveBeenCalledWith('Dry run completed for customers');
      expect(screen.getByTestId('trust-purge-preview')).toHaveTextContent(/Estimated rows: 24/i);
    });

    fireEvent.click(screen.getByTestId('trust-quick-action-submit'));

    await waitFor(() => {
      expect(mockGovernanceService.purgeRetentionExecute).toHaveBeenCalledWith({
        collection: 'customers',
        confirmationToken: 'confirm-123',
      });
      expect(vi.mocked(toast.success)).toHaveBeenCalledWith('Retention purge completed for customers');
      expect(screen.getByTestId('trust-action-summary')).toHaveTextContent('Retention purge completed');
    });
  });
});
