/**
 * Consolidated Canvas Infrastructure Hook
 * 
 * Replaces: useCICDPipeline, useCloudInfrastructure, useDataPipeline, 
 *           useReleaseTrain, useServiceHealth, usePerformanceAnalysis
 * Provides: Infrastructure & DevOps features
 */

import { useCallback, useState } from 'react';

export type CloudProvider = 'aws' | 'azure' | 'gcp';

export interface Pipeline {
  id: string;
  name: string;
  stages: PipelineStage[];
  status: 'running' | 'success' | 'failed' | 'pending';
}

export interface PipelineStage {
  name: string;
  status: 'pending' | 'running' | 'success' | 'failed';
  duration?: number;
}

export interface PipelineConfig {
  name: string;
  trigger: 'push' | 'pr' | 'schedule';
  stages: string[];
}

export interface CloudResource {
  id: string;
  type: string;
  name: string;
  region: string;
  cost: number;
}

export interface ResourceSpec {
  type: string;
  name: string;
  config: Record<string, unknown>;
}

export interface Deployment {
  id: string;
  status: 'pending' | 'deploying' | 'deployed' | 'failed';
  resources: CloudResource[];
}

export interface DataPipeline {
  id: string;
  name: string;
  source: string;
  transformations: string[];
  destination: string;
}

export interface DataPipelineConfig {
  name: string;
  source: { type: string; config: Record<string, unknown> };
  transformations: Array<{ type: string; config: Record<string, unknown> }>;
  destination: { type: string; config: Record<string, unknown> };
}

export interface Release {
  id: string;
  version: string;
  features: string[];
  scheduledDate: Date;
  status: 'planned' | 'in-progress' | 'deployed' | 'rolled-back';
}

export interface ReleaseSpec {
  version: string;
  features: string[];
  scheduledDate: Date;
}

export interface HealthStatus {
  overall: 'healthy' | 'degraded' | 'down';
  services: ServiceHealth[];
}

export interface ServiceHealth {
  name: string;
  status: 'up' | 'down' | 'degraded';
  uptime: number;
  latency: number;
}

export interface Metrics {
  cpu: number;
  memory: number;
  requests: number;
  errors: number;
}

export interface UseCanvasInfrastructureOptions {
  canvasId: string;
  provider?: CloudProvider;
}

export interface UseCanvasInfrastructureReturn {
  pipelines: Pipeline[];
  createPipeline: (config: PipelineConfig) => Promise<Pipeline>;
  triggerPipeline: (pipelineId: string) => Promise<void>;
  
  resources: CloudResource[];
  deployResource: (resource: ResourceSpec) => Promise<Deployment>;
  deleteResource: (resourceId: string) => Promise<void>;
  
  dataPipelines: DataPipeline[];
  createDataPipeline: (config: DataPipelineConfig) => Promise<DataPipeline>;
  
  releases: Release[];
  scheduleRelease: (release: ReleaseSpec) => Promise<Release>;
  
  healthStatus: HealthStatus;
  performanceMetrics: Metrics;
  
  isLoading: boolean;
  error: Error | null;
}

export function useCanvasInfrastructure(
  options: UseCanvasInfrastructureOptions
): UseCanvasInfrastructureReturn {
  const { canvasId, provider = 'aws' } = options;

  const [pipelines, setPipelines] = useState<Pipeline[]>([]);
  const [resources, setResources] = useState<CloudResource[]>([]);
  const [dataPipelines, setDataPipelines] = useState<DataPipeline[]>([]);
  const [releases, setReleases] = useState<Release[]>([]);
  const [healthStatus] = useState<HealthStatus>({
    overall: 'healthy',
    services: [],
  });
  const [performanceMetrics] = useState<Metrics>({
    cpu: 45,
    memory: 60,
    requests: 1000,
    errors: 5,
  });
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const createPipeline = useCallback(async (config: PipelineConfig): Promise<Pipeline> => {
    setIsLoading(true);
    try {
      const pipeline: Pipeline = {
        id: `pipeline-${Date.now()}`,
        name: config.name,
        stages: config.stages.map(name => ({ name, status: 'pending' })),
        status: 'pending',
      };
      setPipelines(prev => [...prev, pipeline]);
      return pipeline;
    } catch (err) {
      setError(err as Error);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const triggerPipeline = useCallback(async (pipelineId: string): Promise<void> => {
    setPipelines(prev =>
      prev.map(p => (p.id === pipelineId ? { ...p, status: 'running' as const } : p))
    );
  }, []);

  const deployResource = useCallback(async (resource: ResourceSpec): Promise<Deployment> => {
    setIsLoading(true);
    try {
      const deployment: Deployment = {
        id: `deploy-${Date.now()}`,
        status: 'deploying',
        resources: [],
      };
      return deployment;
    } catch (err) {
      setError(err as Error);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const deleteResource = useCallback(async (resourceId: string): Promise<void> => {
    setResources(prev => prev.filter(r => r.id !== resourceId));
  }, []);

  const createDataPipeline = useCallback(async (config: DataPipelineConfig): Promise<DataPipeline> => {
    const pipeline: DataPipeline = {
      id: `data-pipeline-${Date.now()}`,
      name: config.name,
      source: config.source.type,
      transformations: config.transformations.map(t => t.type),
      destination: config.destination.type,
    };
    setDataPipelines(prev => [...prev, pipeline]);
    return pipeline;
  }, []);

  const scheduleRelease = useCallback(async (release: ReleaseSpec): Promise<Release> => {
    const newRelease: Release = {
      id: `release-${Date.now()}`,
      ...release,
      status: 'planned',
    };
    setReleases(prev => [...prev, newRelease]);
    return newRelease;
  }, []);

  return {
    pipelines,
    createPipeline,
    triggerPipeline,
    resources,
    deployResource,
    deleteResource,
    dataPipelines,
    createDataPipeline,
    releases,
    scheduleRelease,
    healthStatus,
    performanceMetrics,
    isLoading,
    error,
  };
}
