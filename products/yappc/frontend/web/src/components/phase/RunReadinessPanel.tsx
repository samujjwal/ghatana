/**
 * Run Readiness Panel
 *
 * Phase-native panel for the Run phase. Shows capability gates, deployment
 * plan, and distinguishes planning/preview/actual deploy deployment modes.
 *
 * @doc.type component
 * @doc.purpose Deployment readiness surface for the Run phase
 * @doc.layer product
 * @doc.pattern Phase Panel
 */

import React, { useState, useCallback } from 'react';
import { Button, Card, CardContent } from '@ghatana/design-system';
import { Select } from '../ui/Select';
import { useTranslation } from '@ghatana/i18n';

export type DeploymentMode = 'planning' | 'preview' | 'production';

export type GateStatus = 'passed' | 'failed' | 'skipped' | 'running' | 'not-started';

export interface CapabilityGate {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly status: GateStatus;
  readonly required: boolean;
  readonly failureReason?: string;
  readonly actionLabel?: string;
  readonly onAction?: () => void;
}

export interface DeploymentStep {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly order: number;
  readonly status: 'pending' | 'in-progress' | 'done' | 'failed' | 'skipped';
  readonly estimatedDurationSeconds?: number;
}

export interface RunReadinessPanelProps {
  /** Current deployment mode */
  readonly mode: DeploymentMode;
  /** Capability gates that must pass before deployment */
  readonly gates: readonly CapabilityGate[];
  /** Ordered deployment plan steps */
  readonly plan: readonly DeploymentStep[];
  /** ISO timestamp of last readiness check */
  readonly checkedAt?: string;
  /** Whether any mode switch is currently loading */
  readonly isModeChanging?: boolean;
  /** Called when deployment mode is changed */
  readonly onChangeMode: (mode: DeploymentMode) => void;
  /** Called to trigger the primary deploy action for the current mode */
  readonly onDeploy: () => void;
  /** Whether deployment can be triggered */
  readonly canDeploy: boolean;
  /** Reason deployment cannot be triggered */
  readonly cannotDeployReason?: string;
  /** Custom className */
  readonly className?: string;
}

const GATE_STATUS_STYLE: Record<GateStatus, { icon: string; className: string }> = {
  passed: { icon: '✓', className: 'text-success-color' },
  failed: { icon: '✗', className: 'text-destructive' },
  skipped: { icon: '—', className: 'text-fg-muted' },
  running: { icon: '⟳', className: 'text-info-color' },
  'not-started': { icon: '○', className: 'text-fg-muted' },
};

const STEP_STATUS_STYLE: Record<DeploymentStep['status'], { label: string; className: string }> = {
  pending: { label: 'Pending', className: 'bg-surface-muted text-fg-muted' },
  'in-progress': { label: 'In progress', className: 'bg-info-bg text-info-color' },
  done: { label: 'Done', className: 'bg-success-bg text-success-color' },
  failed: { label: 'Failed', className: 'bg-destructive-bg text-destructive' },
  skipped: { label: 'Skipped', className: 'bg-surface-muted text-fg-muted' },
};

const MODE_LABELS: Record<DeploymentMode, { label: string; description: string }> = {
  planning: {
    label: 'Planning',
    description: 'Simulate deployment without touching any environment.',
  },
  preview: {
    label: 'Preview',
    description: 'Deploy to staging environment for testing and approval.',
  },
  production: {
    label: 'Production',
    description: 'Deploy to the live environment. Requires all gates to pass.',
  },
};

/**
 * Run Readiness Panel
 *
 * Provides deployment readiness visibility with:
 * - Mode selector (planning / preview / production)
 * - Capability gates with per-gate remediation actions
 * - Ordered deployment plan with step status
 * - Deploy trigger (gated by required gates)
 */
