import {
  createContext,
  type ReactElement,
  type ReactNode,
  useContext,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { ArtifactManifest } from '@ghatana/kernel-artifacts';
import type { DeploymentManifest } from '@ghatana/kernel-deployment';
import type { ProductUnit } from '@ghatana/kernel-product-contracts';
import type {
  ApprovalDecision,
  ApprovalRequest,
  GateResultManifest,
  KernelLifecycleClient,
  LifecyclePlan,
  LifecycleRun,
  ProductLifecyclePhase,
  VerifyHealthReport,
} from '../api/kernelLifecycleClient';
import type { StudioRuntimeContextState } from '../config/studioRuntimeContext';
import { studioLogger } from '../logging/studioLogger';

export type StudioLifecycleDataStatus = 'unconfigured' | 'loading' | 'ready' | 'degraded';

export type StudioManifestLoadStatus =
  | 'loaded'
  | 'missing'
  | 'corrupt'
  | 'unauthorized'
  | 'unavailable';

interface StudioManifestLoadState {
  readonly status: StudioManifestLoadStatus;
  readonly message?: string;
}

interface StudioLifecycleManifestLoadState {
  readonly gateResultManifest: StudioManifestLoadState;
  readonly artifactManifest: StudioManifestLoadState;
  readonly deploymentManifest: StudioManifestLoadState;
  readonly verifyHealthReport: StudioManifestLoadState;
}

export interface StudioLifecycleSnapshot {
  readonly status: StudioLifecycleDataStatus;
  readonly runtimeMode: 'configured' | 'unconfigured';
  readonly productUnit?: ProductUnit;
  readonly availableProductUnits: readonly ProductUnit[];
  readonly lifecycleRuns: readonly LifecycleRun[];
  readonly selectedRun?: LifecycleRun;
  readonly pendingApprovals: readonly ApprovalRequest[];
  readonly manifestLoadState: StudioLifecycleManifestLoadState;
  readonly gateResultManifest?: GateResultManifest;
  readonly artifactManifest?: ArtifactManifest;
  readonly deploymentManifest?: DeploymentManifest;
  readonly verifyHealthReport?: VerifyHealthReport;
  readonly errorMessage?: string;
}

export interface StudioLifecycleDataContextValue {
  readonly snapshot: StudioLifecycleSnapshot;
  readonly selectedProductUnitId: string;
  readonly selectedRunId: string | null;
  readonly selectedEnvironment: string;
  readonly selectedProviderMode: 'bootstrap' | 'platform';
  /** Authenticated user ID resolved from the Studio runtime context. Undefined when Studio is unconfigured. */
  readonly authenticatedUserId: string | undefined;
  selectProductUnit(productUnitId: string): void;
  selectRun(runId: string): void;
  setEnvironment(environment: string): void;
  setProviderMode(providerMode: 'bootstrap' | 'platform'): void;
  createPlan(phase: ProductLifecyclePhase, options?: { dryRun?: boolean; environment?: string }): Promise<void>;
  executePhase(phase: ProductLifecyclePhase, options?: { dryRun?: boolean; environment?: string }): Promise<void>;
  requestApproval(actionRequest: ApprovalRequest): Promise<void>;
  submitApprovalDecision(approvalId: string, decision: ApprovalDecision): Promise<void>;
  refresh(): Promise<void>;
}

interface StudioLifecycleDataProviderProps {
  readonly client?: KernelLifecycleClient;
  readonly runtimeContext?: StudioRuntimeContextState;
  readonly productUnitId?: string;
  readonly children: ReactNode;
}

const EMPTY_SNAPSHOT: StudioLifecycleSnapshot = {
  status: 'unconfigured',
  runtimeMode: 'unconfigured',
  availableProductUnits: [],
  lifecycleRuns: [],
  pendingApprovals: [],
  manifestLoadState: {
    gateResultManifest: { status: 'missing' },
    artifactManifest: { status: 'missing' },
    deploymentManifest: { status: 'missing' },
    verifyHealthReport: { status: 'missing' },
  },
};

const StudioLifecycleDataContext = createContext<StudioLifecycleDataContextValue>({
  snapshot: EMPTY_SNAPSHOT,
  selectedProductUnitId: 'digital-marketing',
  selectedRunId: null,
  selectedEnvironment: 'local',
  selectedProviderMode: 'bootstrap',
  authenticatedUserId: undefined,
  selectProductUnit: () => {},
  selectRun: () => {},
  setEnvironment: () => {},
  setProviderMode: () => {},
  createPlan: async () => {},
  executePhase: async () => {},
  requestApproval: async () => {},
  submitApprovalDecision: async () => {},
  refresh: async () => {},
});

export function StudioLifecycleDataProvider(
  props: StudioLifecycleDataProviderProps,
): ReactElement {
  const { client, runtimeContext, productUnitId: initialProductUnitId = 'digital-marketing', children } = props;
  const authenticatedUserId =
    runtimeContext?.status === 'configured' ? runtimeContext.identity.userId : undefined;
  
  const [selectedProductUnitId, setSelectedProductUnitId] = useState<string>(initialProductUnitId);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [selectedEnvironment, setSelectedEnvironment] = useState<string>('local');
  const [selectedProviderMode, setSelectedProviderMode] = useState<'bootstrap' | 'platform'>('bootstrap');
  const [snapshot, setSnapshot] = useState<StudioLifecycleSnapshot>(EMPTY_SNAPSHOT);
  
  const abortControllerRef = useRef<AbortController | null>(null);
  const pollingIntervalRef = useRef<number | null>(null);

  const loadSnapshot = useCallback(async () => {
    if (client === undefined) {
      studioLogger.error('Studio lifecycle client is unconfigured', {
        status: runtimeContext?.status ?? 'unconfigured',
        missingFields: runtimeContext?.status === 'unconfigured' ? runtimeContext.missingFields : [],
      });
      setSnapshot(EMPTY_SNAPSHOT);
      return;
    }

    const lifecycleClient = client;
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    abortControllerRef.current = new AbortController();
    const signal = abortControllerRef.current.signal;

    setSnapshot({
      status: 'loading',
      runtimeMode: 'configured',
      availableProductUnits: [],
      lifecycleRuns: [],
      pendingApprovals: [],
      manifestLoadState: EMPTY_SNAPSHOT.manifestLoadState,
    });

    try {
      const productUnits = await lifecycleClient.listProductUnits();
      const selectedProductUnit =
        productUnits.find((candidate: ProductUnit) => candidate.id === selectedProductUnitId) ??
        productUnits[0];

      if (selectedProductUnit === undefined) {
        if (!signal.aborted) {
          setSnapshot({
            status: 'degraded',
            runtimeMode: 'configured',
            availableProductUnits: [],
            lifecycleRuns: [],
            pendingApprovals: [],
            manifestLoadState: EMPTY_SNAPSHOT.manifestLoadState,
            errorMessage: 'Kernel returned no ProductUnits for Studio.',
          });
        }
        return;
      }

      const lifecycleRuns = await lifecycleClient.listLifecycleRuns(selectedProductUnit.id);
      const selectedRun = selectedRunId
        ? lifecycleRuns.find((run) => run.runId === selectedRunId)
        : lifecycleRuns[0];
      const manifests =
        selectedRun === undefined
          ? undefined
          : await loadRunManifests(lifecycleClient, selectedProductUnit.id, selectedRun.runId, signal);
      const pendingApprovals = await lifecycleClient.listPendingApprovals({
        productUnitId: selectedProductUnit.id,
        ...(selectedRun?.runId === undefined ? {} : { runId: selectedRun.runId }),
      });

      if (!signal.aborted) {
        setSnapshot({
          status: 'ready',
          runtimeMode: 'configured',
          productUnit: selectedProductUnit,
          availableProductUnits: productUnits,
          lifecycleRuns,
          selectedRun,
          pendingApprovals,
          manifestLoadState: manifests?.manifestLoadState ?? EMPTY_SNAPSHOT.manifestLoadState,
          ...(manifests ?? {}),
        });
      }

      // Start polling if run is in a running/pending state
      if (selectedRun?.status === 'running' || selectedRun?.status === 'pending approval') {
        if (pollingIntervalRef.current) {
          clearInterval(pollingIntervalRef.current);
        }
        pollingIntervalRef.current = setInterval(() => {
          loadSnapshot();
        }, 5000);
      } else if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
        pollingIntervalRef.current = null;
      }
    } catch (error: unknown) {
      if (!signal.aborted) {
        setSnapshot({
          status: 'degraded',
          runtimeMode: 'configured',
          availableProductUnits: [],
          lifecycleRuns: [],
          pendingApprovals: [],
          manifestLoadState: EMPTY_SNAPSHOT.manifestLoadState,
          errorMessage: error instanceof Error ? error.message : 'Kernel lifecycle data failed to load.',
        });
      }
    }
  }, [client, selectedProductUnitId, selectedRunId, runtimeContext]);

  useEffect(() => {
    void loadSnapshot();

    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
      }
    };
  }, [loadSnapshot]);

  const selectProductUnit = useCallback((productUnitId: string) => {
    setSelectedProductUnitId(productUnitId);
    setSelectedRunId(null);
  }, []);

  const selectRun = useCallback((runId: string) => {
    setSelectedRunId(runId);
  }, []);

  const setEnvironment = useCallback((environment: string) => {
    setSelectedEnvironment(environment);
  }, []);

  const setProviderMode = useCallback((providerMode: 'bootstrap' | 'platform') => {
    setSelectedProviderMode(providerMode);
  }, []);

  const createPlan = useCallback(async (phase: ProductLifecyclePhase, options?: { dryRun?: boolean; environment?: string }) => {
    if (client === undefined) {
      throw new Error('Kernel lifecycle client is not configured');
    }
    const plan: LifecyclePlan = await client.createLifecyclePlan(selectedProductUnitId, phase, {
      providerMode: selectedProviderMode,
      environment: options?.environment ?? selectedEnvironment,
      ...(options?.dryRun !== undefined ? { dryRun: options.dryRun } : {}),
    });
    setSelectedRunId(plan.runId);
    await loadSnapshot();
  }, [client, selectedProductUnitId, selectedProviderMode, selectedEnvironment, loadSnapshot]);

  const executePhase = useCallback(async (phase: ProductLifecyclePhase, options?: { dryRun?: boolean; environment?: string }) => {
    if (client === undefined) {
      throw new Error('Kernel lifecycle client is not configured');
    }
    const run: LifecycleRun = await client.executeLifecyclePhase(selectedProductUnitId, phase, {
      providerMode: selectedProviderMode,
      environment: options?.environment ?? selectedEnvironment,
      ...(options?.dryRun !== undefined ? { dryRun: options.dryRun } : {}),
    });
    setSelectedRunId(run.runId);
    await loadSnapshot();
  }, [client, selectedProductUnitId, selectedProviderMode, selectedEnvironment, loadSnapshot]);

  const requestApproval = useCallback(async (actionRequest: ApprovalRequest) => {
    if (client === undefined) {
      throw new Error('Kernel lifecycle client is not configured');
    }
    await client.requestApproval(actionRequest);
    await loadSnapshot();
  }, [client, loadSnapshot]);

  const submitApprovalDecision = useCallback(async (approvalId: string, decision: ApprovalDecision) => {
    if (client === undefined) {
      throw new Error('Kernel lifecycle client is not configured');
    }
    await client.submitApprovalDecision(approvalId, decision);
    await loadSnapshot();
  }, [client, loadSnapshot]);

  const refresh = useCallback(async () => {
    await loadSnapshot();
  }, [loadSnapshot]);

  const value = useMemo<StudioLifecycleDataContextValue>(
    () => ({
      snapshot,
      selectedProductUnitId,
      selectedRunId,
      selectedEnvironment,
      selectedProviderMode,
      authenticatedUserId,
      selectProductUnit,
      selectRun,
      setEnvironment,
      setProviderMode,
      createPlan,
      executePhase,
      requestApproval,
      submitApprovalDecision,
      refresh,
    }),
    [
      snapshot,
      selectedProductUnitId,
      selectedRunId,
      selectedEnvironment,
      selectedProviderMode,
      authenticatedUserId,
      selectProductUnit,
      selectRun,
      setEnvironment,
      setProviderMode,
      createPlan,
      executePhase,
      requestApproval,
      submitApprovalDecision,
      refresh,
    ],
  );

  return (
    <StudioLifecycleDataContext.Provider value={value}>
      {children}
    </StudioLifecycleDataContext.Provider>
  );
}

