/**
 * Dedicated TypeScript types for the HITL (Human-in-the-Loop) domain.
 *
 * Re-exports HITL-related types from aep.api.ts so pages/components can import
 * from a single canonical source. Additional HITL-only types are defined here.
 *
 * @doc.type types
 * @doc.purpose HITL domain types for the AEP UI
 * @doc.layer frontend
 */
import type { ReviewItemStatus } from '@/api/aep.api';

export type { ReviewItemStatus, ReviewItem } from '@/api/aep.api';

/** Filter criteria for HITL queue queries. */
export interface HitlFilters {
  status?: ReviewItemStatus | '';
  itemType?: 'POLICY' | 'PATTERN' | 'AGENT_DECISION' | '';
}

/** Approve review request body. */
export interface ApproveRequest {
  note?: string;
}

/** Reject review request body. */
export interface RejectRequest {
  reason: string;
}
