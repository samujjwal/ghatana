import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import LifecyclePage from '../LifecyclePage';

const useStudioLifecycleDataMock = vi.fn<() => StudioLifecycleDataContextValue>();
const useStudioTranslationMock = vi.fn<() => (key: string) => string>();

vi.mock('../../data/StudioLifecycleDataContext', () => ({
  useStudioLifecycleData: () => useStudioLifecycleDataMock(),
}));

vi.mock('../../i18n/studioTranslations', () => ({
  useStudioTranslation: () => useStudioTranslationMock(),
}));

function createContextValue(overrides: Partial<StudioLifecycleDataContextValue> = {}): StudioLifecycleDataContextValue {
  return {
    snapshot: {
      status: 'ready',
      availableProductUnits: [
        {
          schemaVersion: '1.0.0',
          id: 'digital-marketing',
          name: 'Digital Marketing',
          kind: 'business-product',
          registryProviderRef: { providerId: 'registry' },
          sourceProviderRef: { providerId: 'source' },
          metadata: { environments: ['local'] },
          surfaces: [{ id: 'web', type: 'web', implementationStatus: 'implemented' }],
        },
      ],
      productUnit: {
        schemaVersion: '1.0.0',
        id: 'digital-marketing',
        name: 'Digital Marketing',
        kind: 'business-product',
        registryProviderRef: { providerId: 'registry' },
        sourceProviderRef: { providerId: 'source' },
        metadata: { environments: ['local'] },
        surfaces: [{ id: 'web', type: 'web', implementationStatus: 'implemented' }],
      },
      lifecycleRuns: [
        {
          runId: 'run-1',
          correlationId: 'corr-1',
          productUnitId: 'digital-marketing',
          phase: 'build',
          status: 'healthy',
        },
      ],
      selectedRun: {
        runId: 'run-1',
        correlationId: 'corr-1',
        productUnitId: 'digital-marketing',
        phase: 'build',
        status: 'healthy',
      },
      pendingApprovals: [],
      manifestLoadState: {
        gateResultManifest: { status: 'missing' },
        artifactManifest: { status: 'missing' },
        deploymentManifest: { status: 'missing' },
        verifyHealthReport: { status: 'missing' },
      },
    },
    selectedProductUnitId: 'digital-marketing',
    selectedRunId: 'run-1',
    selectedEnvironment: 'local',
    selectedProviderMode: 'bootstrap',
    selectProductUnit: vi.fn(),
    selectRun: vi.fn(),
    setEnvironment: vi.fn(),
    setProviderMode: vi.fn(),
    createPlan: vi.fn(),
    executePhase: vi.fn(),
    requestApproval: vi.fn(),
    submitApprovalDecision: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe('LifecyclePage approval queue', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('renders no pending approvals message', () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<LifecyclePage />);

    expect(screen.getByText('studio.route.lifecycle.noPendingApprovals')).toBeInTheDocument();
  });

  it('submits approval decisions from the queue', async () => {
    const submitApprovalDecision = vi.fn().mockResolvedValue(undefined);
    const refresh = vi.fn().mockResolvedValue(undefined);
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        submitApprovalDecision,
        refresh,
        snapshot: {
          ...createContextValue().snapshot,
          pendingApprovals: [
            {
              approvalId: 'approval-1',
              productUnitId: 'digital-marketing',
              runId: 'run-1',
              requestedBy: 'release-manager',
              reason: 'Deploy',
              requiredApprovers: ['alice'],
              expiresAt: '2026-05-16T00:00:00.000Z',
            },
          ],
        },
      }),
    );

    render(<LifecyclePage />);

    fireEvent.click(screen.getByRole('button', { name: 'studio.lifecycle.action.submitApproval' }));

    await waitFor(() => {
      expect(submitApprovalDecision).toHaveBeenCalledWith(
        'approval-1',
        expect.objectContaining({
          approvalId: 'approval-1',
          approved: true,
          approvedBy: 'studio-operator',
        }),
      );
    });

    fireEvent.click(screen.getByRole('button', { name: 'studio.lifecycle.action.rejectApproval' }));

    await waitFor(() => {
      expect(submitApprovalDecision).toHaveBeenCalledWith(
        'approval-1',
        expect.objectContaining({
          approvalId: 'approval-1',
          approved: false,
          approvedBy: 'studio-operator',
        }),
      );
      expect(refresh).toHaveBeenCalled();
    });
  });

  it('renders typed manifest evidence summaries', () => {
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        snapshot: {
          ...createContextValue().snapshot,
          gateResultManifest: {
            schemaVersion: '1.0.0',
            productUnitId: 'digital-marketing',
            runId: 'run-1',
            gates: [
              {
                gateId: 'security-scan',
                status: 'passed',
                required: true,
              },
            ],
          },
          artifactManifest: {
            schemaVersion: '1.0.0',
            productId: 'digital-marketing',
            phase: 'build',
            timestamp: '2026-05-16T00:00:00.000Z',
            artifacts: [
              {
                id: 'web-dist',
                path: 'dist',
                metadata: {
                  type: 'static-web-bundle',
                  packaging: 'static-files',
                  version: '1.0.0',
                  buildNumber: '1',
                  timestamp: '2026-05-16T00:00:00.000Z',
                  sizeBytes: 1024,
                },
                fingerprint: {
                  algorithm: 'sha256',
                  hash: 'a'.repeat(64),
                },
                expected: true,
                found: true,
              },
            ],
          },
          deploymentManifest: {
            schemaVersion: '1.0.0',
            productId: 'digital-marketing',
            version: '1.0.0',
            environment: 'local',
            deploymentId: 'deploy-1',
            deployedAt: '2026-05-16T00:00:00.000Z',
            rollbackPlan: {
              strategy: 'previous-artifact',
              targetVersion: '0.9.0',
              reason: 'Rollback',
              steps: ['restore previous artifact'],
            },
            surfaces: [],
            target: 'compose-local',
          },
          verifyHealthReport: {
            schemaVersion: '1.0.0',
            productUnitId: 'digital-marketing',
            runId: 'run-1',
            status: 'healthy',
            checkedAt: '2026-05-16T00:00:00.000Z',
          },
          manifestLoadState: {
            gateResultManifest: { status: 'loaded' },
            artifactManifest: { status: 'loaded' },
            deploymentManifest: { status: 'loaded' },
            verifyHealthReport: { status: 'loaded' },
          },
        },
      }),
    );

    render(<LifecyclePage />);

    expect(screen.getByText('security-scan')).toBeInTheDocument();
    expect(screen.getByText('web-dist')).toBeInTheDocument();
    expect(screen.getByText('Environment: local')).toBeInTheDocument();
    expect(screen.getByText('Status: healthy')).toBeInTheDocument();
  });

  it('shows blocked reason codes for unsupported phase/environment and platform provider mode', () => {
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        selectedEnvironment: 'staging',
        selectedProviderMode: 'platform',
        snapshot: {
          ...createContextValue().snapshot,
          status: 'degraded',
          productUnit: {
            ...createContextValue().snapshot.productUnit!,
            metadata: {
              environments: ['local'],
              phases: ['build'],
            },
          },
        },
      }),
    );

    render(<LifecyclePage />);

    fireEvent.click(screen.getByRole('button', { name: 'Deploy' }));

    expect(screen.getByLabelText('lifecycle-blocked-reasons')).toBeInTheDocument();
    expect(screen.getByText('phase-not-supported')).toBeInTheDocument();
    expect(screen.getByText('environment-not-supported')).toBeInTheDocument();
    expect(screen.getByText('provider-mode-unavailable')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'studio.route.lifecycle.executePhaseButton' })).toBeDisabled();
  });
});
