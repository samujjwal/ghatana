/**
 * usePhaseGate Hook
 *
 * Evaluates whether the current project can advance to a target IA phase.
 * Wraps PhaseGateService and exposes gate status, missing artifacts, and an
 * AI-powered readiness assessment (AI-Y3 / F-Y042).
 *
 * The AI readiness assessment is fire-and-forget on first call; it does NOT
 * block the gate evaluation. If the LLM is unavailable the hook degrades to
 * rule-only gate results.
 *
 * @doc.type hook
 * @doc.purpose Phase-gate check + AI readiness assessment
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router';

import type { GateStatus, ItemSummary } from '@/shared/types/phase-gates';
import { validatePhaseTransition } from '@/shared/types/phase-gates';
import type { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

/** 8-phase IA navigation phase identifiers */
export type IaPhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'run'
  | 'observe'
  | 'learn'
  | 'evolve';

export interface AiReadinessAssessment {
  /** 0–100 readiness score from the LLM. */
  score: number;
  /** Items the LLM identified as missing or incomplete. */
  missingItems: string[];
  /** Suggested next steps to achieve readiness. */
  nextSteps: string[];
  /** LLM rationale summary. */
  rationale: string;
  /** Source of the assessment result. */
  source: 'MODEL' | 'RULE';
}

export interface PhaseGateResult {
  /** Whether the project may navigate to the target phase. */
  canAdvance: boolean;
  /** Missing artifact kinds blocking the gate. */
  missingArtifacts: LifecycleArtifactKind[];
  /** Full gate evaluation result (may be undefined while loading). */
  gateStatus: GateStatus | undefined;
  /** Whether the gate evaluation is still loading. */
  isLoading: boolean;
  /** AI readiness assessment result (undefined until requested). */
  aiAssessment: AiReadinessAssessment | undefined;
  /** Whether the AI assessment is loading. */
  isAiAssessing: boolean;
  /** Request the AI readiness assessment for the target phase. */
  requestAiAssessment: () => void;
  /** Error from gate evaluation or AI assessment. */
  error: string | undefined;
}

// ─────────────────────────────────────────────────────────────────────────────
// AI readiness assessment
// ─────────────────────────────────────────────────────────────────────────────

interface AiReadinessRequest {
  projectId: string;
  targetPhase: IaPhase;
  artifacts: ItemSummary[];
}

interface AiReadinessApiResponse {
  score: number;
  missingItems: string[];
  nextSteps: string[];
  rationale: string;
}

async function fetchAiReadiness(req: AiReadinessRequest): Promise<AiReadinessAssessment> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);

  try {
    const response = await fetch('/api/ai/phase-gate-readiness', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      signal: controller.signal,
      body: JSON.stringify(req),
    });

    if (!response.ok) {
      throw new Error(`AI readiness API returned ${response.status}`);
    }

    const data: AiReadinessApiResponse = await (response.json() as Promise<AiReadinessApiResponse>);
    return { ...data, source: 'MODEL' };
  } catch {
    // Graceful fallback — rule-based readiness score
    const missing = req.artifacts.filter((a) => a.status !== 'complete' && a.status !== 'validated').length;
    const total = req.artifacts.length || 1;
    const score = Math.round(((total - missing) / total) * 100);
    return {
      score,
      missingItems: req.artifacts
        .filter((a) => a.status !== 'complete' && a.status !== 'validated')
        .map((a) => a.title),
      nextSteps: [
        'Complete all required artifacts for this phase.',
        'Ensure each artifact has been validated or marked complete.',
      ],
      rationale: 'LLM assessment unavailable — rule-based fallback applied.',
      source: 'RULE',
    };
  } finally {
    clearTimeout(timeout);
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Artifact fetching
// ─────────────────────────────────────────────────────────────────────────────

async function fetchProjectArtifacts(projectId: string): Promise<ItemSummary[]> {
  const response = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/artifacts`,
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (!response.ok) {
    throw new Error(`Artifact fetch failed: ${response.status}`);
  }
  return response.json() as Promise<ItemSummary[]>;
}

// ─────────────────────────────────────────────────────────────────────────────
// Hook
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Evaluates phase-gate readiness for navigating to a target IA phase.
 *
 * @param targetPhase - The IA phase the user wants to navigate to.
 *
 * @example
 * ```tsx
 * const { canAdvance, missingArtifacts, requestAiAssessment, aiAssessment } =
 *   usePhaseGate('shape');
 *
 * if (!canAdvance) {
 *   return <PhaseGateDialog missing={missingArtifacts} onRequestAi={requestAiAssessment} />;
 * }
 * ```
 */
export function usePhaseGate(targetPhase: IaPhase): PhaseGateResult {
  const { projectId } = useParams<{ projectId: string }>();

  const [aiAssessment, setAiAssessment] = useState<AiReadinessAssessment | undefined>(
    undefined
  );
  const [isAiAssessing, setIsAiAssessing] = useState(false);
  const [aiError, setAiError] = useState<string | undefined>(undefined);

  // Fetch project artifacts
  const {
    data: artifacts = [],
    isLoading,
    error: artifactsError,
  } = useQuery({
    queryKey: ['project-artifacts', projectId],
    queryFn: () =>
      projectId ? fetchProjectArtifacts(projectId) : Promise.resolve([]),
    enabled: !!projectId,
    staleTime: 60_000,
  });

  // Run rule-based gate validation (synchronous, derived from artifacts)
  const { gateStatus, missingArtifacts } = useMemo(() => {
    if (!projectId || artifacts.length === 0) {
      return { gateStatus: undefined, missingArtifacts: [] };
    }

    const artifactsByKind = Object.fromEntries(
      artifacts.map((a) => [a.artifactKind, a])
    ) as Record<LifecycleArtifactKind, ItemSummary>;

    // validatePhaseTransition expects LifecyclePhase — map IA phases
    const { gateStatus: gs } = validatePhaseTransition({
      projectId,
      currentPhase: targetPhase as Parameters<typeof validatePhaseTransition>[0]['currentPhase'],
      targetPhase: targetPhase as Parameters<typeof validatePhaseTransition>[0]['targetPhase'],
      lifecycleArtifactItemsByKind: artifactsByKind,
    });

    const missing: LifecycleArtifactKind[] = (gs?.validationResults ?? [])
      .filter((r) => !r.valid)
      .flatMap((r) => r.errors)
      .map((e) => e as LifecycleArtifactKind);

    return { gateStatus: gs, missingArtifacts: missing };
  }, [projectId, artifacts, targetPhase]);

  const canAdvance = gateStatus?.status === 'passed' || gateStatus?.status === 'bypassed';

  const requestAiAssessment = useCallback(() => {
    if (!projectId) return;
    setIsAiAssessing(true);
    setAiError(undefined);
    void fetchAiReadiness({ projectId, targetPhase, artifacts }).then(
      (result) => {
        setAiAssessment(result);
        setIsAiAssessing(false);
      },
      (err: unknown) => {
        setAiError(err instanceof Error ? err.message : 'AI assessment failed');
        setIsAiAssessing(false);
      }
    );
  }, [projectId, targetPhase, artifacts]);

  const error = aiError ?? (artifactsError instanceof Error ? artifactsError.message : undefined);

  return {
    canAdvance,
    missingArtifacts,
    gateStatus,
    isLoading,
    aiAssessment,
    isAiAssessing,
    requestAiAssessment,
    error,
  };
}
