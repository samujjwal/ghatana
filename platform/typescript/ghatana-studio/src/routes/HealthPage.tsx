import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

export default function HealthPage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const snapshot = lifecycleData.snapshot;
  const healthSignals = [
    {
      label: 'Kernel lifecycle',
      status: snapshot.selectedRun?.status ?? describeLifecycleDataStatus(snapshot.status),
    },
    {
      label: 'Data Cloud provider mode',
      status: snapshot.artifactManifest?.providerMode ?? 'unavailable',
    },
    {
      label: 'ProductUnit health',
      status: snapshot.verifyHealthReport?.status ?? 'unknown',
    },
    {
      label: 'Plugin health',
      status: snapshot.selectedRun?.manifestRefs?.pluginHealth ?? 'unknown',
    },
    {
      label: 'Toolchain health',
      status: snapshot.selectedRun?.manifestRefs?.toolchainHealth ?? 'unknown',
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
