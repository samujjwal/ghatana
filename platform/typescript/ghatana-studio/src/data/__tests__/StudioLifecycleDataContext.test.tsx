import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import type { KernelLifecycleClient, LifecycleRun } from '../../api/kernelLifecycleClient';
import { yappcProductUnitIntentCandidate } from '../../routes/yappcWorkflowData';
import {
  StudioLifecycleDataProvider,
  useStudioLifecycleData,
} from '../StudioLifecycleDataContext';

const productUnit = {
  schemaVersion: '1.0.0',
  id: 'digital-marketing',
  name: 'Digital Marketing',
  kind: 'business-product',
  owner: 'digital-marketing-team',
  lifecycleStatus: 'enabled',
  registryProviderRef: { providerId: 'registry' },
  sourceProviderRef: { providerId: 'source' },
  conformance: {
    level: 'standard',
    requiredChecks: ['security'],
  },
  surfaces: [
    {
      id: 'digital-marketing-web',
      type: 'web',
      implementationStatus: 'implemented',
      packagePath: 'products/digital-marketing/ui',
    },
  ],
} as const;

const lifecycleRun: LifecycleRun = {
  runId: 'run-1',
  correlationId: 'corr-1',
  productUnitId: 'digital-marketing',
  phase: 'build',
  status: 'healthy',
  approvalRefs: ['approval-1'],
  manifestRefs: {
    pluginHealth: 'plugin-health.json',
  },
};

function SnapshotProbe(): ReactElement {
  const lifecycle = useStudioLifecycleData();
  const snapshot = lifecycle.snapshot;

  return (
    <div>
      <p>{snapshot.status}</p>
      <p>{snapshot.productUnit?.name ?? 'no product'}</p>
      <p>{snapshot.selectedRun?.runId ?? 'no run'}</p>
      <p>{snapshot.artifactManifest?.artifacts[0]?.id ?? 'no artifact'}</p>
      <p>{snapshot.manifestLoadState.artifactManifest.status}</p>
      <p>{String(snapshot.pendingApprovals.length)}</p>
      <p>{snapshot.errorMessage ?? 'no error'}</p>
    </div>
  );
}

function ActionProbe(): ReactElement {
  const lifecycle = useStudioLifecycleData();

  return (
    <div>
      <p>{lifecycle.selectedProviderMode}</p>
      <button
        type="button"
        onClick={() => {
          lifecycle.setEnvironment('local');
          lifecycle.setProviderMode('platform');
        }}
      >
        configure-provider
      </button>
      <button
        type="button"
        onClick={() => {
          void lifecycle.createPlan('build');
        }}
      >
        run-create-plan
      </button>
    </div>
  );
}

function IntentOperationProbe(): ReactElement {
  const lifecycle = useStudioLifecycleData();

  return (
    <div>
      <p data-testid="intent-status">{lifecycle.intentOperation.status}</p>
      <p data-testid="intent-mode">{lifecycle.intentOperation.mode ?? 'none'}</p>
      <p data-testid="intent-correlation">{lifecycle.intentOperation.correlationId ?? 'none'}</p>
      <p data-testid="intent-error">{lifecycle.intentOperation.errorMessage ?? 'none'}</p>
      <button
        type="button"
        onClick={() => {
          void lifecycle.previewProductUnitIntent?.(yappcProductUnitIntentCandidate, {
            providerMode: 'bootstrap',
          }).catch(() => undefined);
        }}
      >
        preview-intent
      </button>
      <button
        type="button"
        onClick={() => {
          void lifecycle.applyProductUnitIntent?.(yappcProductUnitIntentCandidate, {
            providerMode: 'platform',
          }).catch(() => undefined);
        }}
      >
        apply-intent
      </button>
    </div>
  );
}

interface Deferred<T> {
  readonly promise: Promise<T>;
  readonly resolve: (value: T) => void;
  readonly reject: (reason: Error) => void;
}

function createDeferred<T>(): Deferred<T> {
  let resolveValue: ((value: T) => void) | undefined;
  let rejectValue: ((reason: Error) => void) | undefined;
  const promise = new Promise<T>((resolve, reject) => {
    resolveValue = resolve;
    rejectValue = reject;
  });

  if (resolveValue === undefined || rejectValue === undefined) {
    throw new Error('Deferred promise handlers were not initialized.');
  }

  return {
    promise,
    resolve: resolveValue,
    reject: rejectValue,
  };
}

