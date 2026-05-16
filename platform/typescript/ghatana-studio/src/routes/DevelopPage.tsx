import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

const SAFE_ACTIONS = ['create plan', 'validate', 'test', 'build', 'package'] as const;

export default function DevelopPage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const snapshot = lifecycleData.snapshot;
  const productUnit = snapshot.productUnit;
  const productUnitId = productUnit?.id ?? 'digital-marketing';
  const surfaces = productUnit?.surfaces ?? [];

  return (
    <section className="space-y-6" aria-labelledby="develop-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(snapshot.status)} variant="soft">
          {describeLifecycleDataStatus(snapshot.status)}
        </Badge>
        <h2 id="develop-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.develop.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.develop.description')}
        </p>
      </div>

      <div className="studio-card space-y-4">
        <label htmlFor="product-unit-selector" className="text-sm font-medium text-gray-900">
          {t('studio.route.develop.productUnitLabel')}
        </label>
        <select
          id="product-unit-selector"
          className="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm"
          value={productUnitId}
          onChange={(event) => {
            void event;
          }}
          disabled
        >
          <option value={productUnitId}>{productUnit?.name ?? 'Digital Marketing'}</option>
        </select>
        {snapshot.errorMessage !== undefined ? (
          <p className="text-sm text-amber-700">{snapshot.errorMessage}</p>
        ) : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <article className="studio-card space-y-3" aria-labelledby="product-shape-title">
          <h3 id="product-shape-title" className="text-base font-semibold text-gray-950">
              {t('studio.route.develop.productShapeTitle')}
          </h3>
          <dl className="grid gap-3 text-sm text-gray-600 sm:grid-cols-2">
            <div>
              <dt className="font-medium text-gray-900">Kind</dt>
              <dd>{productUnit?.kind ?? 'awaiting ProductUnit contract'}</dd>
            </div>
            <div>
              <dt className="font-medium text-gray-900">Lifecycle</dt>
              <dd>{productUnit?.lifecycleStatus ?? 'unavailable'}</dd>
            </div>
            <div>
              <dt className="font-medium text-gray-900">Owner</dt>
              <dd>{productUnit?.owner ?? 'unassigned'}</dd>
            </div>
            <div>
              <dt className="font-medium text-gray-900">Conformance</dt>
              <dd>{productUnit?.conformance?.level ?? 'not reported'}</dd>
            </div>
          </dl>
        </article>

        <article className="studio-card space-y-3" aria-labelledby="safe-actions-title">
          <h3 id="safe-actions-title" className="text-base font-semibold text-gray-950">
              {t('studio.route.develop.safeActionsTitle')}
          </h3>
          <div className="flex flex-wrap gap-2">
            {SAFE_ACTIONS.map((action: string) => (
              <button
                key={action}
                type="button"
                className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-800"
              >
                {action}
              </button>
            ))}
          </div>
          <p className="text-sm leading-6 text-gray-600">
            {t('studio.route.develop.safeActionNote')}
          </p>
        </article>
      </div>

      <article className="studio-card space-y-3" aria-labelledby="surface-list-title">
        <h3 id="surface-list-title" className="text-base font-semibold text-gray-950">
          {t('studio.route.develop.surfacesTitle')}
        </h3>
        <div className="grid gap-3 lg:grid-cols-2">
          {surfaces.length === 0 ? (
            <p className="text-sm text-gray-600">
              {t('studio.route.develop.surfacesEmpty')}
            </p>
          ) : null}
          {surfaces.map((surface: {
            id: string;
            type: string;
            implementationStatus: string;
            packagePath?: string;
            gradleModule?: string;
            sourceRef?: string;
          }) => (
            <div key={surface.id} className="rounded-md border border-gray-200 p-4">
              <div className="flex items-center justify-between gap-3">
                <h4 className="text-sm font-semibold text-gray-950">{surface.type}</h4>
                <Badge tone="success" variant="soft">{surface.implementationStatus}</Badge>
              </div>
              <p className="mt-2 text-sm text-gray-600">
                {surface.packagePath ?? surface.gradleModule ?? surface.sourceRef ?? 'source not reported'}
              </p>
            </div>
          ))}
        </div>
      </article>
    </section>
  );
}
