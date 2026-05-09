/**
 * PostmortemDraftPanel — AI-Y14
 *
 * Generates a structured postmortem document from the incident timeline
 * captured in the incident management surface. The model infers root cause,
 * contributing factors, and follow-up action items.
 *
 * ## Data contract
 * `POST /api/incidents/:incidentId/generate-postmortem`
 * Body: `{}` (context inferred from incident data)
 * Response:
 * ```json
 * {
 *   "incidentId": "inc-1",
 *   "draft": {
 *     "title": "Postmortem: API gateway latency spike 2026-04-27",
 *     "severity": "P2",
 *     "summary": "...",
 *     "rootCause": "...",
 *     "contributingFactors": ["..."],
 *     "timeline": [{ "time": "12:00 UTC", "event": "Spike detected" }],
 *     "actionItems": [{ "id": "a1", "description": "Add rate-limit alerts", "owner": "Platform" }],
 *     "source": "model",
 *     "confidence": 0.79
 *   }
 * }
 * ```
 *
 * @doc.type component
 * @doc.purpose Generate AI postmortem drafts from incident timelines
 * @doc.layer product
 * @doc.pattern Command / Data Display
 */

import React, { useState } from 'react';
import { Loader2, AlertCircle, FileWarning, Sparkles } from 'lucide-react';
import { AIAssistLabel } from '../ai/AIAssistLabel';
import type { AIAssistSource } from '../ai/AIAssistLabel';
import { Button } from '../ui/Button';
import { useI18n } from '../../i18n/I18nProvider';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface PostmortemTimelineEntry {
  time: string;
  event: string;
}

export interface PostmortemActionItem {
  id: string;
  description: string;
  owner?: string;
}

export interface PostmortemDraft {
  title: string;
  severity: string;
  summary: string;
  rootCause: string;
  contributingFactors: string[];
  timeline: PostmortemTimelineEntry[];
  actionItems: PostmortemActionItem[];
  source: AIAssistSource;
  confidence: number;
}

export interface PostmortemDraftResult {
  incidentId: string;
  draft: PostmortemDraft;
}

export interface PostmortemDraftPanelProps {
  /** Incident ID to generate a postmortem for. */
  incidentId: string;
  /** Called when user accepts the draft. */
  onAccept?: (draft: PostmortemDraft) => void;
  className?: string;
}

// ── API ────────────────────────────────────────────────────────────────────────

function buildUrl(incidentId: string): string {
  const meta = import.meta as ImportMeta & { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } };
  const base =
    meta.env?.DEV === true
      ? (meta.env.VITE_API_ORIGIN ?? 'http://localhost:8080')
      : '';
  return `${base}/api/incidents/${incidentId}/generate-postmortem`;
}

async function generatePostmortem(incidentId: string): Promise<PostmortemDraftResult> {
  const res = await fetch(buildUrl(incidentId), {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
  });
  if (!res.ok) throw new Error(`Postmortem generation failed: ${res.status}`);
  return res.json() as Promise<PostmortemDraftResult>;
}

// ── Main component ─────────────────────────────────────────────────────────────

type GenState = 'idle' | 'loading' | 'done' | 'error';

export function PostmortemDraftPanel({ incidentId, onAccept, className }: PostmortemDraftPanelProps) {
  const [state, setState] = useState<GenState>('idle');
  const [draft, setDraft] = useState<PostmortemDraft | null>(null);
  const { t } = useI18n();

  if (!incidentId) return null;

  async function handleGenerate() {
    setState('loading');
    setDraft(null);
    try {
      const result = await generatePostmortem(incidentId);
      setDraft(result.draft);
      setState('done');
    } catch {
      setState('error');
    }
  }

  return (
    <section
      data-testid="postmortem-draft-panel"
      aria-label={t('postmortem.aiDraft')}
      className={['space-y-3', className].filter(Boolean).join(' ')}
    >
      {state === 'idle' && (
        <Button
          data-testid="postmortem-generate-btn"
          onClick={() => void handleGenerate()}
          className="flex items-center gap-1.5 rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium text-foreground hover:bg-accent"
          variant="outline"
          size="sm"
        >
          <Sparkles className="h-4 w-4" aria-hidden="true" />
          Generate postmortem draft
        </Button>
      )}

      {state === 'loading' && (
        <div data-testid="postmortem-loading" className="flex items-center gap-2 text-sm text-muted">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          Generating postmortem…
        </div>
      )}

      {state === 'error' && (
        <div className="space-y-2">
          <div data-testid="postmortem-error" className="flex items-center gap-2 text-sm text-destructive">
            <AlertCircle className="h-4 w-4" aria-hidden="true" />
            Could not generate postmortem draft.
          </div>
          <Button onClick={() => setState('idle')} className="text-xs text-muted underline" variant="link" size="sm">
            Try again
          </Button>
        </div>
      )}

      {state === 'done' && draft && (
        <div data-testid="postmortem-draft-result" className="space-y-3 rounded-lg border border-border bg-surface p-4">
          {/* Header */}
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-center gap-1.5">
              <FileWarning className="h-4 w-4 text-muted" aria-hidden="true" />
              <span data-testid="postmortem-title" className="text-sm font-semibold text-foreground">
                {draft.title}
              </span>
            </div>
            <AIAssistLabel source={draft.source} label={`${Math.round(draft.confidence * 100)}%`} />
          </div>

          <div className="space-y-1.5 text-xs text-muted">
            <p>
              <strong className="text-foreground">Severity:</strong> {draft.severity}
            </p>
            <p>
              <strong className="text-foreground">Summary:</strong> {draft.summary}
            </p>
            <p>
              <strong className="text-foreground">Root cause:</strong> {draft.rootCause}
            </p>
          </div>

          {/* Contributing factors */}
          {draft.contributingFactors.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-foreground">Contributing factors</p>
              <ul className="mt-1 list-disc pl-4 text-xs text-muted space-y-0.5">
                {draft.contributingFactors.map((f, i) => (
                  <li key={i}>{f}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Timeline */}
          {draft.timeline.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-foreground">Timeline</p>
              <ol className="mt-1 space-y-0.5 text-xs text-muted">
                {draft.timeline.map((e, i) => (
                  <li key={i} data-testid={`postmortem-timeline-${i}`}>
                    <span className="font-medium text-foreground">{e.time}</span> — {e.event}
                  </li>
                ))}
              </ol>
            </div>
          )}

          {/* Action items */}
          {draft.actionItems.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-foreground">Action items</p>
              <ul className="mt-1 space-y-0.5 text-xs text-muted">
                {draft.actionItems.map((a) => (
                  <li key={a.id} data-testid={`postmortem-action-${a.id}`}>
                    {a.description}
                    {a.owner && <span className="ml-1 text-muted">({a.owner})</span>}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="flex gap-2 pt-1">
            {onAccept && (
              <Button
                data-testid="postmortem-accept-btn"
                onClick={() => onAccept(draft)}
                className="rounded bg-primary px-3 py-1 text-xs font-medium text-primary-foreground hover:bg-primary/90"
                size="sm"
              >
                Accept draft
              </Button>
            )}
            <Button
              onClick={() => setState('idle')}
              data-testid="postmortem-regenerate-btn"
              className="rounded border border-border px-3 py-1 text-xs font-medium text-muted hover:bg-accent"
              variant="outline"
              size="sm"
            >
              Regenerate
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}