function createClient(overrides: Partial<KernelLifecycleClient> = {}): KernelLifecycleClient {
  return {
    listProductUnits: vi.fn().mockResolvedValue([productUnit]),
    getProductUnit: vi.fn().mockResolvedValue(productUnit),
    createLifecyclePlan: vi.fn(),
    executeLifecyclePhase: vi.fn().mockResolvedValue(lifecycleRun),
    getLifecycleRun: vi.fn().mockResolvedValue(lifecycleRun),
    listLifecycleRuns: vi.fn().mockResolvedValue([lifecycleRun]),
    getGateResultManifest: vi.fn().mockResolvedValue({
      schemaVersion: '1.0.0',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      gates: [{ gateId: 'security', status: 'passed', required: true }],
    }),
    getArtifactManifest: vi.fn().mockResolvedValue({
      schemaVersion: '1.0.0',
      productId: 'digital-marketing',
      productUnitId: 'digital-marketing',
      providerMode: 'platform',
      phase: 'build',
      timestamp: '2026-05-14T00:00:00.000Z',
      artifacts: [
        {
          id: 'web-dist',
          path: 'dist',
          metadata: {
            type: 'static-web-bundle',
            packaging: 'static-files',
            version: '1.0.0',
            buildNumber: '1',
            timestamp: '2026-05-14T00:00:00.000Z',
            sizeBytes: 2048,
          },
          fingerprint: { algorithm: 'sha256', hash: 'a'.repeat(64) },
          expected: true,
          found: true,
        },
      ],
    }),
    getDeploymentManifest: vi.fn().mockResolvedValue({
      schemaVersion: '1.0.0',
      productId: 'digital-marketing',
      version: '1.0.0',
      environment: 'local',
      environmentSafety: 'local',
      deploymentId: 'deploy-1',
      target: 'compose-local',
      surfaces: [],
      deployedAt: '2026-05-14T00:00:00.000Z',
      rollbackPlan: {
        strategy: 'previous-artifact',
        targetVersion: '0.9.0',
        reason: 'Rollback',
        steps: ['restore previous artifact'],
      },
      services: {
        api: { status: 'running', healthCheckPassed: true },
      },
    }),
    getVerifyHealthReport: vi.fn().mockResolvedValue({
      schemaVersion: '1.0.0',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      status: 'healthy',
      checkedAt: '2026-05-14T00:00:00.000Z',
    }),
    getLifecycleRunSummary: vi.fn().mockResolvedValue({
      runId: 'run-1',
      productUnitId: 'digital-marketing',
      phase: 'verify',
      status: 'healthy',
    }),
    listPendingApprovals: vi.fn().mockResolvedValue([
      {
        approvalId: 'approval-1',
        productUnitId: 'digital-marketing',
        runId: 'run-1',
        requestedBy: 'release-manager',
        requestedAt: '2026-05-14T00:00:00.000Z',
        reason: 'Deploy',
        requiredApprovers: ['alice'],
        expiresAt: '2026-05-14T00:00:00.000Z',
      },
    ]),
    requestApproval: vi.fn(),
    submitApprovalDecision: vi.fn(),
    ...overrides,
  } as unknown as KernelLifecycleClient;
}

