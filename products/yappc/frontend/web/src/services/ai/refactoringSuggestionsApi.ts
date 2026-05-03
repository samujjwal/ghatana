/**
 * Refactoring Suggestions API Client (F-Y017 / AI-Y4)
 *
 * Implements the full simulate-then-apply lifecycle for refactor suggestions:
 * - GET  /refactoring-suggestions/{designId}     — list suggestions
 * - POST /refactoring-suggestions/{designId}/simulate/{suggestionId} — dry-run diff
 * - POST /refactoring-suggestions/{designId}/apply/{suggestionId}    — apply
 * - POST /refactoring-suggestions/{designId}/undo/{suggestionId}     — undo
 *
 * @doc.type service
 * @doc.purpose Refactoring suggestion CRUD and lifecycle (simulate → apply → undo)
 * @doc.layer product
 * @doc.pattern API Client
 */

import { parseJsonResponse } from '@/lib/http';

const importMetaEnv = import.meta as ImportMeta & {
  env?: { DEV?: boolean; VITE_API_ORIGIN?: string };
};

const API_BASE_URL = importMetaEnv.env?.DEV
  ? `${importMetaEnv.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
  : '/api';

// ── Types ──────────────────────────────────────────────────────────────────────

export type RefactorSuggestionStatus =
  | 'PENDING'
  | 'SIMULATED'
  | 'APPLIED'
  | 'UNDONE';

export interface RefactorFileDiff {
  path: string;
  /** Unified diff format */
  diff: string;
  linesAdded: number;
  linesRemoved: number;
}

export interface RefactorSuggestion {
  id: string;
  designId: string;
  title: string;
  rationale: string;
  confidence: number; // 0.0–1.0
  /** Files that will be affected by applying this suggestion */
  affectedFiles: string[];
  status: RefactorSuggestionStatus;
  /** Present after simulate has been called */
  simulatedDiff?: RefactorFileDiff[];
  createdAt: string;
  appliedAt?: string;
  undoneAt?: string;
}

export interface RefactorSuggestionsResponse {
  designId: string;
  suggestions: RefactorSuggestion[];
}

export interface SimulateResult {
  suggestionId: string;
  diff: RefactorFileDiff[];
  estimatedRiskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  canApply: boolean;
  /** Human-readable warnings (e.g. merge conflicts) */
  warnings: string[];
}

export interface ApplyResult {
  suggestionId: string;
  appliedAt: string;
  affectedFiles: string[];
}

export interface UndoResult {
  suggestionId: string;
  undoneAt: string;
  restoredFiles: string[];
}

// ── API Functions ─────────────────────────────────────────────────────────────

export async function listRefactorSuggestions(
  designId: string
): Promise<RefactorSuggestionsResponse> {
  const response = await fetch(
    `${API_BASE_URL}/refactoring-suggestions/${encodeURIComponent(designId)}`,
    { credentials: 'include' }
  );
  return parseJsonResponse<RefactorSuggestionsResponse>(response, 'list refactor suggestions');
}

export async function simulateRefactorSuggestion(
  designId: string,
  suggestionId: string
): Promise<SimulateResult> {
  const response = await fetch(
    `${API_BASE_URL}/refactoring-suggestions/${encodeURIComponent(designId)}/simulate/${encodeURIComponent(suggestionId)}`,
    { method: 'POST', credentials: 'include' }
  );
  return parseJsonResponse<SimulateResult>(response, 'simulate refactor suggestion');
}

export async function applyRefactorSuggestion(
  designId: string,
  suggestionId: string
): Promise<ApplyResult> {
  const response = await fetch(
    `${API_BASE_URL}/refactoring-suggestions/${encodeURIComponent(designId)}/apply/${encodeURIComponent(suggestionId)}`,
    { method: 'POST', credentials: 'include' }
  );
  return parseJsonResponse<ApplyResult>(response, 'apply refactor suggestion');
}

export async function undoRefactorSuggestion(
  designId: string,
  suggestionId: string
): Promise<UndoResult> {
  const response = await fetch(
    `${API_BASE_URL}/refactoring-suggestions/${encodeURIComponent(designId)}/undo/${encodeURIComponent(suggestionId)}`,
    { method: 'POST', credentials: 'include' }
  );
  return parseJsonResponse<UndoResult>(response, 'undo refactor suggestion');
}