export const RunReadinessPanel: React.FC<RunReadinessPanelProps> = ({
  mode,
  gates,
  plan,
  checkedAt,
  isModeChanging = false,
  onChangeMode,
  onDeploy,
  canDeploy,
  cannotDeployReason,
  className = '',
}) => {
  const { t } = useTranslation('common');
  const [planExpanded, setPlanExpanded] = useState(false);

  const failedRequiredGates = gates.filter((g) => g.required && g.status === 'failed');
  const allRequiredGatesPassed = failedRequiredGates.length === 0;

  const handleModeChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      onChangeMode(e.target.value as DeploymentMode);
    },
    [onChangeMode],
  );

  const togglePlan = useCallback(() => {
    setPlanExpanded((prev) => !prev);
  }, []);

  const modeInfo = MODE_LABELS[mode];

  return (
    <section
      className={`run-readiness-panel space-y-6 ${className}`}
      aria-label={t('phase.run.panel')}
      data-testid="run-readiness-panel"
    >
      {/* Mode Selector */}
      <Card variant="outlined">
        <CardContent className="p-5 space-y-3">
          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div>
              <h3 className="text-base font-semibold text-fg">Deployment mode</h3>
              <p className="text-sm text-fg-muted mt-0.5">{modeInfo.description}</p>
            </div>
            {checkedAt && (
              <p className="text-xs text-fg-muted">
                Checked:{' '}
                <time dateTime={checkedAt}>
                  {new Date(checkedAt).toLocaleString()}
                </time>
              </p>
            )}
          </div>
          <div className="flex items-center gap-3">
            <label htmlFor="deployment-mode-select" className="text-sm font-medium text-fg sr-only">
              Deployment mode
            </label>
            <Select
              id="deployment-mode-select"
              value={mode}
              onChange={handleModeChange}
              disabled={isModeChanging}
              className="rounded-md border border-border bg-surface text-fg text-sm px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-ring"
              aria-label={t('phase.run.selectDeploymentMode')}
            >
              {(Object.keys(MODE_LABELS) as DeploymentMode[]).map((m) => (
                <option key={m} value={m}>
                  {MODE_LABELS[m].label}
                </option>
              ))}
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* Capability Gates */}
      {gates.length > 0 && (
        <section aria-label={t('phase.run.capabilityGates')}>
          <h4 className="text-sm font-medium text-fg mb-3">
            Capability gates ({gates.filter((g) => g.status === 'passed').length}/{gates.length} passed)
          </h4>
          <div className="space-y-2">
            {gates.map((gate) => {
              const style = GATE_STATUS_STYLE[gate.status];
              return (
                <div
                  key={gate.id}
                  className={`flex items-start gap-3 rounded-lg border p-3 ${
                    gate.status === 'failed' && gate.required
                      ? 'bg-destructive-bg border-destructive-border'
                      : gate.status === 'passed'
                      ? 'bg-success-bg border-success-border'
                      : 'bg-surface border-border'
                  }`}
                >
                  <span
                    className={`mt-0.5 text-base font-mono font-bold ${style.className}`}
                    aria-hidden="true"
                  >
                    {style.icon}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-fg">
                      {gate.name}
                      {gate.required && gate.status !== 'passed' && (
                        <span className="ml-2 text-xs font-normal text-fg-muted">(required)</span>
                      )}
                    </p>
                    <p className="text-xs text-fg-muted mt-0.5">
                      {gate.status === 'failed' && gate.failureReason
                        ? gate.failureReason
                        : gate.description}
                    </p>
                  </div>
                  {gate.actionLabel && gate.onAction && gate.status === 'failed' && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={gate.onAction}
                      aria-label={`${gate.actionLabel} for gate ${gate.name}`}
                    >
                      {gate.actionLabel}
                    </Button>
                  )}
                </div>
              );
            })}
          </div>
        </section>
      )}

      {/* Deployment Plan */}
      {plan.length > 0 && (
        <section aria-label={t('phase.run.deploymentPlan')}>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="flex w-full items-center justify-between text-sm font-medium text-fg mb-3 hover:text-fg-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            onClick={togglePlan}
            aria-expanded={planExpanded}
          >
            <span>Deployment plan ({plan.length} steps)</span>
            <span aria-hidden="true">{planExpanded ? '▲' : '▼'}</span>
          </Button>
          {planExpanded && (
            <ol className="space-y-2 list-none" aria-label={t('phase.run.deploymentSteps')}>
              {[...plan].sort((a, b) => a.order - b.order).map((step) => {
                const stepStyle = STEP_STATUS_STYLE[step.status];
                return (
                  <li
                    key={step.id}
                    className="flex items-start gap-3 rounded-lg border border-border bg-surface p-3"
                  >
                    <span className="text-xs text-fg-muted font-mono w-5 text-right flex-shrink-0 mt-0.5">
                      {step.order}.
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-fg">{step.name}</p>
                      <p className="text-xs text-fg-muted mt-0.5">{step.description}</p>
                      {step.estimatedDurationSeconds != null && (
                        <p className="text-xs text-fg-muted mt-0.5">
                          ~{step.estimatedDurationSeconds}s
                        </p>
                      )}
                    </div>
                    <span
                      className={`flex-shrink-0 text-xs rounded px-1.5 py-0.5 ${stepStyle.className}`}
                    >
                      {stepStyle.label}
                    </span>
                  </li>
                );
              })}
            </ol>
          )}
        </section>
      )}

      {/* Deploy Action */}
      <section aria-label={t('phase.run.deploy')}>
        {!allRequiredGatesPassed && (
          <div className="mb-3 rounded-lg bg-destructive-bg border border-destructive-border p-3">
            <p className="text-sm text-destructive">
              {failedRequiredGates.length} required gate{failedRequiredGates.length !== 1 ? 's' : ''}{' '}
              must pass before deploying.
            </p>
          </div>
        )}
        {!canDeploy && cannotDeployReason && (
          <div className="mb-3 rounded-lg bg-warning-bg border border-warning-border p-3">
            <p className="text-sm text-warning-color">{cannotDeployReason}</p>
          </div>
        )}
        <Button
          variant="solid"
          onClick={onDeploy}
          disabled={!canDeploy || !allRequiredGatesPassed}
          aria-label={`Deploy in ${modeInfo.label} mode`}
          className="w-full sm:w-auto"
        >
          {mode === 'planning' ? 'Simulate deployment' : mode === 'preview' ? 'Deploy to preview' : 'Deploy to production'}
        </Button>
      </section>
    </section>
  );
};

export default RunReadinessPanel;
