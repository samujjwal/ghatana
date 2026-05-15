import {
  createContext,
  type ReactElement,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import type { ArtifactManifest } from '@ghatana/kernel-artifacts';
import type { DeploymentManifest } from '@ghatana/kernel-deployment';
import type { ProductUnit } from '@ghatana/kernel-product-contracts';
import type {
  GateResultManifest,
  KernelLifecycleClient,
  LifecycleRun,
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

interface StudioLifecycleDataProviderProps {
  readonly client?: KernelLifecycleClient;
  readonly productUnitId?: string;
  readonly children: ReactNode;
}

const EMPTY_SNAPSHOT: StudioLifecycleSnapshot = {
  status: 'unconfigured',
  lifecycleRuns: [],
};

const StudioLifecycleDataContext = createContext<StudioLifecycleSnapshot>(EMPTY_SNAPSHOT);

export function StudioLifecycleDataProvider(
  props: StudioLifecycleDataProviderProps,
): ReactElement {
  const { client, productUnitId = 'digital-marketing', children } = props;
  const [snapshot, setSnapshot] = useState<StudioLifecycleSnapshot>(EMPTY_SNAPSHOT);

  useEffect(() => {
    if (client === undefined) {
      setSnapshot(EMPTY_SNAPSHOT);
      return;
    }

    const lifecycleClient = client;
    let cancelled = false;
    setSnapshot({ status: 'loading', lifecycleRuns: [] });

    async function loadSnapshot(): Promise<void> {
      try {
        const productUnits = await lifecycleClient.listProductUnits();
        const selectedProductUnit =
          productUnits.find((candidate: ProductUnit) => candidate.id === productUnitId) ??
          productUnits[0];

        if (selectedProductUnit === undefined) {
          if (!cancelled) {
            setSnapshot({
              status: 'degraded',
              lifecycleRuns: [],
              errorMessage: 'Kernel returned no ProductUnits for Studio.',
            });
          }
          return;
        }

        const lifecycleRuns = await lifecycleClient.listLifecycleRuns(selectedProductUnit.id);
        const selectedRun = lifecycleRuns[0];
        const manifests =
          selectedRun === undefined
            ? undefined
            : await loadRunManifests(lifecycleClient, selectedProductUnit.id, selectedRun.runId);

        if (!cancelled) {
          setSnapshot({
            status: 'ready',
            productUnit: selectedProductUnit,
            lifecycleRuns,
            selectedRun,
            ...(manifests ?? {}),
          });
        }
      } catch (error: unknown) {
        if (!cancelled) {
          setSnapshot({
            status: 'degraded',
            lifecycleRuns: [],
            errorMessage: error instanceof Error ? error.message : 'Kernel lifecycle data failed to load.',
          });
        }
      }
    }

    void loadSnapshot();

    return () => {
      cancelled = true;
    };
  }, [client, productUnitId]);

  const value = useMemo((): StudioLifecycleSnapshot => snapshot, [snapshot]);

  return (
    <StudioLifecycleDataContext.Provider value={value}>
      {children}
    </StudioLifecycleDataContext.Provider>
  );
}

export function useStudioLifecycleData(): StudioLifecycleSnapshot {
  return useContext(StudioLifecycleDataContext);
}

async function loadRunManifests(
  client: KernelLifecycleClient,
  productUnitId: string,
  runId: string,
): Promise<
  Pick<
    StudioLifecycleSnapshot,
    'gateResultManifest' | 'artifactManifest' | 'deploymentManifest' | 'verifyHealthReport'
  >
> {
  const [gateResultManifest, artifactManifest, deploymentManifest, verifyHealthReport] =
    await Promise.all([
      client.getGateResultManifest(productUnitId, runId),
      client.getArtifactManifest(productUnitId, runId),
      client.getDeploymentManifest(productUnitId, runId),
      client.getVerifyHealthReport(productUnitId, runId),
    ]);

  return {
    gateResultManifest,
    artifactManifest,
    deploymentManifest,
    verifyHealthReport,
  };
}
