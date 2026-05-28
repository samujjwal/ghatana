import React from 'react';

import { useTranslation } from '@ghatana/i18n';

import { ErrorState } from '../../../components/common/ErrorState';
import { SectionLoading } from '../../../components/common/LoadingState';
import type { MountedPhase } from '../../../services/phase';

interface PhaseRouteGuardStateProps {
  readonly title: string;
  readonly body: string;
  readonly testId: string;
}

function PhaseRouteGuardState({ title, body, testId }: PhaseRouteGuardStateProps): React.ReactNode {
  return (
    <div className="p-6" data-testid={testId}>
      <ErrorState
        title={title}
        message={body}
        type="error"
        variant="card"
        size="md"
      />
    </div>
  );
}

export function MissingProjectState(): React.ReactNode {
  const { t } = useTranslation('common');
  return (
    <PhaseRouteGuardState
      title={t('phaseCockpit.errors.projectContextTitle')}
      body={t('phaseCockpit.errors.projectContextBody')}
      testId="phase-missing-project"
    />
  );
}

export function MissingWorkspaceState(): React.ReactNode {
  const { t } = useTranslation('common');
  return (
    <PhaseRouteGuardState
      title={t('phaseCockpit.errors.workspaceContextTitle')}
      body={t('phaseCockpit.errors.workspaceContextBody')}
      testId="phase-missing-workspace"
    />
  );
}

interface AccessDeniedStateProps {
  readonly phase: MountedPhase;
}

export function AccessDeniedState({ phase }: AccessDeniedStateProps): React.ReactNode {
  const { t } = useTranslation('common');
  return (
    <PhaseRouteGuardState
      title={t('phaseCockpit.errors.accessDeniedTitle')}
      body={t('phaseCockpit.errors.accessDeniedBody', { phase })}
      testId="phase-access-denied"
    />
  );
}

export function PhaseRouteLoadingState(): React.ReactNode {
  const { t } = useTranslation('common');
  return (
    <div className="p-6" data-testid="phase-route-loading">
      <SectionLoading message={t('phaseCockpit.loading', { defaultValue: 'Loading phase cockpit...' })} />
    </div>
  );
}
