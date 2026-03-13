/**
 * Dedicated TypeScript types for agent domain.
 *
 * Re-exports types from aep.api.ts so pages/components can import from a
 * single canonical source. Additional agent-only types are defined here.
 *
 * @doc.type types
 * @doc.purpose Agent domain types for the AEP UI
 * @doc.layer frontend
 */
import type { AgentStatus } from '@/api/aep.api';

export type { AgentStatus, AgentRegistration } from '@/api/aep.api';

/** Filter criteria for agent list queries. */
export interface AgentFilters {
  search?: string;
  status?: AgentStatus | '';
  capability?: string;
}

/** Agent memory summary returned by the backend. */
export interface AgentMemorySummary {
  agentId: string;
  tenantId: string;
  total: number;
  byType: Record<string, number>;
  timestamp: string;
}

/** Agent execution request body. */
export interface AgentExecuteRequest {
  input: Record<string, unknown>;
  tenantId: string;
}