export function useStudioLifecycleData(): StudioLifecycleDataContextValue {
  return useContext(StudioLifecycleDataContext);
}

async function loadRunManifests(
  client: KernelLifecycleClient,
  productUnitId: string,
  runId: string,
  signal: AbortSignal,
): Promise<
  Pick<
    StudioLifecycleSnapshot,
    'gateResultManifest' | 'artifactManifest' | 'deploymentManifest' | 'verifyHealthReport' | 'manifestLoadState'
  >
> {
  const [gateResultManifest, artifactManifest, deploymentManifest, verifyHealthReport] = await Promise.all([
    loadManifest(() => client.getGateResultManifest(productUnitId, runId)),
    loadManifest(() => client.getArtifactManifest(productUnitId, runId)),
    loadManifest(() => client.getDeploymentManifest(productUnitId, runId)),
    loadManifest(() => client.getVerifyHealthReport(productUnitId, runId)),
  ]);

  if (signal.aborted) {
    throw new Error('Load cancelled');
  }

  return {
    ...(gateResultManifest.data === undefined ? {} : { gateResultManifest: gateResultManifest.data }),
    ...(artifactManifest.data === undefined ? {} : { artifactManifest: artifactManifest.data }),
    ...(deploymentManifest.data === undefined ? {} : { deploymentManifest: deploymentManifest.data }),
    ...(verifyHealthReport.data === undefined ? {} : { verifyHealthReport: verifyHealthReport.data }),
    manifestLoadState: {
      gateResultManifest: {
        status: gateResultManifest.status,
        ...(gateResultManifest.message === undefined ? {} : { message: gateResultManifest.message }),
      },
      artifactManifest: {
        status: artifactManifest.status,
        ...(artifactManifest.message === undefined ? {} : { message: artifactManifest.message }),
      },
      deploymentManifest: {
        status: deploymentManifest.status,
        ...(deploymentManifest.message === undefined ? {} : { message: deploymentManifest.message }),
      },
      verifyHealthReport: {
        status: verifyHealthReport.status,
        ...(verifyHealthReport.message === undefined ? {} : { message: verifyHealthReport.message }),
      },
    },
  };
}

