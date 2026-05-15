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

export type StudioLifecycleDataStatus = 'unconfigured' | 'loading' | 'ready' | 'degraded';

export interface StudioLifecycleSnapshot {
  readonly status: StudioLifecycleDataStatus;
  readonly productUnit?: ProductUnit;
  readonly lifecycleRuns: readonly LifecycleRun[];
  readonly selectedRun?: LifecycleRun;
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
  readonly productUnitId?: string;
  readonly children: ReactNode;
}

const EMPTY_SNAPSHOT: StudioLifecycleSnapshot = {
  status: 'unconfigured',
  lifecycleRuns: [],
};

const StudioLifecycleDataContext = createContext<StudioLifecycleDataContextValue>({
  snapshot: EMPTY_SNAPSHOT,
  selectedProductUnitId: 'digital-marketing',
  selectedRunId: null,
  selectedEnvironment: 'local',
  selectedProviderMode: 'bootstrap',
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
  const { client, productUnitId: initialProductUnitId = 'digital-marketing', children } = props;
  
  const [selectedProductUnitId, setSelectedProductUnitId] = useState<string>(initialProductUnitId);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [selectedEnvironment, setSelectedEnvironment] = useState<string>('local');
  const [selectedProviderMode, setSelectedProviderMode] = useState<'bootstrap' | 'platform'>('bootstrap');
  const [snapshot, setSnapshot] = useState<StudioLifecycleSnapshot>(EMPTY_SNAPSHOT);
  
  const abortControllerRef = useRef<AbortController | null>(null);
  const pollingIntervalRef = useRef<number | null>(null);

  const loadSnapshot = useCallback(async () => {
    if (client === undefined) {
      setSnapshot(EMPTY_SNAPSHOT);
      return;
    }

    const lifecycleClient = client;
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    abortControllerRef.current = new AbortController();
    const signal = abortControllerRef.current.signal;

    setSnapshot({ status: 'loading', lifecycleRuns: [] });

    try {
      const productUnits = await lifecycleClient.listProductUnits();
      const selectedProductUnit =
        productUnits.find((candidate: ProductUnit) => candidate.id === selectedProductUnitId) ??
        productUnits[0];

      if (selectedProductUnit === undefined) {
        if (!signal.aborted) {
          setSnapshot({
            status: 'degraded',
            lifecycleRuns: [],
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

      if (!signal.aborted) {
        setSnapshot({
          status: 'ready',
          productUnit: selectedProductUnit,
          lifecycleRuns,
          selectedRun,
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
          lifecycleRuns: [],
          errorMessage: error instanceof Error ? error.message : 'Kernel lifecycle data failed to load.',
        });
      }
    }
  }, [client, selectedProductUnitId, selectedRunId]);

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
    const plan: LifecyclePlan = await client.createLifecyclePlan(selectedProductUnitId, phase, options);
    setSelectedRunId(plan.runId);
    await loadSnapshot();
  }, [client, selectedProductUnitId, loadSnapshot]);

  const executePhase = useCallback(async (phase: ProductLifecyclePhase, options?: { dryRun?: boolean; environment?: string }) => {
    if (client === undefined) {
      throw new Error('Kernel lifecycle client is not configured');
    }
    const run: LifecycleRun = await client.executeLifecyclePhase(selectedProductUnitId, phase, options);
    setSelectedRunId(run.runId);
    await loadSnapshot();
  }, [client, selectedProductUnitId, loadSnapshot]);

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
    'gateResultManifest' | 'artifactManifest' | 'deploymentManifest' | 'verifyHealthReport'
  >
> {
  const [gateResultManifest, artifactManifest, deploymentManifest, verifyHealthReport] =
    await Promise.all([
      client.getGateResultManifest(productUnitId, runId).catch(() => undefined),
      client.getArtifactManifest(productUnitId, runId).catch(() => undefined),
      client.getDeploymentManifest(productUnitId, runId).catch(() => undefined),
      client.getVerifyHealthReport(productUnitId, runId).catch(() => undefined),
    ]);

  if (signal.aborted) {
    throw new Error('Load cancelled');
  }

  return {
    gateResultManifest,
    artifactManifest,
    deploymentManifest,
    verifyHealthReport,
  };
}
