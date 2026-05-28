import React from 'react';

import { useTranslation } from '@ghatana/i18n';

import type { DegradedPacketDetails } from '../../../types/phasePacket';

interface PhaseDegradedPacketPanelProps {
  readonly details: DegradedPacketDetails | undefined;
}

export function PhaseDegradedPacketPanel({ details }: PhaseDegradedPacketPanelProps): React.ReactNode {
  const { t } = useTranslation('common');
  if (!details) {
    return null;
  }

  return (
    <section
      className="rounded-2xl border border-warning-border bg-warning-bg p-4 text-sm text-warning-color"
      data-testid="phase-degraded-details"
      aria-label={t('phaseCockpit.degraded.aria')}
    >
      <p className="font-semibold text-warning-color">{t('phaseCockpit.degraded.title')}</p>
      <dl className="mt-3 grid gap-3 md:grid-cols-2">
        <div>
          <dt className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
            {t('phaseCockpit.degraded.dependency')}
          </dt>
          <dd className="mt-1 text-fg" data-testid="phase-degraded-dependency">{details.dependency}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
            {t('phaseCockpit.degraded.truthSource')}
          </dt>
          <dd className="mt-1 text-fg" data-testid="phase-degraded-truth-source">{details.truthSource}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
            {t('phaseCockpit.degraded.reason')}
          </dt>
          <dd className="mt-1 text-fg" data-testid="phase-degraded-reason">{details.reason}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
            {t('phaseCockpit.degraded.recoveryAction')}
          </dt>
          <dd className="mt-1 text-fg" data-testid="phase-degraded-recovery">{details.recoveryAction}</dd>
        </div>
      </dl>
      {details.impactedFeatures.length > 0 ? (
        <p className="mt-3 text-xs text-fg-muted" data-testid="phase-degraded-impacted-features">
          {t('phaseCockpit.degraded.impactedFeatures', { features: details.impactedFeatures.join(', ') })}
        </p>
      ) : null}
    </section>
  );
}
