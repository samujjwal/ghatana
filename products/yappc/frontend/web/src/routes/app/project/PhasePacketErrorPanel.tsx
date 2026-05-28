import React from 'react';

import { useTranslation } from '@ghatana/i18n';

import { ErrorState, errorCorrelationId } from '../../../components/common/ErrorState';

interface PhasePacketErrorPanelProps {
  readonly error: Error | null;
  readonly onRetry: () => void;
}

export function PhasePacketErrorPanel({ error, onRetry }: PhasePacketErrorPanelProps): React.ReactNode {
  const { t } = useTranslation('common');
  if (!error) {
    return null;
  }

  return (
    <section data-testid="phase-packet-error" aria-label={t('phaseCockpit.error.aria')}>
      <ErrorState
        title={t('phaseCockpit.error.title')}
        message={error.message}
        correlationId={errorCorrelationId(error)}
        onRetry={onRetry}
        retryLabel={t('phaseCockpit.error.retry')}
        variant="banner"
        type="warning"
        size="sm"
      />
    </section>
  );
}
