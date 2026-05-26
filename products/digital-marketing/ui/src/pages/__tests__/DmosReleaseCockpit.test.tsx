import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { AuthProvider } from '@/context/AuthContext';
import { clearAuthToken, clearRequestContext } from '@/lib/http-client';
import { DmosReleaseCockpit } from '@/pages/DmosReleaseCockpit';

const fetchMock = vi.fn();

function readinessPayload(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    schemaVersion: '1.0',
    productId: 'digital-marketing',
    productName: 'Digital Marketing',
    checkedAt: '2026-05-25T20:00:00Z',
    targetCommitSha: 'bdcee47c1e304454e7af848be60d981b24da1151',
    targetEnvironment: 'production',
    validationStatus: 'passed',
    evidenceFreshness: {
      status: 'current',
      current: true,
      warnings: [],
    },
    dataCloudProviderReadiness: {
      status: 'passed',
      evidenceRef: '.kernel/evidence/data-cloud-platform-provider-readiness.json',
    },
    dataCloudRuntimeProfile: {
      status: 'passed',
      evidenceRef: '.kernel/evidence/data-cloud-release-runtime-profile.json',
    },
    contradictionState: 'NONE',
    releaseReadiness: {
      status: 'ready',
      overallScore: 1,
      blockingIssues: [],
      warnings: [],
    },
    evidenceCategories: {
      persistence: {
        status: 'passed',
        lastChecked: '2026-05-25T20:00:00Z',
        evidenceRefs: ['.kernel/evidence/digital-marketing/database-module-evidence.json'],
      },
      deployment: {
        status: 'passed',
        lastChecked: '2026-05-25T20:00:00Z',
        evidenceRefs: ['.kernel/evidence/digital-marketing/staging-bootstrap.json'],
      },
    },
    gates: {
      release: {
        status: 'passed',
        evidenceRef: '.kernel/evidence/product-release-readiness.digital-marketing.json',
        environment: 'production',
      },
    },
    summary: {
      totalChecks: 2,
      passed: 2,
      partial: 0,
      failed: 0,
      blocked: 0,
      overallStatus: 'passed',
    },
    nextRequiredWork: [],
    connectorReadiness: {
      googleAds: {
        name: 'Google Ads',
        status: 'passed',
        lastChecked: '2026-05-25T20:00:00Z',
        oauthValid: true,
        tokenRefreshWorking: true,
        idempotencyValid: true,
      },
      connectors: {},
      overallStatus: 'passed',
    },
    rollbackStatus: {
      staging: {
        hasEvidence: true,
        lastRollbackTest: '2026-05-25T20:00:00Z',
        campaignStatePreserved: true,
      },
      production: {
        hasEvidence: true,
        lastRollbackTest: '2026-05-25T20:00:00Z',
        campaignStatePreserved: true,
      },
      overallStatus: 'passed',
    },
    ...overrides,
  };
}

function renderCockpit(payload: Record<string, unknown>): void {
  fetchMock.mockResolvedValueOnce(
    new Response(JSON.stringify(payload), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  );

  render(
    <AuthProvider
      initialToken="test-token"
      initialWorkspaceId="ws-1"
      initialTenantId="tenant-1"
      initialPrincipalId="user-1"
      initialSessionId="session-1"
      initialRoles={['brand-manager']}
    >
      <DmosReleaseCockpit />
    </AuthProvider>,
  );
}

describe('DmosReleaseCockpit', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    clearAuthToken();
    clearRequestContext();
  });

  it('fetches release readiness with tenant and principal context', async () => {
    renderCockpit(readinessPayload());

    expect(await screen.findByText(/Overall Status: ready/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/v1/workspaces/ws-1/release-readiness?environment=production',
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: 'Bearer test-token',
            'X-Tenant-ID': 'tenant-1',
            'X-Principal-ID': 'user-1',
            'X-Session-ID': 'session-1',
          }),
        }),
      );
    });
  });

  it('blocks readiness when backend runtime truth sections are missing', async () => {
    renderCockpit(
      readinessPayload({
        dataCloudRuntimeProfile: undefined,
        connectorReadiness: undefined,
        rollbackStatus: undefined,
        evidenceCategories: {},
      }),
    );

    expect(await screen.findByText('Runtime truth blocked')).toBeInTheDocument();
    expect(screen.getByText('Connector proof: missing')).toBeInTheDocument();
    expect(screen.getByText('Persistence proof: missing')).toBeInTheDocument();
    expect(screen.getByText('Rollback proof: missing')).toBeInTheDocument();
    expect(screen.getByText(/Google Ads connector readiness missing/i)).toBeInTheDocument();
    expect(screen.getByText(/staging and production rollback evidence missing/i)).toBeInTheDocument();
  });
});