describe('StudioLifecycleDataProvider', () => {
  it('exposes an explicit unconfigured state without network access', () => {
    render(
      <StudioLifecycleDataProvider>
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    expect(screen.getByText('unconfigured')).toBeInTheDocument();
    expect(screen.getByText('no product')).toBeInTheDocument();
  });

  it('loads ProductUnit, run, and manifests through the typed Kernel client', async () => {
    render(
      <StudioLifecycleDataProvider client={createClient()}>
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    await waitFor(() => expect(screen.getByText('ready')).toBeInTheDocument());
    expect(screen.getByText('Digital Marketing')).toBeInTheDocument();
    expect(screen.getByText('run-1')).toBeInTheDocument();
    expect(screen.getByText('web-dist')).toBeInTheDocument();
    expect(screen.getByText('loaded')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('propagates selected provider mode and environment into plan creation', async () => {
    const createLifecyclePlan = vi.fn().mockResolvedValue({
      runId: 'run-plan',
      correlationId: 'corr-plan',
      productUnitId: 'digital-marketing',
      phase: 'build',
      status: 'planned',
    });

    render(
      <StudioLifecycleDataProvider
        client={createClient({
          createLifecyclePlan,
          listLifecycleRuns: vi.fn().mockResolvedValue([]),
        })}
      >
        <ActionProbe />
      </StudioLifecycleDataProvider>,
    );

    await waitFor(() => expect(screen.getByText('configure-provider')).toBeInTheDocument());
    fireEvent.click(screen.getByText('configure-provider'));
    await waitFor(() => expect(screen.getByText('platform')).toBeInTheDocument());
    fireEvent.click(screen.getByText('run-create-plan'));

    await waitFor(() =>
      expect(createLifecyclePlan).toHaveBeenCalledWith('digital-marketing', 'build', {
        providerMode: 'platform',
        environment: 'local',
      }),
    );
  });

  it('supports product selection and ready state without a run manifest', async () => {
    const otherProduct = {
      ...productUnit,
      id: 'finance',
      name: 'Finance',
      surfaces: [],
    };

    render(
      <StudioLifecycleDataProvider
        client={createClient({
          listProductUnits: vi.fn().mockResolvedValue([productUnit, otherProduct]),
          listLifecycleRuns: vi.fn().mockResolvedValue([]),
        })}
        productUnitId="finance"
      >
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    await waitFor(() => expect(screen.getByText('ready')).toBeInTheDocument());
    expect(screen.getByText('Finance')).toBeInTheDocument();
    expect(screen.getByText('no run')).toBeInTheDocument();
    expect(screen.getByText('no artifact')).toBeInTheDocument();
  });

  it('surfaces degraded state when Kernel returns no ProductUnits', async () => {
    render(
      <StudioLifecycleDataProvider
        client={createClient({
          listProductUnits: vi.fn().mockResolvedValue([]),
        })}
      >
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    await waitFor(() => expect(screen.getByText('degraded')).toBeInTheDocument());
    expect(screen.getByText('Kernel returned no ProductUnits for Studio.')).toBeInTheDocument();
  });

  it('surfaces degraded state when Kernel data loading fails', async () => {
    render(
      <StudioLifecycleDataProvider
        client={createClient({
          listProductUnits: vi.fn().mockRejectedValue(new Error('provider unavailable')),
        })}
      >
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    await waitFor(() => expect(screen.getByText('degraded')).toBeInTheDocument());
    expect(screen.getByText('provider unavailable')).toBeInTheDocument();
  });

  it('classifies manifest load errors instead of swallowing them', async () => {
    render(
      <StudioLifecycleDataProvider
        client={createClient({
          getArtifactManifest: vi.fn().mockRejectedValue({ statusCode: 401, message: 'unauthorized' }),
        })}
      >
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    await waitFor(() => expect(screen.getByText('ready')).toBeInTheDocument());
    expect(screen.getByText('unauthorized')).toBeInTheDocument();
  });

  it('does not commit empty ProductUnit state after unmount', async () => {
    const deferred = createDeferred<readonly []>();
    const listProductUnits = vi.fn().mockReturnValue(deferred.promise);

    const { unmount } = render(
      <StudioLifecycleDataProvider client={createClient({ listProductUnits })}>
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    unmount();
    deferred.resolve([]);
    await expect(deferred.promise).resolves.toEqual([]);
  });

  it('does not commit loaded run state after unmount', async () => {
    const deferredRuns = createDeferred<readonly LifecycleRun[]>();
    const client = createClient({
      listLifecycleRuns: vi.fn().mockReturnValue(deferredRuns.promise),
    });

    const { unmount } = render(
      <StudioLifecycleDataProvider client={client}>
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    await waitFor(() => expect(client.listLifecycleRuns).toHaveBeenCalledWith('digital-marketing'));
    unmount();
    deferredRuns.resolve([]);
    await expect(deferredRuns.promise).resolves.toEqual([]);
  });

  it('does not commit loaded manifest state after unmount', async () => {
    const deferredGate = createDeferred<{
      readonly schemaVersion: '1.0.0';
      readonly productUnitId: 'digital-marketing';
      readonly runId: 'run-1';
      readonly gates: readonly [];
    }>();
    const client = createClient({
      getGateResultManifest: vi.fn().mockReturnValue(deferredGate.promise),
    });

    const { unmount } = render(
      <StudioLifecycleDataProvider client={client}>
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    await waitFor(() => expect(client.getGateResultManifest).toHaveBeenCalled());
    unmount();
    deferredGate.resolve({
      schemaVersion: '1.0.0',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      gates: [],
    });
    await expect(deferredGate.promise).resolves.toMatchObject({ runId: 'run-1' });
  });

  it('does not commit degraded state after unmount', async () => {
    const deferred = createDeferred<readonly []>();
    const listProductUnits = vi.fn().mockReturnValue(deferred.promise);

    const { unmount } = render(
      <StudioLifecycleDataProvider client={createClient({ listProductUnits })}>
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    unmount();
    deferred.reject(new Error('provider unavailable'));
    await expect(deferred.promise).rejects.toThrow('provider unavailable');
  });

  it('uses a safe degraded message for non-Error rejections', async () => {
    render(
      <StudioLifecycleDataProvider
        client={createClient({
          listProductUnits: vi.fn().mockRejectedValue('untyped failure'),
        })}
      >
        <SnapshotProbe />
      </StudioLifecycleDataProvider>,
    );

    await waitFor(() => expect(screen.getByText('degraded')).toBeInTheDocument());
    expect(screen.getByText('Kernel lifecycle data failed to load.')).toBeInTheDocument();
  });

  it('tracks preview intent operation state from loading to success', async () => {
    const previewDeferred = createDeferred<{
      readonly schemaVersion: '1.0.0';
      readonly intentId: string;
      readonly status: 'previewed';
      readonly productUnitId: string;
      readonly correlationId: string;
      readonly providerMode: 'bootstrap';
      readonly registryProviderId: string;
      readonly sourceProviderId: string;
      readonly lifecycleEventRefs: readonly string[];
      readonly provenanceRefs: readonly string[];
      readonly runtimeTruthRefs: readonly string[];
      readonly blockedReasons: readonly string[];
      readonly errors: readonly string[];
    }>();

    render(
      <StudioLifecycleDataProvider
        client={createClient({
          previewProductUnitIntent: vi.fn().mockReturnValue(previewDeferred.promise),
        })}
      >
        <IntentOperationProbe />
      </StudioLifecycleDataProvider>,
    );

    fireEvent.click(screen.getByText('preview-intent'));

    await waitFor(() => expect(screen.getByTestId('intent-status').textContent).toBe('loading'));
    expect(screen.getByTestId('intent-mode').textContent).toBe('preview');

    previewDeferred.resolve({
      schemaVersion: '1.0.0',
      intentId: yappcProductUnitIntentCandidate.intentId,
      status: 'previewed',
      productUnitId: yappcProductUnitIntentCandidate.productUnit.id,
      correlationId: 'corr-preview-1',
      providerMode: 'bootstrap',
      registryProviderId: 'kernel-product-registry',
      sourceProviderId: 'yappc-creator',
      lifecycleEventRefs: [],
      provenanceRefs: [],
      runtimeTruthRefs: [],
      blockedReasons: [],
      errors: [],
    });

    await waitFor(() => expect(screen.getByTestId('intent-status').textContent).toBe('success'));
    expect(screen.getByTestId('intent-mode').textContent).toBe('preview');
    expect(screen.getByTestId('intent-correlation').textContent).toBe('corr-preview-1');
  });

  it('captures explicit error state when preview ProductUnitIntent is unsupported', async () => {
    render(
      <StudioLifecycleDataProvider client={createClient()}>
        <IntentOperationProbe />
      </StudioLifecycleDataProvider>,
    );

    fireEvent.click(screen.getByText('preview-intent'));

    await waitFor(() => expect(screen.getByTestId('intent-status').textContent).toBe('error'));
    expect(screen.getByTestId('intent-mode').textContent).toBe('preview');
    expect(screen.getByTestId('intent-error').textContent).toBe(
      'Kernel lifecycle client does not support ProductUnitIntent preview',
    );
  });

  it('captures apply ProductUnitIntent failures with surfaced error messages', async () => {
    render(
      <StudioLifecycleDataProvider
        client={createClient({
          applyProductUnitIntent: vi.fn().mockRejectedValue(new Error('apply denied by policy')),
        })}
      >
        <IntentOperationProbe />
      </StudioLifecycleDataProvider>,
    );

    fireEvent.click(screen.getByText('apply-intent'));

    await waitFor(() => expect(screen.getByTestId('intent-status').textContent).toBe('error'));
    expect(screen.getByTestId('intent-mode').textContent).toBe('apply');
    expect(screen.getByTestId('intent-error').textContent).toBe('apply denied by policy');
  });
});