async function loadManifest<TValue>(
  load: () => Promise<TValue>,
): Promise<{ readonly status: StudioManifestLoadStatus; readonly data?: TValue; readonly message?: string }> {
  try {
    return { status: 'loaded', data: await load() };
  } catch (error: unknown) {
    if (isNotFoundError(error)) {
      return { status: 'missing' };
    }
    if (isUnauthorizedError(error)) {
      return { status: 'unauthorized', message: errorMessage(error) };
    }
    if (isCorruptManifestError(error)) {
      return { status: 'corrupt', message: errorMessage(error) };
    }
    return { status: 'unavailable', message: errorMessage(error) };
  }
}

function isNotFoundError(error: unknown): boolean {
  const code = extractStatusCode(error);
  return code === 404;
}

function isUnauthorizedError(error: unknown): boolean {
  const code = extractStatusCode(error);
  return code === 401 || code === 403;
}

function isCorruptManifestError(error: unknown): boolean {
  return errorMessage(error).toLowerCase().includes('corrupt');
}

function extractStatusCode(error: unknown): number | undefined {
  if (typeof error !== 'object' || error === null) {
    return undefined;
  }
  const candidate = error as { readonly statusCode?: unknown; readonly status?: unknown };
  if (typeof candidate.statusCode === 'number') {
    return candidate.statusCode;
  }
  if (typeof candidate.status === 'number') {
    return candidate.status;
  }
  return undefined;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Manifest unavailable';
}
