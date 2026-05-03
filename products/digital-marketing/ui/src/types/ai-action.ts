export type AiActionType =
  | 'RECOMMENDATION_GENERATED'
  | 'DRAFT_GENERATED'
  | 'VALIDATION_RESULT'
  | 'APPROVAL_DECISION'
  | 'ACTION_EXECUTED'
  | 'ACTION_BLOCKED';

export type AiActionStatus =
  | 'PROPOSED'
  | 'EXECUTED'
  | 'BLOCKED'
  | 'APPROVED'
  | 'REJECTED';

export interface AiActionLogEntry {
  actionId: string;
  workspaceId: string;
  correlationId: string;
  actionType: AiActionType;
  status: AiActionStatus;
  actor: string;
  initiatedByAi: boolean;
  confidence: number | null;
  evidenceLinks: string[];
  policyChecks: string[];
  summary: string;
  details: string;
  relatedEntityId: string | null;
  occurredAt: string;
}

export interface ListAiActionLogResponse {
  entries: AiActionLogEntry[];
}
