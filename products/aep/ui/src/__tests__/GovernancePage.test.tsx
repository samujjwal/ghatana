/**
 * GovernancePage coverage for consent and operations surfaces.
 *
 * @doc.type test
 * @doc.purpose Ensure governance consent dashboard and ops summary render truthful data
 * @doc.layer frontend
 */
import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GovernancePage } from '@/pages/GovernancePage';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';
import * as aepApi from '@/api/aep.api';
import { useAuth } from '@/context/AuthContext';

vi.mock('@/api/aep.api');
vi.mock('@/context/AuthContext', () => ({
  useAuth: vi.fn(),
}));
vi.mock('@/lib/feature-flags', () => ({
  isFeatureEnabled: () => true,
  featureFlags: {
    COMPLIANCE_REPORTS: true,
    TENANT_MANAGEMENT: true,
    AUDIT_LOG: true,
  },
}));

function renderWithQuery(ui: React.ReactElement) {
  return render(ui, { wrapper: createAepTestWrapper() });
}

describe('GovernancePage', () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue({
      authToken: 'jwt-token',
      sessionToken: 'session-token',
      isAuthenticated: true,
      isBootstrappingSession: false,
      isVerifyingAuth: false,
      roles: ['admin'],
      hasRole: () => true,
      hasAnyRole: () => true,
      loginWithToken: vi.fn(),
      loginWithPlatform: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(aepApi.listPolicies).mockResolvedValue([]);
    vi.mocked(aepApi.activateGovernanceKillSwitch).mockResolvedValue({
      activated: true,
      tenantId: 'default',
      incidentId: 'INC-42',
      auditId: 'audit-1',
    });
    vi.mocked(aepApi.deactivateGovernanceKillSwitch).mockResolvedValue({
      deactivated: true,
      tenantId: 'default',
      auditId: 'audit-2',
    });
    vi.mocked(aepApi.getGovernanceComplianceSummary).mockResolvedValue({
      tenantId: 'default',
      configured: true,
      supportedOperations: ['gdpr_access'],
      registeredCollections: ['dc_memory'],
      soc2: {
        title: 'SOC 2 Snapshot',
        generatedAt: new Date().toISOString(),
        overallStatus: 'PASS',
        controlCount: 1,
        freshness: {
          status: 'STALE',
          reportAvailable: false,
          newestEvidenceAt: '2026-01-01T00:00:00.000Z',
          evidenceAgeDays: 117,
          maxAgeDays: 90,
          message: 'SOC 2 evidence is 117 days old; the configured maximum is 90 days.',
        },
        controls: [{ controlId: 'CC1', description: 'Control', status: 'PASS' }],
      },
      timestamp: new Date().toISOString(),
    });
    vi.mocked(aepApi.getGovernanceTenancySummary).mockResolvedValue({
      tenantId: 'default',
      active: false,
      globalActive: false,
      mode: 'NORMAL',
    });
    vi.mocked(aepApi.getGovernanceAuditSummary).mockResolvedValue({
      tenantId: 'default',
      configured: true,
      entries: [],
      count: 0,
      timestamp: new Date().toISOString(),
    });
    vi.mocked(aepApi.listConsentDecisions).mockResolvedValue({
      tenantId: 'default',
      count: 2,
      items: [
        {
          consentId: 'consent-1',
          userId: 'sam',
          status: 'granted',
          purposes: ['voice_processing'],
          decidedAt: '2026-04-27T10:00:00.000Z',
        },
        {
          consentId: 'consent-2',
          userId: 'alex',
          status: 'withdrawn',
          purposes: [],
          decidedAt: '2026-04-27T11:00:00.000Z',
        },
      ],
    });
    vi.mocked(aepApi.getGovernanceOpsSummary).mockResolvedValue({
      tenantId: 'default',
      backupConfigured: true,
      backupCount: 4,
      lastBackupAt: '2026-04-27T09:00:00.000Z',
      latestBackupStatus: 'COMPLETE',
      drReadiness: 'PASS',
      lastDrDrillAt: null,
      exportQueueConfigured: false,
      exportQueueDepth: null,
      automatedBackupsScheduled: false,
      trustedProxyForwardedAcceptedCount: 4,
      trustedProxyForwardedRejectedCount: 2,
      trustedProxyAlertState: 'ALERT',
      trustedProxyRejectionReasons: {
        untrusted_proxy: 1,
        invalid_forwarded_for: 1,
      },
      notes: ['Historical DR drill timestamps are not yet persisted.'],
      timestamp: '2026-04-27T09:05:00.000Z',
    });

    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:consent-export'),
      revokeObjectURL: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  it('renders consent history and exports the filtered view', async () => {
    const user = userEvent.setup();
    renderWithQuery(<GovernancePage />);

    await user.click(screen.getByRole('button', { name: /consent/i }));
    await waitFor(() => expect(screen.getByText('Consent change history')).toBeInTheDocument());
    expect(screen.getByText('sam')).toBeInTheDocument();
    expect(screen.getByText('voice_processing')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'withdrawn' }));
    expect(screen.queryByText('sam')).not.toBeInTheDocument();
    expect(screen.getByText('alex')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /export csv/i }));
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('surfaces stale SOC2 evidence truthfully in compliance', async () => {
    const user = userEvent.setup();
    renderWithQuery(<GovernancePage />);

    await user.click(screen.getByRole('button', { name: /compliance/i }));

    await waitFor(() => expect(screen.getByText(/SOC 2 report availability/i)).toBeInTheDocument());
    expect(screen.getByText(/Blocked until evidence is refreshed/i)).toBeInTheDocument();
    expect(screen.getByText(/117 days old/i)).toBeInTheDocument();
    expect(screen.getByText('117')).toBeInTheDocument();
  });

  it('renders operations telemetry with truthful unavailable states', async () => {
    const user = userEvent.setup();
    renderWithQuery(<GovernancePage />);

    await user.click(screen.getByRole('button', { name: /operations/i }));
    await waitFor(() => expect(screen.getByText('Backup and DR posture')).toBeInTheDocument());
    expect(screen.getByText('PASS')).toBeInTheDocument();
    expect(screen.getByText(/State: ALERT/i)).toBeInTheDocument();
    expect(screen.getByText(/untrusted_proxy: 1/i)).toBeInTheDocument();
    expect(screen.getAllByText('Unavailable').length).toBeGreaterThan(0);
    expect(screen.getByText(/Historical DR drill timestamps are not yet persisted/i)).toBeInTheDocument();
  });

  it('surfaces policy promotion advisor and timeline details', async () => {
    const user = userEvent.setup();
    vi.mocked(aepApi.listPolicies).mockResolvedValue([
      {
        id: 'policy-1',
        tenantId: 'default',
        skillId: 'email-routing',
        name: 'Policy proposal for email-routing',
        description: 'Candidate policy derived from 24 episodes.',
        status: 'PENDING_REVIEW',
        confidenceScore: 0.91,
        episodeCount: 24,
        version: 2,
        createdAt: '2026-04-28T09:00:00.000Z',
        updatedAt: '2026-04-28T09:00:00.000Z',
        reviewId: 'review-1',
        autoPromotable: true,
        autoPromoted: false,
        provenance: {
          policyId: 'policy-1',
          skillId: 'email-routing',
          version: 2,
          sourceEpisodeIds: ['ep-1', 'ep-2'],
          evaluationMetrics: {
            successRate: 0.92,
            errorRate: 0.03,
          },
          activationMode: 'SHADOW',
          rollbackPointerId: 'policy-0',
        },
        gateResult: {
          gateName: 'composite-eval',
          passed: true,
          score: 0.91,
          threshold: 0.7,
          reason: 'Confidence clears the review threshold.',
        },
      },
      {
        id: 'policy-0',
        tenantId: 'default',
        skillId: 'email-routing',
        name: 'Policy proposal for email-routing',
        description: 'Previous active policy.',
        status: 'APPROVED',
        confidenceScore: 0.84,
        episodeCount: 18,
        version: 1,
        createdAt: '2026-04-20T09:00:00.000Z',
        updatedAt: '2026-04-20T10:00:00.000Z',
        autoPromotable: false,
        autoPromoted: true,
        decidedAt: '2026-04-20T10:00:00.000Z',
        reviewerId: 'auto-promote',
        reviewerRationale: 'Previous high-confidence rollout.',
        provenance: {
          policyId: 'policy-0',
          skillId: 'email-routing',
          version: 1,
          sourceEpisodeIds: ['ep-prev'],
          evaluationMetrics: {
            successRate: 0.84,
          },
          activationMode: 'ACTIVE',
          approverId: 'auto-promote',
          promotedAt: '2026-04-20T10:00:00.000Z',
        },
      },
    ]);

    renderWithQuery(<GovernancePage />);

    await waitFor(() => expect(screen.getAllByText('Auto-promotable').length).toBeGreaterThan(0));
    expect(screen.getAllByText('Hybrid promotion advisor').length).toBeGreaterThan(0);
    expect(screen.getByText('Policy timeline for email-routing')).toBeInTheDocument();
    expect(screen.getAllByText('policy-0').length).toBeGreaterThan(0);

    await user.click(screen.getAllByRole('button', { name: /details/i })[0]);
    expect(screen.getByText('Rollback target')).toBeInTheDocument();
    expect(screen.getAllByText('policy-0').length).toBeGreaterThan(0);
    expect(screen.getByText('Confidence clears the review threshold.')).toBeInTheDocument();
  });

  it('shows kill-switch impact preview and submits activation controls', async () => {
    const user = userEvent.setup();
    renderWithQuery(<GovernancePage />);

    await user.click(screen.getByRole('button', { name: /tenancy/i }));
    await waitFor(() => expect(screen.getByText('Kill-switch impact preview')).toBeInTheDocument());
    expect(screen.getByText(/Activation will pause normal execution for the tenant/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText(/reason/i), 'Suspected tenant compromise');
    await user.type(screen.getByLabelText(/incident id/i), 'INC-42');
    await user.type(screen.getByLabelText(/mfa code/i), '123456');
    await user.click(screen.getByRole('button', { name: /^Activate kill-switch$/i }));

    await waitFor(() => expect(aepApi.activateGovernanceKillSwitch).toHaveBeenCalled());
    expect(await screen.findByText(/Kill-switch activated with audit audit-1/i)).toBeInTheDocument();
  });
});
