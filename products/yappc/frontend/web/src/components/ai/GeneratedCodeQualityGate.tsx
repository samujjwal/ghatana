/**
 * GeneratedCodeQualityGate — Quality gate display (F-Y016 / AI-Y5)
 *
 * Shows compile / lint / test check status for a generated-code artifact and
 * renders the Accept button as disabled until all three checks pass.
 *
 * Usage:
 * ```tsx
 * <GeneratedCodeQualityGate
 *   artifactId={artifact.id}
 *   onAccept={handleAccept}
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose Quality gate enforcement UI that blocks Accept until all checks pass
 * @doc.layer product
 * @doc.pattern AI Quality Gate
 */

import { Check, CircleAlert, Loader2, RefreshCw } from 'lucide-react';
import React from 'react';

import { cn } from '@/lib/utils';
import { useGeneratedCodeQualityGate } from '@/hooks/useGeneratedCodeQualityGate';
import type { QualityCheckResult, QualityCheckStatus } from '@/hooks/useGeneratedCodeQualityGate';
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';

// ── Props ─────────────────────────────────────────────────────────────────────

export interface GeneratedCodeQualityGateProps {
  artifactId: string;
  /** Called when the user clicks Accept (only possible when all checks pass) */
  onAccept: () => void;
  /** Optional extra class for the container */
  className?: string;
}

// ── Sub-components ────────────────────────────────────────────────────────────

const statusIcon: Record<QualityCheckStatus, React.ReactNode> = {
  PENDING: <Loader2 className="h-3.5 w-3.5 animate-spin text-text-secondary" />,
  RUNNING: <Loader2 className="h-3.5 w-3.5 animate-spin text-primary" />,
  PASSED: <Check className="h-3.5 w-3.5 text-success-color" />,
  FAILED: <CircleAlert className="h-3.5 w-3.5 text-error-color" />,
};

const statusLabel: Record<QualityCheckStatus, string> = {
  PENDING: 'Pending',
  RUNNING: 'Running…',
  PASSED: 'Passed',
  FAILED: 'Failed',
};

const statusRowClass: Record<QualityCheckStatus, string> = {
  PENDING: 'text-text-secondary',
  RUNNING: 'text-primary',
  PASSED: 'text-success-color',
  FAILED: 'text-error-color',
};

interface CheckRowProps {
  label: string;
  result: QualityCheckResult;
  testId: string;
}

function CheckRow({ label, result, testId }: CheckRowProps) {
  return (
    <div
      className={cn('flex flex-col gap-0.5', statusRowClass[result.status])}
      data-testid={testId}
    >
      <div className="flex items-center gap-2">
        {statusIcon[result.status]}
        <span className="text-xs font-medium">{label}</span>
        <span className="ml-auto text-xs">{statusLabel[result.status]}</span>
      </div>
      {result.status === 'FAILED' && result.detail && (
        <p className="ml-5 font-mono text-xs text-error-color opacity-90">{result.detail}</p>
      )}
      {result.status === 'FAILED' && result.remediationSuggestion && (
        <p className="ml-5 text-xs italic text-text-secondary">
          Suggestion: {result.remediationSuggestion}
        </p>
      )}
    </div>
  );
}

// ── Component ─────────────────────────────────────────────────────────────────

export function GeneratedCodeQualityGate({
  artifactId,
  onAccept,
  className,
}: GeneratedCodeQualityGateProps) {
  const { t } = useTranslation('common');
  const { canAccept, isLoading, isError, quality, refetch } =
    useGeneratedCodeQualityGate({ artifactId });

  return (
    <div
      className={cn('rounded-lg border border-divider bg-bg-paper p-4', className)}
      data-testid="quality-gate"
    >
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-text-primary">Quality gate</h3>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => refetch()}
          className="rounded p-1 text-text-secondary hover:bg-grey-100"
          aria-label={t('ai.quality.refreshChecks')}
          data-testid="btn-refresh-quality"
        >
          <RefreshCw className="h-3.5 w-3.5" />
        </Button>
      </div>

      {isLoading && (
        <div
          className="flex items-center gap-2 py-4 text-text-secondary"
          data-testid="quality-loading"
        >
          <Loader2 className="h-4 w-4 animate-spin" />
          <span className="text-sm">Running quality checks…</span>
        </div>
      )}

      {isError && (
        <p className="text-sm text-error-color" role="alert" data-testid="quality-error">
          Failed to load quality results. Retry above.
        </p>
      )}

      {!isLoading && !isError && quality && (
        <div className="mb-4 space-y-2">
          <CheckRow label="Compile" result={quality.compile} testId="check-compile" />
          <CheckRow label="Lint" result={quality.lint} testId="check-lint" />
          <CheckRow label="Tests" result={quality.test} testId="check-test" />
        </div>
      )}

      <Button
        type="button"
        onClick={onAccept}
        disabled={!canAccept}
        fullWidth
        className={cn(
          'w-full rounded-md px-4 py-2 text-sm font-medium transition-colors',
          canAccept
            ? 'bg-primary text-white hover:bg-primary/90'
            : 'cursor-not-allowed bg-grey-200 text-text-disabled'
        )}
        data-testid="btn-accept"
        aria-disabled={!canAccept}
        title={
          canAccept
            ? 'Accept generated code'
            : 'All quality checks must pass before accepting'
        }
      >
        {canAccept ? 'Accept' : 'Accept (blocked — fix checks first)'}
      </Button>
    </div>
  );
}
