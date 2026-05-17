import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import type { StudioTranslationKey } from '../i18n/studioTranslations';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

type TranslateFn = (key: StudioTranslationKey) => string;

function deploymentVerifierStatusLabel(valid: boolean | undefined, t: TranslateFn): string {
  if (valid === true) {
    return t('studio.route.deployments.verifierStatus.valid');
  }
  if (valid === false) {
    return t('studio.route.deployments.verifierStatus.requiresVerification');
  }
  return t('studio.route.deployments.verifierStatus.unavailable');
}

export default function DeploymentsPage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const snapshot = lifecycleData.snapshot;
  const deploymentManifest = snapshot.deploymentManifest;
  const deploymentManifestStatus = snapshot.manifestLoadState.deploymentManifest.status;
  const deploymentManifestMessage = snapshot.manifestLoadState.deploymentManifest.message;
  const verifyHealthStatus = snapshot.manifestLoadState.verifyHealthReport.status;
  const deploymentManifestRef = snapshot.selectedRun?.manifestRefs?.['deployment-manifest'];
  const verifyHealthRef = snapshot.selectedRun?.manifestRefs?.['verify-health-report'] ?? snapshot.selectedRun?.healthSnapshotRef;
  const serviceNames = Object.keys(deploymentManifest?.services ?? {});

  return (
    <section className="space-y-6" aria-labelledby="deployments-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(snapshot.status)} variant="soft">
          {describeLifecycleDataStatus(snapshot.status)}
        </Badge>
        <h2 id="deployments-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.deployments.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.deployments.description')}
        </p>
      </div>

      {deploymentManifestStatus === 'missing' || deploymentManifestStatus === 'unavailable' || deploymentManifestStatus === 'corrupt' || deploymentManifestStatus === 'unauthorized' || verifyHealthStatus === 'missing' || verifyHealthStatus === 'unavailable' ? (
        <article className="rounded-md border border-red-200 bg-red-50 p-4" aria-label="provider-readiness-blocked">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-semibold text-red-900">{t('studio.route.providerReadiness.blockedTitle')}</h3>
            <Badge tone="danger" variant="soft" className="text-xs">
              deployment-manifest: {deploymentManifestStatus}
            </Badge>
          </div>
          <p className="mt-2 text-sm text-red-800">{t('studio.route.providerReadiness.blockedMessage')}</p>
          <div className="mt-3 space-y-1">
            <h4 className="text-xs font-medium text-red-900">{t('studio.route.providerReadiness.missingEvidence')}</h4>
            <ul className="space-y-1 text-xs text-red-700">
              {deploymentManifestRef === undefined || deploymentManifestRef === 'not reported' ? (
                <li className="font-mono">deployment-manifest: not reported</li>
              ) : null}
              {verifyHealthRef === undefined || verifyHealthRef === 'not reported' ? (
                <li className="font-mono">verify-health-report: not reported</li>
              ) : null}
            </ul>
          </div>
        </article>
      ) : (
        <article className="studio-card space-y-2" aria-label="deployment-manifest-state">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-semibold text-gray-950">{t('studio.route.deployments.localEnvironment')}</h3>
            <Badge tone={deploymentManifestStatus === 'loaded' ? 'success' : deploymentManifestStatus === 'missing' ? 'warning' : 'danger'} variant="soft" className="text-xs">
              deployment-manifest: {deploymentManifestStatus}
            </Badge>
          </div>
          <p className="text-xs text-gray-600">verify-health-report: {verifyHealthStatus}</p>
          {deploymentManifestStatus !== 'loaded' && (
            <p className="text-sm text-amber-700">
              {t('studio.route.lifecycle.manifest.remediation.missing')}
            </p>
          )}
          {deploymentManifestMessage !== undefined && (
            <p className="text-xs text-gray-600">{deploymentManifestMessage}</p>
          )}
          <ul className="space-y-1 text-xs text-gray-600">
            <li className="font-mono">deployment-manifest: {deploymentManifestRef ?? t('studio.route.lifecycle.notAvailableValue')}</li>
            <li className="font-mono">verify-health-report: {verifyHealthRef ?? t('studio.route.lifecycle.notAvailableValue')}</li>
          </ul>
        </article>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <article className="studio-card space-y-3">
          <h3 className="text-base font-semibold text-gray-950">
            {t('studio.route.deployments.localEnvironment')}
          </h3>
          <dl className="space-y-2 text-sm text-gray-600">
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">{t('studio.route.lifecycle.targetLabel')}</dt>
              <dd>{deploymentManifest?.target ?? deploymentManifest?.environment ?? t('studio.route.lifecycle.notAvailableValue')}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">{t('studio.route.lifecycle.surfacesLabel')}</dt>
              <dd>{serviceNames.length > 0 ? serviceNames.join(', ') : t('studio.deployments.servicesNotReported')}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">{t('studio.route.lifecycle.outcomeLabel')}</dt>
              <dd>{deploymentVerifierStatusLabel(deploymentManifest?.verifierResult?.valid, t)}</dd>
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
