/**
 * StrideSuggestionPanel — AI-Y11
 *
 * Surfaces AI-generated STRIDE threat suggestions for a given threat model,
 * helping teams identify threats they may have missed.
 *
 * ## Data contract
 * `GET /api/threat-models/:modelId/stride-suggestions`
 * ```json
 * {
 *   "modelId": "tm-1",
 *   "suggestions": [
 *     {
 *       "id": "s1",
 *       "category": "Spoofing",
 *       "threat": "Attacker impersonates admin via stolen session cookie.",
 *       "mitigationHint": "Rotate session tokens after privilege escalation.",
 *       "confidence": 0.85,
 *       "source": "model"
 *     }
 *   ]
 * }
 * ```
 *
 * @doc.type component
 * @doc.purpose AI-suggested STRIDE threat expansion for threat models
 * @doc.layer product
 * @doc.pattern Data Display
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Loader2, AlertCircle, ShieldPlus } from 'lucide-react';
import { AIAssistLabel } from '../../ai/AIAssistLabel';
import type { AIAssistSource } from '../../ai/AIAssistLabel';

// ── Types ─────────────────────────────────────────────────────────────────────

export type StrideCategory =
  | 'Spoofing'
  | 'Tampering'
  | 'Repudiation'
  | 'InformationDisclosure'
  | 'DenialOfService'
  | 'ElevationOfPrivilege';

export interface StrideSuggestion {
  id: string;
  category: StrideCategory;
  threat: string;
  mitigationHint?: string;
  confidence: number;
  source: AIAssistSource;
}

export interface StrideSuggestionData {
  modelId: string;
  suggestions: StrideSuggestion[];
}

export interface StrideSuggestionPanelProps {
  /** Threat model ID. */
  modelId: string;
  /** Called when the user wants to add a suggestion to the model. */
  onAddThreat?: (suggestion: StrideSuggestion) => void;
  className?: string;
}

// ── Category colours ───────────────────────────────────────────────────────────

const CATEGORY_COLOR: Record<StrideCategory, string> = {
  Spoofing: 'bg-info-bg text-info-color border-info-border',
  Tampering: 'bg-warning-bg text-warning-color border-warning-border',
  Repudiation: 'bg-info-bg text-info-color border-info-border',
  InformationDisclosure: 'bg-warning-bg text-warning-color border-warning-border',
  DenialOfService: 'bg-destructive-bg text-destructive border-destructive-border',
  ElevationOfPrivilege: 'bg-rose-50 text-rose-700 border-rose-200',
};

const STRIDE_ABBREV: Record<StrideCategory, string> = {
  Spoofing: 'S',
  Tampering: 'T',
  Repudiation: 'R',
  InformationDisclosure: 'I',
  DenialOfService: 'D',
  ElevationOfPrivilege: 'E',
};

// ── API ────────────────────────────────────────────────────────────────────────

function buildUrl(modelId: string): string {
  const meta = import.meta as ImportMeta & { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } };
  const base =
    meta.env?.DEV === true
      ? (meta.env.VITE_API_ORIGIN ?? 'http://localhost:8080')
      : '';
  return `${base}/api/threat-models/${modelId}/stride-suggestions`;
}

async function fetchSuggestions(modelId: string): Promise<StrideSuggestionData> {
  const res = await fetch(buildUrl(modelId), { credentials: 'include' });
  if (!res.ok) throw new Error(`STRIDE suggestions failed: ${res.status}`);
  return res.json() as Promise<StrideSuggestionData>;
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function SuggestionItem({
  suggestion,
  onAdd,
}: {
  suggestion: StrideSuggestion;
  onAdd?: (s: StrideSuggestion) => void;
}) {
  const colorClass = CATEGORY_COLOR[suggestion.category];

  return (
    <div
      data-testid={`stride-suggestion-${suggestion.id}`}
      className="rounded-lg border border-border bg-surface p-3 space-y-1.5"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-1.5">
          <span
            className={`rounded border px-1.5 py-0.5 text-xs font-bold ${colorClass}`}
            title={suggestion.category}
          >
            {STRIDE_ABBREV[suggestion.category]}
          </span>
          <span className="text-xs font-medium text-muted">{suggestion.category}</span>
        </div>
        <AIAssistLabel
          source={suggestion.source}
          label={`${Math.round(suggestion.confidence * 100)}%`}
        />
      </div>

      <p className="text-sm text-foreground">{suggestion.threat}</p>

      {suggestion.mitigationHint && (
        <p className="text-xs text-muted italic">{suggestion.mitigationHint}</p>
      )}

      {onAdd && (
        <button
          data-testid={`stride-add-${suggestion.id}`}
          onClick={() => onAdd(suggestion)}
          className="mt-1 rounded bg-primary px-2 py-0.5 text-xs font-medium text-primary-foreground hover:bg-primary/90"
        >
          + Add to model
        </button>
      )}
    </div>
  );
}

// ── Main component ─────────────────────────────────────────────────────────────

export function StrideSuggestionPanel({ modelId, onAddThreat, className }: StrideSuggestionPanelProps) {
  const { data, isLoading, isError } = useQuery<StrideSuggestionData>({
    queryKey: ['stride-suggestions', modelId],
    queryFn: () => fetchSuggestions(modelId),
    enabled: modelId.length > 0,
    staleTime: 120_000,
  });

  if (!modelId) return null;

  if (isLoading) {
    return (
      <div data-testid="stride-loading" className="flex items-center gap-2 p-4 text-sm text-muted">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
        Generating threat suggestions…
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div data-testid="stride-error" className="flex items-center gap-2 p-4 text-sm text-destructive">
        <AlertCircle className="h-4 w-4" aria-hidden="true" />
        Could not load STRIDE suggestions.
      </div>
    );
  }

  if (data.suggestions.length === 0) {
    return (
      <p data-testid="stride-empty" className="p-4 text-sm text-muted">
        No additional threats suggested.
      </p>
    );
  }

  return (
    <section
      data-testid="stride-suggestion-panel"
      aria-label="AI STRIDE threat suggestions"
      className={['space-y-3', className].filter(Boolean).join(' ')}
    >
      <div className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
        <ShieldPlus className="h-4 w-4" aria-hidden="true" />
        Suggested threats ({data.suggestions.length})
      </div>

      <div className="space-y-2">
        {data.suggestions.map((s) => (
          <SuggestionItem key={s.id} suggestion={s} onAdd={onAddThreat} />
        ))}
      </div>
    </section>
  );
}
