/**
 * AI Interaction State Atoms
 *
 * Jotai atoms for copilot session management, AI insight caching, and
 * prediction data. All atoms use @ghatana/state factory methods.
 *
 * @module state/aiAtoms
 * @doc.type module
 * @doc.purpose AI interaction and copilot state atoms
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';

import { createAtom } from '@ghatana/state';

// ============================================================================
// Types
// ============================================================================

export interface CopilotMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export interface CopilotSession {
  id: string;
  messages: CopilotMessage[];
  context?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface AIInsight {
  id: string;
  type: string;
  category: string;
  severity: string;
  title: string;
  description: string;
  confidence: number;
  actionItems: string[];
  projectId?: string;
  itemId?: string;
  createdAt: string;
}

export interface AIPrediction {
  id: string;
  type: string;
  target: string;
  probability: number;
  timeframe: string;
  description: string;
  factors: string[];
  createdAt: string;
}

// ============================================================================
// Copilot atoms
// ============================================================================

/**
 * Active copilot session, or null when no session is in progress.
 */
export const copilotSessionAtom = createAtom<CopilotSession | null>(
  'ai:copilot:session',
  null,
  'Active copilot session'
);

/**
 * Whether the copilot is currently generating a response.
 */
export const copilotLoadingAtom = createAtom<boolean>(
  'ai:copilot:loading',
  false,
  'Copilot response loading flag'
);

/**
 * Last copilot error, or null.
 */
export const copilotErrorAtom = createAtom<Error | null>(
  'ai:copilot:error',
  null,
  'Copilot error state'
);

// ============================================================================
// AI Insights atoms
// ============================================================================

/**
 * AI insights for the currently active project.
 */
export const aiInsightsAtom = createAtom<AIInsight[]>(
  'ai:insights',
  [],
  'AI insights for the current project'
);

/**
 * Whether AI insights are being fetched.
 */
export const aiInsightsLoadingAtom = createAtom<boolean>(
  'ai:insights:loading',
  false,
  'AI insights loading flag'
);

// ============================================================================
// AI Predictions atoms
// ============================================================================

/**
 * AI predictions for the currently active project.
 */
export const aiPredictionsAtom = createAtom<AIPrediction[]>(
  'ai:predictions',
  [],
  'AI predictions for the current project'
);

// ============================================================================
// Action atoms
// ============================================================================

/**
 * Write-only atom that appends a message to the active copilot session.
 */
export const appendCopilotMessageAtom = atom(
  null,
  (get, set, message: CopilotMessage) => {
    const sAtom = copilotSessionAtom;
    const session = get(sAtom);
    if (!session) return;
    const updated: CopilotSession = {
      ...session,
      messages: [...session.messages, message],
      updatedAt: new Date().toISOString(),
    };
    set(sAtom, updated);
  }
);

/**
 * Write-only atom that clears the copilot session.
 */
export const clearCopilotSessionAtom = atom(null, (_get, set) => {
  set(copilotSessionAtom, null);
  set(copilotErrorAtom, null);
});

/**
 * Write-only atom that dismisses (removes) a single AI insight by ID.
 */
export const dismissInsightAtom = atom(null, (get, set, insightId: string) => {
  const iAtom = aiInsightsAtom;
  const insights = get(iAtom);
  set(
    iAtom,
    insights.filter((i) => i.id !== insightId)
  );
});
