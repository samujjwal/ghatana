/**
 * Build API Types
 *
 * TypeScript type definitions for Build module (Workflows, Agents, Simulator).
 * Aligned with SOFTWARE_ORG_BUILD_IMPLEMENTATION_PLAN.md.
 *
 * @doc.type module
 * @doc.purpose Type definitions for Build APIs
 * @doc.layer product
 * @doc.pattern Types
 */

// =============================================================================
// Workflow Types
// =============================================================================

export interface WorkflowResponse {
    id: string;
    tenantId: string;
    name: string;
    slug: string;
    description: string | null;
    status: string;
    ownerTeamId: string | null;
    trigger: Record<string, unknown>;
    steps: Record<string, unknown>[];
    serviceIds: string[];
    policyIds: string[];
    createdAt: string;
    updatedAt: string;
}

export interface WorkflowCreateBody {
    tenantId: string;
    name: string;
    slug: string;
    description?: string;
    ownerTeamId?: string;
    trigger: Record<string, unknown>;
    steps: Record<string, unknown>[];
}

export interface WorkflowUpdateBody {
    name?: string;
    description?: string;
    ownerTeamId?: string;
    trigger?: Record<string, unknown>;
    steps?: Record<string, unknown>[];
    serviceIds?: string[];
    policyIds?: string[];
}

// =============================================================================
// Agent Types
// =============================================================================

export interface AgentResponse {
    id: string;
    tenantId: string;
    name: string;
    slug: string;
    description: string | null;
    type: string;
    status: string;
    personaId: string | null;
    tools: string[];
    guardrails: Record<string, unknown>;
    serviceIds: string[];
    createdAt: string;
    updatedAt: string;
}

export interface AgentCreateBody {
    tenantId: string;
    name: string;
    slug: string;
    description?: string;
    type: string;
    personaId?: string;
    tools: string[];
    guardrails: Record<string, unknown>;
}

export interface AgentUpdateBody {
    name?: string;
    description?: string;
    type?: string;
    personaId?: string;
    tools?: string[];
    guardrails?: Record<string, unknown>;
    serviceIds?: string[];
}

// =============================================================================
// Simulator Types
// =============================================================================

export interface SimulateRequest {
    workflowId?: string;
    agentId?: string;
    tenantId: string;
    environment: string;
    eventPayload: Record<string, unknown>;
}

export interface SimulateResponse {
    simulationId: string;
    status: 'success' | 'failure';
    trace: {
        step: string;
        timestamp: string;
        action: string;
        result: Record<string, unknown>;
    }[];
    policyBlocks: {
        policyId: string;
        policyName: string;
        reason: string;
    }[];
    duration: number;
}
