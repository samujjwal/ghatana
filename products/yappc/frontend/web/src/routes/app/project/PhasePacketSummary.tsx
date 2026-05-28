import React from 'react';

import { useTranslation } from '@ghatana/i18n';

import type { PhaseCockpitPacket } from '../../../types/phasePacket';

interface PhasePacketSummaryProps {
  readonly packet: PhaseCockpitPacket | null;
}

export function PhasePacketSummary({ packet }: PhasePacketSummaryProps): React.ReactNode {
  const { t } = useTranslation('common');
  if (!packet) {
    return null;
  }

  const activityCount = packet.activityFeed.length;
  const blockerCount = packet.blockers.length;
  const evidenceCount = packet.evidence.length;
  const governanceCount = packet.governance.length;
  const actionCount = packet.availableActions.length;

  return (
    <section
      className="grid gap-3 rounded-2xl border border-border bg-surface-raised p-4 text-sm shadow-sm md:grid-cols-4"
      data-testid="phase-packet-summary"
      aria-label={t('phaseCockpit.summary.aria')}
    >
      <div data-testid="phase-packet-context">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">{t('phaseCockpit.summary.context')}</p>
        <p className="mt-2 font-medium text-fg">{packet.projectName ?? t('phaseCockpit.fallback.project')}</p>
        <p className="mt-1 text-xs text-fg-muted">{t('phaseCockpit.summary.activityCount', { count: activityCount })}</p>
      </div>
      <div data-testid="phase-packet-state">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">{t('phaseCockpit.summary.state')}</p>
        <p className="mt-2 font-medium text-fg">
          {t('phaseCockpit.summary.stateCounts', { blockers: blockerCount, evidence: evidenceCount })}
        </p>
        <p className="mt-1 text-xs text-fg-muted">{t('phaseCockpit.summary.governanceCount', { count: governanceCount })}</p>
      </div>
      <div data-testid="phase-packet-actions">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">{t('phaseCockpit.summary.actions')}</p>
        <p className="mt-2 font-medium text-fg">{t('phaseCockpit.summary.actionCount', { count: actionCount })}</p>
        <p className="mt-1 text-xs text-fg-muted">
          {packet.dashboardActions.primaryAction ?? t('phaseCockpit.summary.noPrimaryAction')}
        </p>
      </div>
      <div data-testid="phase-packet-readiness">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">{t('phaseCockpit.summary.readiness')}</p>
        <p className="mt-2 font-medium text-fg">
          {packet.readiness.canAdvance ? t('phaseCockpit.summary.canAdvance') : t('phaseCockpit.summary.blocked')}
        </p>
        <p className="mt-1 text-xs text-fg-muted">
          {t('phaseCockpit.summary.score', { score: Math.round(packet.readiness.completenessScore * 100) })}
        </p>
        {packet.readiness.estimatedReadyIn ? (
          <p className="mt-1 text-xs text-fg-muted" data-testid="phase-packet-estimate">
            {t('phaseCockpit.summary.estimate', { estimate: packet.readiness.estimatedReadyIn })}
          </p>
        ) : null}
        {typeof packet.readiness.predictionConfidence === 'number' ? (
          <p className="mt-1 text-xs text-fg-muted" data-testid="phase-packet-confidence">
            {t('phaseCockpit.summary.confidence', {
              confidence: Math.round(packet.readiness.predictionConfidence * 100),
            })}
          </p>
        ) : null}
      </div>
    </section>
  );
}
