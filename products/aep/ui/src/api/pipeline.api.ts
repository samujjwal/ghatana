/**
 * AEP Pipeline REST API client.
 *
 * Communicates with the AEP backend for pipeline CRUD,
 * validation, pattern management, and capabilities introspection.
 *
 * @doc.type api-client
 * @doc.purpose AEP backend integration
 * @doc.layer frontend
 */
import axios, { type AxiosInstance } from 'axios';
import type {
  PipelineSpec,
  PipelineValidationResult,
  ConnectorSpec,
  PatternType,
} from '@/types/pipeline.types';

const BASE_URL = import.meta.env.VITE_AEP_API_URL ?? 'http://localhost:8081';

const client: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
});

// ─── Pipeline CRUD ───────────────────────────────────────────────────

export async function listPipelines(tenantId = 'default'): Promise<PipelineSpec[]> {
  const { data } = await client.get('/api/v1/pipelines', {
    params: { tenantId },
  });
  return data.pipelines;
}

export async function getPipeline(id: string, tenantId = 'default'): Promise<PipelineSpec> {
  const { data } = await client.get(`/api/v1/pipelines/${id}`, {
    params: { tenantId },
  });
  return data;
}

export async function savePipeline(pipeline: PipelineSpec, tenantId = 'default'): Promise<PipelineSpec> {
  if (pipeline.id) {
    const { data } = await client.put(`/api/v1/pipelines/${pipeline.id}`, pipeline, {
      params: { tenantId },
    });
    return data;
  }
  const { data } = await client.post('/api/v1/pipelines', pipeline, {
    params: { tenantId },
  });
  return data;
}

export async function deletePipeline(id: string, tenantId = 'default'): Promise<void> {
  await client.delete(`/api/v1/pipelines/${id}`, {
    params: { tenantId },
  });
}

// ─── Validation ──────────────────────────────────────────────────────

export async function validatePipeline(
  pipeline: PipelineSpec,
  tenantId = 'default',
): Promise<PipelineValidationResult> {
  const { data } = await client.post('/api/v1/pipelines/validate', pipeline, {
    params: { tenantId },
  });
  return data;
}

// ─── Patterns ────────────────────────────────────────────────────────

export interface PatternSummary {
  id: string;
  name: string;
  type: PatternType;
  status: string;
}

export async function listPatterns(tenantId = 'default'): Promise<PatternSummary[]> {
  const { data } = await client.get('/api/v1/patterns', {
    params: { tenantId },
  });
  return data.patterns;
}

// ─── Capabilities ────────────────────────────────────────────────────

export interface SchemaFormat {
  id: string;
  display: string;
  enabled: boolean;
}

export interface ConnectorCapability {
  type: string;
  direction: string;
  enabled: boolean;
}

export async function listSchemaFormats(): Promise<SchemaFormat[]> {
  const { data } = await client.get('/admin/capabilities/schemas');
  return data.schemaFormats;
}

export async function listConnectorCapabilities(): Promise<ConnectorCapability[]> {
  const { data } = await client.get('/admin/capabilities/connectors');
  return data.connectors;
}

export async function listEncodings(): Promise<string[]> {
  const { data } = await client.get('/admin/capabilities/encodings');
  return data.encodings;
}

export async function listTransforms(): Promise<{ id: string; description: string }[]> {
  const { data } = await client.get('/admin/capabilities/transforms');
  return data.transforms;
}

// ─── Export Pipeline as YAML ─────────────────────────────────────────

export function exportPipelineSpec(pipeline: PipelineSpec): string {
  // Produce a YAML-friendly JSON representation matching Java PipelineSpec
  const spec = {
    stages: pipeline.stages.map((stage) => ({
      name: stage.name,
      workflow: stage.workflow.map((agent) => ({
        id: agent.id,
        agent: agent.agent,
        role: agent.role,
        ...(agent.inputsSpec && { inputsSpec: agent.inputsSpec }),
        ...(agent.outputsSpec && { outputsSpec: agent.outputsSpec }),
        ...(agent.config && { config: agent.config }),
      })),
      ...(stage.connectorIds && { connectorIds: stage.connectorIds }),
      ...(stage.connectors && {
        connectors: stage.connectors.map((c: ConnectorSpec) => ({
          id: c.id,
          type: c.type,
          direction: c.direction,
          ...(c.encoding && { encoding: c.encoding }),
          ...(c.config && { config: c.config }),
        })),
      }),
    })),
  };
  return JSON.stringify(spec, null, 2);
}
