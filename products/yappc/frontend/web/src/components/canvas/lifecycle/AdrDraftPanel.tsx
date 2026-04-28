/**
 * AdrDraftPanel — AI-Y12
 *
 * Generates an ADR (Architecture Decision Record) draft from the decisions
 * captured on a canvas node, using the backend LLM to produce a structured
 * draft the user can review, edit, and promote to a real ADR.
 *
 * ## Data contract
 * `POST /api/canvas/nodes/:nodeId/generate-adr`
 * Body: `{}` (no body needed — server infers context from the node)
 * Response:
 * ```json
 * {
 *   "nodeId": "node-1",
 *   "draft": {
 *     "title": "Use PostgreSQL for primary data store",
 *     "status": "Proposed",
 *     "context": "...",
 *     "decision": "...",
 *     "consequences": "...",
 *     "source": "model",
 *     "confidence": 0.82
 *   }
 * }
 * ```
 *
 * @doc.type component
 * @doc.purpose Generate ADR drafts from canvas decisions via AI
 * @doc.layer product
 * @doc.pattern Command / Data Display
 */

import React, { useState } from 'react';
import { Loader2, AlertCircle, FileText, Sparkles } from 'lucide-react';
import { AIAssistLabel } from '../ai/AIAssistLabel';
import type { AIAssistSource } from '../ai/AIAssistLabel';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface AdrDraft {
  title: string;
  status: string;
  context: string;
  decision: string;
  consequences: string;
  source: AIAssistSource;
  confidence: number;
}

export interface AdrDraftResult {
  nodeId: string;
  draft: AdrDraft;
}

export interface AdrDraftPanelProps {
  /** Canvas node ID to generate an ADR from. */
  nodeId: string;
  /** Called when the user accepts the draft (passes the draft for editing/saving). */
  onAcceptDraft?: (draft: AdrDraft) => void;
  className?: string;
}

// ── API ────────────────────────────────────────────────────────────────────────

function buildUrl(nodeId: string): string {
  const meta = import.meta as ImportMeta & { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } };
  const base =
    meta.env?.DEV === true
      ? (meta.env.VITE_API_ORIGIN ?? 'http://localhost:8080')
      : '';
  return `${base}/api/canvas/nodes/${nodeId}/generate-adr`;
}

async function generateAdrDraft(nodeId: string): Promise<AdrDraftResult> {
  const res = await fetch(buildUrl(nodeId), {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
  });
  if (!res.ok) throw new Error(`ADR generation failed: ${res.status}`);
  return res.json() as Promise<AdrDraftResult>;
}

// ── Main component ─────────────────────────────────────────────────────────────

type GenerationState = 'idle' | 'loading' | 'done' | 'error';

/**
 * Button-triggered ADR draft generator.  The user clicks "Generate ADR draft"
 * to trigger the model, then reviews and optionally accepts the result.
 */
export function AdrDraftPanel({ nodeId, onAcceptDraft, className }: AdrDraftPanelProps) {
  const [state, setState] = useState<GenerationState>('idle');
  const [draft, setDraft] = useState<AdrDraft | null>(null);

  if (!nodeId) return null;

  async function handleGenerate() {
    setState('loading');
    setDraft(null);
    try {
      const result = await generateAdrDraft(nodeId);
      setDraft(result.draft);
      setState('done');
    } catch {
      setState('error');
    }
  }

  return (
    <section
      data-testid="adr-draft-panel"
      aria-label="AI ADR draft generator"
      className={['space-y-3', className].filter(Boolean).join(' ')}
    >
      {/* Trigger */}
      {state === 'idle' && (
        <button
          data-testid="adr-generate-btn"
          onClick={() => void handleGenerate()}
          className="flex items-center gap-1.5 rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium text-foreground hover:bg-accent"
        >
          <Sparkles className="h-4 w-4" aria-hidden="true" />
          Generate ADR draft
        </button>
      )}

      {/* Loading */}
      {state === 'loading' && (
        <div data-testid="adr-loading" className="flex items-center gap-2 text-sm text-muted">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          Generating ADR draft…
        </div>
      )}

      {/* Error */}
      {state === 'error' && (
        <div className="space-y-2">
          <div data-testid="adr-error" className="flex items-center gap-2 text-sm text-destructive">
            <AlertCircle className="h-4 w-4" aria-hidden="true" />
            Could not generate ADR draft.
          </div>
          <button
            onClick={() => setState('idle')}
            className="text-xs text-muted underline"
          >
            Try again
          </button>
        </div>
      )}

      {/* Draft result */}
      {state === 'done' && draft && (
        <div data-testid="adr-draft-result" className="space-y-2 rounded-lg border border-border bg-surface p-3">
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-center gap-1.5">
              <FileText className="h-4 w-4 text-muted" aria-hidden="true" />
              <span data-testid="adr-draft-title" className="text-sm font-semibold text-foreground">
                {draft.title}
              </span>
            </div>
            <AIAssistLabel source={draft.source} label={`${Math.round(draft.confidence * 100)}% confidence`} />
          </div>

          <div className="space-y-1 text-xs text-muted">
            <p>
              <strong className="text-foreground">Status:</strong> {draft.status}
            </p>
            <p>
              <strong className="text-foreground">Context:</strong> {draft.context}
            </p>
            <p>
              <strong className="text-foreground">Decision:</strong> {draft.decision}
            </p>
            <p>
              <strong className="text-foreground">Consequences:</strong> {draft.consequences}
            </p>
          </div>

          <div className="flex gap-2 pt-1">
            {onAcceptDraft && (
              <button
                data-testid="adr-accept-btn"
                onClick={() => onAcceptDraft(draft)}
                className="rounded bg-primary px-3 py-1 text-xs font-medium text-primary-foreground hover:bg-primary/90"
              >
                Accept draft
              </button>
            )}
            <button
              data-testid="adr-regenerate-btn"
              onClick={() => setState('idle')}
              className="rounded border border-border px-3 py-1 text-xs font-medium text-muted hover:bg-accent"
            >
              Regenerate
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
