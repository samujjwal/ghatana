/**
 * SprintPlanningAidPanel — AI-Y10
 *
 * AI sprint planning aid: displays model-generated story-point estimation
 * and risk signals for a given sprint, helping teams calibrate velocity
 * and surface potential blockers before the sprint starts.
 *
 * ## Data contract
 * `GET /api/projects/:projectId/sprints/:sprintId/planning-aid`
 * ```json
 * {
 *   "sprintId": "sp-1",
 *   "estimatedVelocity": 42,
 *   "capacityPoints": 40,
 *   "overloadRisk": "high",
 *   "risks": [
 *     { "id": "r1", "description": "Story SP-44 has no acceptance criteria", "severity": "high" },
 *     { "id": "r2", "description": "3 stories blocked by external dependency", "severity": "medium" }
 *   ],
 *   "modelSource": "model",
 *   "confidence": 0.78
 * }
 * ```
 *
 * @doc.type component
 * @doc.purpose AI sprint estimation and risk surface for planning
 * @doc.layer product
 * @doc.pattern Data Display
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Loader2,
  AlertCircle,
  Zap,
  ShieldAlert,
  TrendingUp,
  TrendingDown,
  Minus,
} from 'lucide-react';
import { AIAssistLabel } from '../ai/AIAssistLabel';
import type { AIAssistSource } from '../ai/AIAssistLabel';

// ── Types ─────────────────────────────────────────────────────────────────────

export type RiskSeverity = 'high' | 'medium' | 'low';
export type OverloadRisk = 'high' | 'medium' | 'low' | 'none';

export interface SprintRisk {
  id: string;
  description: string;
  severity: RiskSeverity;
}

export interface SprintPlanningAidData {
  sprintId: string;
  /** Model-estimated velocity in story points. */
  estimatedVelocity: number;
  /** Team capacity in story points for this sprint. */
  capacityPoints: number;
  /** Overall overload risk. */
  overloadRisk: OverloadRisk;
  /** Individual risk items. */
  risks: SprintRisk[];
  /** Provenance: rule-based or model-based. */
  modelSource: AIAssistSource;
  /** Confidence score 0–1. */
  confidence: number;
}

export interface SprintPlanningAidPanelProps {
  projectId: string;
  sprintId: string;
  className?: string;
}

// ── API ────────────────────────────────────────────────────────────────────────

function buildUrl(projectId: string, sprintId: string): string {
  const meta = import.meta as ImportMeta & { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } };
  const base =
    meta.env?.DEV === true
      ? (meta.env.VITE_API_ORIGIN ?? 'http://localhost:8080')
      : '';
  return `${base}/api/projects/${projectId}/sprints/${sprintId}/planning-aid`;
}

async function fetchPlanningAid(projectId: string, sprintId: string): Promise<SprintPlanningAidData> {
  const res = await fetch(buildUrl(projectId, sprintId), { credentials: 'include' });
  if (!res.ok) throw new Error(`Planning aid fetch failed: ${res.status}`);
  return res.json() as Promise<SprintPlanningAidData>;
}

// ── Sub-components ─────────────────────────────────────────────────────────────

const RISK_COLOR: Record<RiskSeverity, string> = {
  high: 'text-destructive bg-destructive-bg border-destructive-border',
  medium: 'text-warning-color bg-warning-bg border-warning-border',
  low: 'text-muted bg-surface border-border',
};

const OVERLOAD_ICON: Record<OverloadRisk, React.ReactNode> = {
  high: <TrendingUp className="h-4 w-4 text-destructive" aria-hidden="true" />,
  medium: <TrendingUp className="h-4 w-4 text-warning-color" aria-hidden="true" />,
  low: <TrendingDown className="h-4 w-4 text-success-color" aria-hidden="true" />,
  none: <Minus className="h-4 w-4 text-muted" aria-hidden="true" />,
};

function RiskItem({ risk }: { risk: SprintRisk }) {
  return (
    <div
      data-testid={`sprint-risk-${risk.id}`}
      className={`flex items-start gap-2 rounded border px-3 py-2 text-xs ${RISK_COLOR[risk.severity]}`}
    >
      <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>{risk.description}</span>
    </div>
  );
}

// ── Main component ─────────────────────────────────────────────────────────────

export function SprintPlanningAidPanel({ projectId, sprintId, className }: SprintPlanningAidPanelProps) {
  const enabled = Boolean(projectId) && Boolean(sprintId);

  const { data, isLoading, isError } = useQuery<SprintPlanningAidData>({
    queryKey: ['sprint-planning-aid', projectId, sprintId],
    queryFn: () => fetchPlanningAid(projectId, sprintId),
    enabled,
    staleTime: 120_000,
  });

  if (!enabled) return null;

  if (isLoading) {
    return (
      <div data-testid="sprint-aid-loading" className="flex items-center gap-2 p-4 text-sm text-muted">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
        Analysing sprint…
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div data-testid="sprint-aid-error" className="flex items-center gap-2 p-4 text-sm text-destructive">
        <AlertCircle className="h-4 w-4" aria-hidden="true" />
        Could not load sprint planning aid.
      </div>
    );
  }

  const delta = data.estimatedVelocity - data.capacityPoints;

  return (
    <section
      data-testid="sprint-aid-panel"
      aria-label="AI sprint planning aid"
      className={['space-y-3', className].filter(Boolean).join(' ')}
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
          <Zap className="h-4 w-4" aria-hidden="true" />
          Sprint planning aid
        </div>
        <AIAssistLabel source={data.modelSource} label={`${Math.round(data.confidence * 100)}% confidence`} />
      </div>

      {/* Velocity vs capacity */}
      <div
        data-testid="sprint-velocity-summary"
        className="flex items-center gap-3 rounded-lg border border-border bg-surface p-3"
      >
        {OVERLOAD_ICON[data.overloadRisk]}
        <div className="text-sm">
          <span className="font-medium text-foreground">
            Estimated velocity: {data.estimatedVelocity} pts
          </span>{' '}
          <span className="text-muted">/ Capacity: {data.capacityPoints} pts</span>
          {delta !== 0 && (
            <span className={delta > 0 ? 'ml-1 text-destructive' : 'ml-1 text-success-color'}>
              ({delta > 0 ? '+' : ''}{delta})
            </span>
          )}
        </div>
      </div>

      {/* Risk items */}
      {data.risks.length > 0 && (
        <div className="space-y-1.5">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted">
            Risks ({data.risks.length})
          </p>
          {data.risks.map((r) => (
            <RiskItem key={r.id} risk={r} />
          ))}
        </div>
      )}

      {data.risks.length === 0 && (
        <p data-testid="sprint-no-risks" className="text-xs text-muted">
          No significant risks detected for this sprint.
        </p>
      )}
    </section>
  );
}
