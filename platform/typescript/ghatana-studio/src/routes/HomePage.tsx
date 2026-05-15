import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

interface JourneyStep {
  readonly title: string;
  readonly owner: string;
  readonly description: string;
}

const JOURNEY_STEPS: readonly JourneyStep[] = [
  {
    title: 'Ideate',
    owner: 'YAPPC',
    description: 'Capture ideas, turn them into blueprints, and keep product intent visible.',
  },
  {
    title: 'Develop',
    owner: 'Kernel',
    description: 'Plan lifecycle work, execute gates, and collect artifacts through contracts.',
  },
  {
    title: 'Operate',
    owner: 'Data Cloud',
    description: 'Surface runtime truth, provider health, evidence, and lifecycle memory.',
  },
] as const;

export default function HomePage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();

  return (
    <section className="space-y-6" aria-labelledby="studio-home-title">
      <div className="space-y-2">
        <Badge tone="primary" variant="soft">{t('studio.route.home.badge')}</Badge>
        <h2 id="studio-home-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.home.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.home.description')}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {JOURNEY_STEPS.map((step: JourneyStep) => (
          <article key={step.title} className="studio-card space-y-3">
            <div className="flex items-center justify-between gap-3">
              <h3 className="text-base font-semibold text-gray-950">{step.title}</h3>
              <Badge tone="neutral" variant="outline">{step.owner}</Badge>
            </div>
            <p className="text-sm leading-6 text-gray-600">{step.description}</p>
          </article>
        ))}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <article className="studio-card space-y-3">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-semibold text-gray-950">
              {t('studio.route.home.pilotTitle')}
            </h3>
            <Badge tone="success" variant="soft">Digital Marketing</Badge>
          </div>
          <p className="text-sm leading-6 text-gray-600">
            {t('studio.route.home.pilotDescription')}
          </p>
        </article>

        <article className="studio-card space-y-3">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-semibold text-gray-950">
              {t('studio.route.home.providerModeTitle')}
            </h3>
            <Badge tone="warning" variant="soft">Bootstrap</Badge>
          </div>
          <p className="text-sm leading-6 text-gray-600">
            {t('studio.route.home.providerModeDescription')}
          </p>
        </article>
      </div>

      <article className="studio-card space-y-3" aria-labelledby="studio-health-summary-title">
        <div className="flex items-center justify-between gap-3">
            <h3 id="studio-health-summary-title" className="text-base font-semibold text-gray-950">
            {t('studio.route.home.healthSummaryTitle')}
            </h3>
          <Badge tone={lifecycleDataBadgeTone(lifecycleData.status)} variant="soft">
            {describeLifecycleDataStatus(lifecycleData.status)}
          </Badge>
        </div>
        <p className="text-sm leading-6 text-gray-600">
          {lifecycleData.selectedRun === undefined
            ? t('studio.route.home.healthSummaryEmpty')
            : `Latest run ${lifecycleData.selectedRun.runId} is ${lifecycleData.selectedRun.status}.`}
        </p>
      </article>
    </section>
  );
}
