/**
 * PhaseGateDialog Component
 *
 * Non-blocking dialog shown when a user tries to navigate to a phase they
 * haven't unlocked yet. Displays missing artifacts, gate status, and an
 * optional AI readiness assessment (AI-Y3).
 *
 * @doc.type component
 * @doc.purpose Phase-gate enforcement UI with AI readiness
 * @doc.layer product
 * @doc.pattern Dialog Component
 */

import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Chip,
  Typography,
} from '@ghatana/design-system';
import {
  AlertTriangle,
  Brain,
  CheckCircle,
  ChevronRight,
  Lock,
  RefreshCw,
  X,
} from 'lucide-react';

import type { AiReadinessAssessment, IaPhase } from '@/hooks/usePhaseGate';
import type { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

// ─────────────────────────────────────────────────────────────────────────────
// Props
// ─────────────────────────────────────────────────────────────────────────────

export interface PhaseGateDialogProps {
  /** The phase the user is trying to navigate to. */
  targetPhase: IaPhase;
  /** Artifact kinds that are blocking the gate. */
  missingArtifacts: LifecycleArtifactKind[];
  /** AI readiness assessment result, if available. */
  aiAssessment: AiReadinessAssessment | undefined;
  /** Whether the AI assessment is currently loading. */
  isAiAssessing: boolean;
  /** Trigger an AI readiness assessment. */
  onRequestAiAssessment: () => void;
  /** Dismiss the dialog. */
  onClose: () => void;
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

const PHASE_LABEL: Record<IaPhase, string> = {
  intent: 'Intent',
  shape: 'Shape',
  validate: 'Validate',
  generate: 'Generate',
  run: 'Run',
  observe: 'Observe',
  learn: 'Learn',
  evolve: 'Evolve',
};

function ReadinessScore({ score }: { score: number }): React.ReactElement {
  const color =
    score >= 80
      ? 'text-emerald-600 dark:text-emerald-400'
      : score >= 50
        ? 'text-warning-color dark:text-warning-color'
        : 'text-destructive dark:text-destructive';

  return (
    <Box className="flex items-center gap-2">
      <Typography className={`text-3xl font-bold tabular-nums ${color}`}>
        {score}
      </Typography>
      <Typography className="text-sm text-text-secondary">/ 100</Typography>
    </Box>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Main component
// ─────────────────────────────────────────────────────────────────────────────

/**
 * PhaseGateDialog
 *
 * Shown as an overlay when a user navigates to a locked phase.
 * Never blocks access from admin bypasses — the dialog is informational
 * but enforcement happens at the NavLink level.
 *
 * @example
 * ```tsx
 * {!canAdvance && (
 *   <PhaseGateDialog
 *     targetPhase="shape"
 *     missingArtifacts={missingArtifacts}
 *     aiAssessment={aiAssessment}
 *     isAiAssessing={isAiAssessing}
 *     onRequestAiAssessment={requestAiAssessment}
 *     onClose={() => navigate(-1)}
 *   />
 * )}
 * ```
 */
export const PhaseGateDialog: React.FC<PhaseGateDialogProps> = ({
  targetPhase,
  missingArtifacts,
  aiAssessment,
  isAiAssessing,
  onRequestAiAssessment,
  onClose,
}) => {
  return (
    /* Backdrop */
    <Box
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="phase-gate-title"
      data-testid="phase-gate-dialog"
    >
      <Card className="w-full max-w-lg shadow-xl">
        <CardContent className="p-0">
          {/* Header */}
          <Box className="flex items-start justify-between border-b border-divider px-5 py-4">
            <Box className="flex items-center gap-2">
              <Lock className="h-5 w-5 text-warning-color" aria-hidden="true" />
              <Box>
                <Typography
                  id="phase-gate-title"
                  className="text-base font-semibold"
                >
                  Phase gate: {PHASE_LABEL[targetPhase]}
                </Typography>
                <Typography className="text-xs text-text-secondary">
                  Complete the required artifacts to unlock this phase.
                </Typography>
              </Box>
            </Box>
            <button
              type="button"
              onClick={onClose}
              className="rounded p-1 text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors"
              aria-label="Close"
              data-testid="phase-gate-close"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </Box>

          {/* Missing artifacts */}
          <Box className="px-5 py-4 space-y-3">
            {missingArtifacts.length === 0 ? (
              <Box className="flex items-center gap-2 text-emerald-600 dark:text-emerald-400">
                <CheckCircle className="h-4 w-4" aria-hidden="true" />
                <Typography className="text-sm">
                  All required artifacts are complete.
                </Typography>
              </Box>
            ) : (
              <>
                <Box className="flex items-center gap-1.5">
                  <AlertTriangle className="h-4 w-4 text-warning-color" aria-hidden="true" />
                  <Typography className="text-sm font-medium">
                    {missingArtifacts.length} artifact
                    {missingArtifacts.length !== 1 ? 's' : ''} required
                  </Typography>
                </Box>
                <Box
                  className="space-y-1.5"
                  data-testid="phase-gate-missing-list"
                >
                  {missingArtifacts.map((kind) => (
                    <Box
                      key={kind}
                      className="flex items-center gap-2 rounded-md border border-warning-border dark:border-warning-border bg-warning-bg dark:bg-warning-bg/20 px-3 py-2"
                    >
                      <ChevronRight
                        className="h-3.5 w-3.5 shrink-0 text-warning-color"
                        aria-hidden="true"
                      />
                      <Typography className="text-sm capitalize">
                        {kind.replace(/_/g, ' ')}
                      </Typography>
                      <Chip
                        label="Required"
                        size="sm"
                        className="ml-auto bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color text-xs"
                      />
                    </Box>
                  ))}
                </Box>
              </>
            )}

            {/* AI assessment section */}
            <Box className="border-t border-divider pt-3">
              {!aiAssessment && !isAiAssessing && (
                <button
                  type="button"
                  onClick={onRequestAiAssessment}
                  className="flex items-center gap-2 text-sm text-primary hover:underline"
                  data-testid="phase-gate-ai-assess-btn"
                >
                  <Brain className="h-4 w-4" aria-hidden="true" />
                  Get AI readiness assessment
                </button>
              )}

              {isAiAssessing && (
                <Box className="flex items-center gap-2 text-text-secondary">
                  <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" />
                  <Typography className="text-sm">Assessing readiness…</Typography>
                </Box>
              )}

              {aiAssessment && !isAiAssessing && (
                <Box
                  className="space-y-3 rounded-md bg-grey-50 dark:bg-grey-900 p-3"
                  data-testid="phase-gate-ai-result"
                >
                  <Box className="flex items-start justify-between gap-4">
                    <Box>
                      <Typography className="text-xs font-semibold text-text-secondary mb-1">
                        AI Readiness Score
                      </Typography>
                      <ReadinessScore score={aiAssessment.score} />
                    </Box>
                    {aiAssessment.source === 'RULE' && (
                      <Chip
                        label="Rule-based"
                        size="sm"
                        className="bg-grey-200 text-grey-600 text-xs"
                        title="LLM unavailable — rule-based fallback"
                      />
                    )}
                  </Box>

                  {aiAssessment.rationale && (
                    <Typography className="text-xs text-text-secondary italic">
                      {aiAssessment.rationale}
                    </Typography>
                  )}

                  {aiAssessment.nextSteps.length > 0 && (
                    <Box>
                      <Typography className="text-xs font-semibold mb-1">
                        Suggested next steps
                      </Typography>
                      <Box
                        className="space-y-1"
                        data-testid="phase-gate-ai-next-steps"
                      >
                        {aiAssessment.nextSteps.map((step, i) => (
                          <Box key={i} className="flex gap-1.5 text-xs text-text-secondary">
                            <span className="mt-0.5 shrink-0 text-primary">•</span>
                            <span>{step}</span>
                          </Box>
                        ))}
                      </Box>
                    </Box>
                  )}
                </Box>
              )}
            </Box>
          </Box>

          {/* Footer */}
          <Box className="flex justify-end border-t border-divider px-5 py-3">
            <button
              type="button"
              onClick={onClose}
              className="rounded bg-grey-100 dark:bg-grey-800 px-4 py-2 text-sm font-medium text-text-primary hover:bg-grey-200 dark:hover:bg-grey-700 transition-colors"
            >
              Go back
            </button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default PhaseGateDialog;
