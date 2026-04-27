/**
 * Conflict resolution policy for offline mutation replay.
 *
 * @doc.type module
 * @doc.purpose Deterministic conflict handling for mobile offline sync
 * @doc.layer product
 * @doc.pattern Policy
 */

export type ConflictStrategy = 'SERVER_WINS' | 'CLIENT_WINS' | 'LAST_WRITE_WINS';

export type ConflictDecision = 'accept_server' | 'retry_client';

export interface ConflictEnvelope {
  serverUpdatedAt?: string;
  reason?: string;
}

export interface MutationEnvelope {
  type: string;
  payload: unknown;
}

const MUTATION_STRATEGY: Record<string, ConflictStrategy> = {
  COMPLETE_LESSON: 'LAST_WRITE_WINS',
  UPDATE_PROGRESS: 'LAST_WRITE_WINS',
  SUBMIT_QUIZ: 'SERVER_WINS',
  ADD_BOOKMARK: 'CLIENT_WINS',
  ADD_NOTE: 'CLIENT_WINS',
};

function readClientUpdatedAt(payload: unknown): number | null {
  if (!payload || typeof payload !== 'object') {
    return null;
  }

  const candidate = (payload as { updatedAt?: unknown }).updatedAt;
  if (typeof candidate !== 'string') {
    return null;
  }

  const timestamp = Date.parse(candidate);
  return Number.isNaN(timestamp) ? null : timestamp;
}

function readServerUpdatedAt(conflict: ConflictEnvelope): number | null {
  if (!conflict.serverUpdatedAt) {
    return null;
  }
  const timestamp = Date.parse(conflict.serverUpdatedAt);
  return Number.isNaN(timestamp) ? null : timestamp;
}

export function getStrategyForMutation(type: string): ConflictStrategy {
  return MUTATION_STRATEGY[type] ?? 'SERVER_WINS';
}

export function decideConflictResolution(
  mutation: MutationEnvelope,
  conflict: ConflictEnvelope,
): ConflictDecision {
  const strategy = getStrategyForMutation(mutation.type);

  if (strategy === 'CLIENT_WINS') {
    return 'retry_client';
  }

  if (strategy === 'SERVER_WINS') {
    return 'accept_server';
  }

  const clientUpdatedAt = readClientUpdatedAt(mutation.payload);
  const serverUpdatedAt = readServerUpdatedAt(conflict);

  if (clientUpdatedAt === null || serverUpdatedAt === null) {
    // Without deterministic timestamps we fail safe to server state.
    return 'accept_server';
  }

  return clientUpdatedAt >= serverUpdatedAt ? 'retry_client' : 'accept_server';
}
