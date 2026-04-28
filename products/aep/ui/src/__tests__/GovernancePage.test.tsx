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

vi.mock('@/api/aep.api');
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
    vi.mocked(aepApi.listPolicies).mockResolvedValue([]);
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
});
