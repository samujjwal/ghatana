import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import type { KernelLifecycleClient, LifecycleRun } from '../../api/kernelLifecycleClient';
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
      <p>{snapshot.errorMessage ?? 'no error'}</p>
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
    requestApproval: vi.fn(),
    submitApprovalDecision: vi.fn(),
    ...overrides,
  };
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
});
