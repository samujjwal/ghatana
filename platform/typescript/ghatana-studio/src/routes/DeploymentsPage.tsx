import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

export default function DeploymentsPage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const deploymentManifest = lifecycleData.deploymentManifest;
  const serviceNames = Object.keys(deploymentManifest?.services ?? {});

  return (
    <section className="space-y-6" aria-labelledby="deployments-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(lifecycleData.status)} variant="soft">
          {describeLifecycleDataStatus(lifecycleData.status)}
        </Badge>
        <h2 id="deployments-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.deployments.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.deployments.description')}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <article className="studio-card space-y-3">
          <h3 className="text-base font-semibold text-gray-950">
            {t('studio.route.deployments.localEnvironment')}
          </h3>
          <dl className="space-y-2 text-sm text-gray-600">
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">Target</dt>
              <dd>{deploymentManifest?.target ?? deploymentManifest?.environment ?? 'not reported'}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">Services</dt>
              <dd>{serviceNames.length > 0 ? serviceNames.join(', ') : 'not reported'}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">Verifier</dt>
              <dd>{deploymentManifest?.verifierResult?.valid === true ? 'valid' : 'requires verification'}</dd>
            </div>
          </dl>
        </article>

        <article className="studio-card space-y-3">
          <h3 className="text-base font-semibold text-gray-950">
            {t('studio.route.deployments.rollbackPlan')}
          </h3>
          <p className="text-sm leading-6 text-gray-600">
            {deploymentManifest?.rollbackPlan.steps.join(' ') ??
              t('studio.route.deployments.rollbackFallback')}{' '}
            {t('studio.route.deployments.noProductionButton')}
          </p>
        </article>
      </div>
    </section>
  );
}
