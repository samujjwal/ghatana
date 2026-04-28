/**
 * AEP Orchestration Client
 *
 * Calls the AEP Central Registry to submit requirement orchestration requests.
 * Routes through the canonical `/api/v1/agents/:agentId/execute` endpoint.
 *
 * @doc.type class
 * @doc.purpose HTTP client for AEP agent execution (requirement orchestration)
 * @doc.layer product
 * @doc.pattern Service
 */

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface RequirementApprovedPayload {
  type: 'requirement.approved';
  projectId: string;
  requirementId: string;
  approvalId: string;
  timestamp: string;
}

export type OrchestrationPayload = RequirementApprovedPayload;

export interface OrchestrationRunRef {
  runId: string;
  agentId: string;
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  createdAt: string;
}

/**
 * Lineage information for an AEP run.
 * Includes pipeline version, agent versions, policy bundle, and evaluation gate.
 */
export interface AepRunLineage {
  runId: string;
  pipelineVersion: string;
  agentVersions: Record<string, string>;
  policyBundle: {
    bundleId: string;
    version: string;
    policies: string[];
  };
  evaluationGate: {
    gateId: string;
    status: 'PENDING' | 'PASSED' | 'FAILED';
    checks: EvaluationCheck[];
  };
  runDetailUrl: string;
  createdAt: string;
  updatedAt: string;
}

export interface EvaluationCheck {
  checkId: string;
  name: string;
  status: 'PENDING' | 'PASSED' | 'FAILED';
  message?: string;
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

const REQUIREMENT_ORCHESTRATION_AGENT_ID = 'requirement-orchestration-agent';

async function executeAgent(
  agentId: string,
  payload: OrchestrationPayload,
  signal?: AbortSignal,
): Promise<OrchestrationRunRef> {
  const url = `/api/v1/agents/${encodeURIComponent(agentId)}/execute`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal,
  });

  if (!response.ok) {
    const text = await response.text().catch(() => response.statusText);
    throw new Error(
      `AEP orchestration request failed [${response.status}]: ${text}`,
    );
  }

  const data: unknown = await response.json();
  return validateOrchestrationRunRef(data);
}

function validateOrchestrationRunRef(data: unknown): OrchestrationRunRef {
  if (
    typeof data !== 'object' ||
    data === null ||
    typeof (data as Record<string, unknown>)['runId'] !== 'string' ||
    typeof (data as Record<string, unknown>)['agentId'] !== 'string'
  ) {
    throw new Error('AEP orchestration response did not match expected shape');
  }
  const record = data as Record<string, unknown>;
  return {
    runId: record['runId'] as string,
    agentId: record['agentId'] as string,
    status: (record['status'] as OrchestrationRunRef['status']) ?? 'QUEUED',
    createdAt:
      typeof record['createdAt'] === 'string'
        ? record['createdAt']
        : new Date().toISOString(),
  };
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Submit a requirement-approved event to the AEP orchestration agent.
 *
 * Returns the AEP run reference so callers can track progress.
 */
export async function submitRequirementApproved(
  params: {
    projectId: string;
    requirementId: string;
    approvalId: string;
  },
  signal?: AbortSignal,
): Promise<OrchestrationRunRef> {
  const payload: RequirementApprovedPayload = {
    type: 'requirement.approved',
    projectId: params.projectId,
    requirementId: params.requirementId,
    approvalId: params.approvalId,
    timestamp: new Date().toISOString(),
  };
  return executeAgent(REQUIREMENT_ORCHESTRATION_AGENT_ID, payload, signal);
}

/**
 * Fetch lineage information for an AEP run.
 *
 * Returns pipeline version, agent versions, policy bundle, and evaluation gate details.
 */
export async function fetchAepRunLineage(
  runId: string,
  signal?: AbortSignal,
): Promise<AepRunLineage> {
  const url = `/api/v1/aep/runs/${encodeURIComponent(runId)}/lineage`;

  const response = await fetch(url, {
    method: 'GET',
    headers: { 'Content-Type': 'application/json' },
    signal,
  });

  if (!response.ok) {
    const text = await response.text().catch(() => response.statusText);
    throw new Error(
      `AEP lineage request failed [${response.status}]: ${text}`,
    );
  }

  const data: unknown = await response.json();
  return validateAepRunLineage(data);
}

function validateAepRunLineage(data: unknown): AepRunLineage {
  if (
    typeof data !== 'object' ||
    data === null ||
    typeof (data as Record<string, unknown>)['runId'] !== 'string' ||
    typeof (data as Record<string, unknown>)['pipelineVersion'] !== 'string' ||
    typeof (data as Record<string, unknown>)['runDetailUrl'] !== 'string'
  ) {
    throw new Error('AEP lineage response did not match expected shape');
  }
  const record = data as Record<string, unknown>;
  return {
    runId: record['runId'] as string,
    pipelineVersion: record['pipelineVersion'] as string,
    agentVersions: (record['agentVersions'] as Record<string, string>) ?? {},
    policyBundle: validatePolicyBundle(record['policyBundle']),
    evaluationGate: validateEvaluationGate(record['evaluationGate']),
    runDetailUrl: record['runDetailUrl'] as string,
    createdAt:
      typeof record['createdAt'] === 'string'
        ? record['createdAt']
        : new Date().toISOString(),
    updatedAt:
      typeof record['updatedAt'] === 'string'
        ? record['updatedAt']
        : new Date().toISOString(),
  };
}

function validatePolicyBundle(data: unknown): AepRunLineage['policyBundle'] {
  if (typeof data !== 'object' || data === null) {
    return { bundleId: '', version: '', policies: [] };
  }
  const record = data as Record<string, unknown>;
  return {
    bundleId: (record['bundleId'] as string) ?? '',
    version: (record['version'] as string) ?? '',
    policies: (record['policies'] as string[]) ?? [],
  };
}

function validateEvaluationGate(data: unknown): AepRunLineage['evaluationGate'] {
  if (typeof data !== 'object' || data === null) {
    return { gateId: '', status: 'PENDING', checks: [] };
  }
  const record = data as Record<string, unknown>;
  const checks = record['checks'];
  return {
    gateId: (record['gateId'] as string) ?? '',
    status: (record['status'] as AepRunLineage['evaluationGate']['status']) ?? 'PENDING',
    checks: Array.isArray(checks) ? checks.map(validateEvaluationCheck) : [],
  };
}

function validateEvaluationCheck(data: unknown): EvaluationCheck {
  if (typeof data !== 'object' || data === null) {
    return { checkId: '', name: '', status: 'PENDING' };
  }
  const record = data as Record<string, unknown>;
  return {
    checkId: (record['checkId'] as string) ?? '',
    name: (record['name'] as string) ?? '',
    status: (record['status'] as EvaluationCheck['status']) ?? 'PENDING',
    message: (record['message'] as string) ?? undefined,
  };
}
