/**
 * AEP Run Lineage API client for YAPPC (F-Y009 / C-Y4)
 *
 * Fetches the full AEP run lineage from the YAPPC backend, which proxies to
 * the AEP Central Registry at `/api/v1/agents/:agentId/runs/:runId/lineage`.
 *
 * Returns structured lineage data including:
 *  - run ID + status
 *  - pipeline version
 *  - agent versions (per step)
 *  - policy bundle reference
 *  - evaluation gate results
 *  - deep link to AEP run-detail
 *
 * @doc.type service
 * @doc.purpose AEP run lineage API client for traceability in YAPPC
 * @doc.layer product
 * @doc.pattern API Client
 */

import { parseJsonResponse } from '@/lib/http';
import type { RunLineageData, RunLineageNode } from '@/components/ai/RunLineage';

// ── Internal AEP types ────────────────────────────────────────────────────────

export type AepRunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED';

export interface AepAgentVersion {
  agentId: string;
  agentName: string;
  version: string;
}

export interface AepEvaluationGateResult {
  passed: boolean;
  score?: number;
  /** Human-readable reason for pass/fail */
  reason?: string;
}

/**
 * Raw shape returned by the YAPPC backend proxy of the AEP lineage endpoint.
 */
export interface AepRunLineageResponse {
  runId: string;
  status: AepRunStatus;
  pipelineVersion: string;
  agentVersions: AepAgentVersion[];
  policyBundleRef: string;
  evaluationGate: AepEvaluationGateResult;
  /** Full URL to the AEP run-detail page (e.g. /aep/runs/:runId) */
  aepRunDetailUrl: string;
}

// ── Fetch ─────────────────────────────────────────────────────────────────────

const importMetaEnv = import.meta as ImportMeta & {
  env?: { DEV?: boolean; VITE_API_ORIGIN?: string };
};

const API_BASE_URL = importMetaEnv.env?.DEV
  ? `${importMetaEnv.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
  : '/api';

/**
 * Fetch raw AEP run lineage from the YAPPC backend.
 */
export async function fetchAepRunLineageRaw(
  runId: string
): Promise<AepRunLineageResponse> {
  const response = await fetch(
    `${API_BASE_URL}/v1/agents/runs/${encodeURIComponent(runId)}/lineage`,
    { credentials: 'include' }
  );
  return parseJsonResponse<AepRunLineageResponse>(response, 'fetch AEP run lineage');
}

/**
 * Fetch AEP run lineage and transform it into the `RunLineageData` shape
 * expected by the `<RunLineage>` component.
 *
 * Nodes are ordered: Run → Pipeline → Policy → Agents → Evaluation Gate.
 */
export async function fetchAepRunLineage(runId: string): Promise<RunLineageData> {
  const raw = await fetchAepRunLineageRaw(runId);

  const nodes: RunLineageNode[] = [
    {
      id: raw.runId,
      label: `Run ${raw.runId.slice(0, 8)}`,
      type: 'run',
      href: raw.aepRunDetailUrl,
    },
    {
      id: `pipeline-${raw.pipelineVersion}`,
      label: `Pipeline v${raw.pipelineVersion}`,
      type: 'plan',
    },
    {
      id: `policy-${raw.policyBundleRef}`,
      label: `Policy: ${raw.policyBundleRef}`,
      type: 'step',
    },
    ...raw.agentVersions.map<RunLineageNode>((av) => ({
      id: av.agentId,
      label: `${av.agentName} v${av.version}`,
      type: 'agent',
      href: `/aep/agents/${av.agentId}`,
    })),
    {
      id: 'eval-gate',
      label: raw.evaluationGate.passed ? 'Eval gate ✓' : 'Eval gate ✗',
      type: 'step',
    },
  ];

  return { runId: raw.runId, nodes };
}
