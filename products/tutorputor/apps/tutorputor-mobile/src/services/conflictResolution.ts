/**
 * Conflict resolution policy for offline mutation replay.
 *
 * F-020: Per-entity conflict resolution table with warn-on-conflict observability.
 *
 * ## Policy table
 *
 * | Mutation type           | Strategy         | Rationale |
 * |-------------------------|------------------|-----------|
 * | COMPLETE_LESSON         | LAST_WRITE_WINS  | Device with most recent completion wins |
 * | UPDATE_PROGRESS         | LAST_WRITE_WINS  | Retain highest timestamp progress |
 * | SUBMIT_QUIZ             | SERVER_WINS      | Server is authority for graded attempts |
 * | SUBMIT_ASSESSMENT       | SERVER_WINS      | Assessment submissions are immutable once received |
 * | ABANDON_ASSESSMENT      | SERVER_WINS      | State machine; server owns transitions |
 * | ADD_BOOKMARK            | CLIENT_WINS      | Additive; double-insert is idempotent |
 * | REMOVE_BOOKMARK         | SERVER_WINS      | Deletions favour the server to prevent ghost data |
 * | ADD_NOTE                | CLIENT_WINS      | User-authored content should never be silently lost |
 * | EDIT_NOTE               | LAST_WRITE_WINS  | Last editor wins on concurrent note edits |
 * | DELETE_NOTE             | SERVER_WINS      | Deletions favour the server |
 * | UPDATE_NOTIFICATION_PREF| LAST_WRITE_WINS  | Most recent preference update wins |
 * | RATE_MODULE             | SERVER_WINS      | Prevent duplicate ratings |
 *
 * Any mutation type not listed here defaults to SERVER_WINS (safe fallback).
 *
 * @doc.type module
 * @doc.purpose Deterministic conflict handling for mobile offline sync (F-020)
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

/**
 * Structured conflict event emitted for observability (F-020).
 * Callers may wire this into analytics / crash-reporting.
 */
export interface ConflictEvent {
  mutationType: string;
  strategy: ConflictStrategy;
  decision: ConflictDecision;
  clientUpdatedAt: string | null;
  serverUpdatedAt: string | null;
  reason?: string;
}

/**
 * Per-entity mutation strategy table (F-020).
 * Unknown mutation types resolve to SERVER_WINS.
 */
const MUTATION_STRATEGY: Record<string, ConflictStrategy> = {
  // Progress / completion
  COMPLETE_LESSON: 'LAST_WRITE_WINS',
  UPDATE_PROGRESS: 'LAST_WRITE_WINS',

  // Assessments — server is authoritative
  SUBMIT_QUIZ: 'SERVER_WINS',
  SUBMIT_ASSESSMENT: 'SERVER_WINS',
  ABANDON_ASSESSMENT: 'SERVER_WINS',

  // Bookmarks
  ADD_BOOKMARK: 'CLIENT_WINS',
  REMOVE_BOOKMARK: 'SERVER_WINS',

  // Notes — user-authored content is precious
  ADD_NOTE: 'CLIENT_WINS',
  EDIT_NOTE: 'LAST_WRITE_WINS',
  DELETE_NOTE: 'SERVER_WINS',

  // Preferences
  UPDATE_NOTIFICATION_PREF: 'LAST_WRITE_WINS',

  // Ratings
  RATE_MODULE: 'SERVER_WINS',
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

/**
 * Resolve an offline conflict and optionally emit a structured warning.
 *
 * @param mutation - The offline mutation that was rejected by the server.
 * @param conflict - Metadata returned by the server explaining the conflict.
 * @param onConflict - Optional callback invoked with a structured {@link ConflictEvent}
 *   for warn-on-conflict observability (F-020). Wire this to your analytics service.
 */
export function decideConflictResolution(
  mutation: MutationEnvelope,
  conflict: ConflictEnvelope,
  onConflict?: (event: ConflictEvent) => void,
): ConflictDecision {
  const strategy = getStrategyForMutation(mutation.type);

  let decision: ConflictDecision;

  if (strategy === 'CLIENT_WINS') {
    decision = 'retry_client';
  } else if (strategy === 'SERVER_WINS') {
    decision = 'accept_server';
  } else {
    // LAST_WRITE_WINS
    const clientTs = readClientUpdatedAt(mutation.payload);
    const serverTs = readServerUpdatedAt(conflict);

    if (clientTs === null || serverTs === null) {
      // Without deterministic timestamps we fail safe to server state.
      decision = 'accept_server';
    } else {
      decision = clientTs >= serverTs ? 'retry_client' : 'accept_server';
    }
  }

  // F-020: Warn on conflict — emit structured event so the crash-reporter /
  // analytics pipeline can track conflict frequency per mutation type.
  if (onConflict) {
    const clientUpdatedAt =
      mutation.payload &&
      typeof mutation.payload === 'object' &&
      typeof (mutation.payload as { updatedAt?: unknown }).updatedAt === 'string'
        ? (mutation.payload as { updatedAt: string }).updatedAt
        : null;

    onConflict({
      mutationType: mutation.type,
      strategy,
      decision,
      clientUpdatedAt,
      serverUpdatedAt: conflict.serverUpdatedAt ?? null,
      reason: conflict.reason,
    });
  }

  return decision;
}

