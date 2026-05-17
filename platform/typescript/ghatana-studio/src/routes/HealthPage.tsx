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

function healthSignalStatusLabel(status: string, t: TranslateFn): string {
  if (status === 'healthy') {
    return t('studio.route.health.signalStatus.healthy');
  }
  if (status === 'degraded') {
    return t('studio.route.health.signalStatus.degraded');
  }
  if (status === 'unhealthy') {
    return t('studio.route.health.signalStatus.unhealthy');
  }
  if (status === 'running') {
    return t('studio.route.health.signalStatus.running');
  }
  if (status === 'loading') {
    return t('studio.route.health.signalStatus.loading');
  }
  if (status === 'unavailable') {
    return t('studio.route.health.signalStatus.unavailable');
  }
  if (status === 'bootstrap') {
    return t('studio.route.health.providerMode.bootstrap');
  }
  if (status === 'platform') {
    return t('studio.route.health.providerMode.platform');
  }
  return t('studio.route.health.signalStatus.unknown');
}

export default function HealthPage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const snapshot = lifecycleData.snapshot;
  const pluginHealthRaw = snapshot.selectedRun?.manifestRefs?.pluginHealth;
  const toolchainHealthRaw = snapshot.selectedRun?.manifestRefs?.toolchainHealth;
  const providerHealthRaw =
    pluginHealthRaw === 'unhealthy' || toolchainHealthRaw === 'unhealthy'
      ? 'unhealthy'
      : pluginHealthRaw === 'degraded' || toolchainHealthRaw === 'degraded'
        ? 'degraded'
        : pluginHealthRaw === undefined && toolchainHealthRaw === undefined
          ? 'unknown'
          : 'healthy';
  const composedHealthModel = {
    bootstrapTruth: snapshot.runtimeMode === 'configured' ? 'running' : 'unavailable',
    platformTruth: snapshot.artifactManifest?.providerMode ?? 'unknown',
    providerHealth: providerHealthRaw,
    productHealth: snapshot.verifyHealthReport?.status ?? 'unknown',
  } as const;
  const healthSignals = [
    {
      label: 'Kernel lifecycle',
      status: snapshot.selectedRun?.status === undefined
        ? describeLifecycleDataStatus(snapshot.status)
        : healthSignalStatusLabel(snapshot.selectedRun.status, t),
    },
    {
      label: 'Data Cloud provider mode',
      status: snapshot.artifactManifest?.providerMode === undefined
        ? healthSignalStatusLabel('unavailable', t)
        : healthSignalStatusLabel(snapshot.artifactManifest.providerMode, t),
    },
    {
      label: 'ProductUnit health',
      status: snapshot.verifyHealthReport?.status === undefined
        ? healthSignalStatusLabel('unknown', t)
        : healthSignalStatusLabel(snapshot.verifyHealthReport.status, t),
    },
    {
      label: 'Plugin health',
      status: snapshot.selectedRun?.manifestRefs?.pluginHealth === undefined
        ? healthSignalStatusLabel('unknown', t)
        : healthSignalStatusLabel(snapshot.selectedRun.manifestRefs.pluginHealth, t),
    },
    {
      label: 'Toolchain health',
      status: snapshot.selectedRun?.manifestRefs?.toolchainHealth === undefined
        ? healthSignalStatusLabel('unknown', t)
        : healthSignalStatusLabel(snapshot.selectedRun.manifestRefs.toolchainHealth, t),
    },
  ] as const;

  return (
    <section className="space-y-6" aria-labelledby="health-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(snapshot.status)} variant="soft">
          {describeLifecycleDataStatus(snapshot.status)}
        </Badge>
        <h2 id="health-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.health.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.health.description')}
        </p>
      </div>

      {composedHealthModel.bootstrapTruth === 'unavailable' || composedHealthModel.platformTruth === 'unknown' || composedHealthModel.providerHealth === 'unknown' || composedHealthModel.productHealth === 'unknown' ? (
        <article className="rounded-md border border-red-200 bg-red-50 p-4" aria-label="provider-readiness-blocked">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-semibold text-red-900">{t('studio.route.providerReadiness.blockedTitle')}</h3>
            <Badge tone="danger" variant="soft" className="text-xs">
              Health evidence missing
            </Badge>
          </div>
          <p className="mt-2 text-sm text-red-800">{t('studio.route.providerReadiness.blockedMessage')}</p>
          <div className="mt-3 space-y-1">
            <h4 className="text-xs font-medium text-red-900">{t('studio.route.providerReadiness.missingEvidence')}</h4>
            <ul className="space-y-1 text-xs text-red-700">
              {composedHealthModel.bootstrapTruth === 'unavailable' ? (
                <li className="font-mono">bootstrap-truth: unavailable</li>
              ) : null}
              {composedHealthModel.platformTruth === 'unknown' ? (
                <li className="font-mono">platform-truth: unknown</li>
              ) : null}
              {composedHealthModel.providerHealth === 'unknown' ? (
                <li className="font-mono">provider-health: unknown</li>
              ) : null}
              {composedHealthModel.productHealth === 'unknown' ? (
                <li className="font-mono">product-health: unknown</li>
              ) : null}
            </ul>
          </div>
        </article>
      ) : (
        <article className="studio-card space-y-2" aria-label="composed-health-model">
          <h3 className="text-base font-semibold text-gray-950">Composed health model</h3>
          <dl className="grid gap-2 text-sm text-gray-700 md:grid-cols-2">
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">Bootstrap truth</dt>
              <dd>{healthSignalStatusLabel(composedHealthModel.bootstrapTruth, t)}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">Platform truth</dt>
              <dd>{healthSignalStatusLabel(composedHealthModel.platformTruth, t)}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">Provider health</dt>
              <dd>{healthSignalStatusLabel(composedHealthModel.providerHealth, t)}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="font-medium text-gray-900">Product health</dt>
              <dd>{healthSignalStatusLabel(composedHealthModel.productHealth, t)}</dd>
            </div>
          </dl>
        </article>
      )}

      <div className="grid gap-3">
        {healthSignals.map((signal) => (
          <article key={signal.label} className="studio-card flex items-center justify-between gap-4">
            <div>
              <h3 className="text-base font-semibold text-gray-950">{signal.label}</h3>
              <p className="text-sm text-gray-600">
                {t('studio.route.health.statusTextVisible')}
              </p>
            </div>
            <Badge tone="neutral" variant="outline">{signal.status}</Badge>
          </article>
        ))}
      </div>
    </section>
  );
}
