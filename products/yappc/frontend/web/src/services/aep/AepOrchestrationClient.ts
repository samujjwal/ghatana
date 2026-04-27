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
