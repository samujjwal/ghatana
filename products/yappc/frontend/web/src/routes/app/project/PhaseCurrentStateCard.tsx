import React from 'react';

import { useTranslation } from '@ghatana/i18n';

import type { PhaseCockpitPacket } from '../../../types/phasePacket';

interface PhaseCurrentStateCardProps {
  readonly packet: PhaseCockpitPacket;
  readonly primaryNextActionLabel: string;
  readonly governanceOutcome: string;
  readonly isDependencyDegraded: boolean;
}

export function PhaseCurrentStateCard({
  packet,
  primaryNextActionLabel,
  governanceOutcome,
  isDependencyDegraded,
}: PhaseCurrentStateCardProps): React.ReactNode {
  const { t } = useTranslation('common');

  const readinessLabel = packet.readiness.canAdvance
    ? t('phaseCockpit.summary.canAdvance')
    : t('phaseCockpit.summary.blocked');

  const estimateLabel = packet.readiness.estimatedReadyIn
    ? t('phaseCockpit.summary.estimate', { estimate: packet.readiness.estimatedReadyIn })
    : null;

  const confidenceLabel = typeof packet.readiness.predictionConfidence === 'number'
    ? t('phaseCockpit.summary.confidence', {
      confidence: Math.round(packet.readiness.predictionConfidence * 100),
    })
    : null;

  return (
    <section
      className="grid gap-3 rounded-2xl border border-border bg-surface-raised p-4 text-sm shadow-sm md:grid-cols-5"
      data-testid="phase-contract-summary"
      aria-label={t('phaseCockpit.summary.aria')}
    >
      <div data-testid="phase-current-state-card" className="contents" />
      <div data-testid="phase-current-project">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
          {t('phaseCockpit.summary.context')}
        </p>
        <p className="mt-2 font-medium text-fg" data-testid="phase-contract-persisted">
          {packet.projectName ?? t('phaseCockpit.fallback.project')}
        </p>
      </div>
      <div data-testid="phase-current-readiness">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
          {t('phaseCockpit.summary.readiness')}
        </p>
        <p className="mt-2 font-medium text-fg">{readinessLabel}</p>
        <p className="mt-1 text-xs text-fg-muted">
          {t('phaseCockpit.summary.score', { score: Math.round(packet.readiness.completenessScore * 100) })}
        </p>
      </div>
      <div data-testid="phase-current-evidence">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
          {t('phaseCockpit.summary.state')}
        </p>
        <p className="mt-2 font-medium text-fg" data-testid="phase-contract-derived">
          {t('phaseCockpit.contract.evidenceCount', { count: packet.evidence.length })}
        </p>
      </div>
      <div data-testid="phase-current-next-action">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
          {t('phaseCockpit.summary.actions')}
        </p>
        <p className="mt-2 font-medium text-fg" data-testid="phase-contract-suggested">{primaryNextActionLabel}</p>
      </div>
      <div data-testid="phase-current-governance">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
          {t('phaseCockpit.summary.governanceCount', { count: packet.governance.length })}
        </p>
        <p className="mt-2 font-medium text-fg" data-testid="phase-contract-review">{governanceOutcome}</p>
        {estimateLabel ? (
          <p className="mt-1 text-xs text-fg-muted" data-testid="phase-packet-estimate">
            {estimateLabel}
          </p>
        ) : null}
        {confidenceLabel ? (
          <p className="mt-1 text-xs text-fg-muted" data-testid="phase-packet-confidence">
            {confidenceLabel}
          </p>
        ) : null}
        {isDependencyDegraded ? (
          <p className="mt-1 text-xs font-medium text-warning-color" data-testid="phase-current-degraded">
            {t('phaseCockpit.disabled.degradedDependency')}
          </p>
        ) : null}
      </div>
    </section>
  );
}
