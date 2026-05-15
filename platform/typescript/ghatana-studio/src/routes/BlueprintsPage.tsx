import type { ReactElement } from 'react';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { dependencyGraphEvidence, generatedChangeSetSummary, productShapeEvidence } from './yappcWorkflowData';

export default function BlueprintsPage(): ReactElement {
  const t = useStudioTranslation();

  return (
    <section className="studio-section" aria-labelledby="blueprints-title">
      <div className="studio-card">
        <h2 id="blueprints-title" className="text-xl font-semibold text-gray-950">
          {t('studio.route.blueprints.title')}
        </h2>
        <p className="mt-1 text-sm text-gray-600">
          {t('studio.route.blueprints.description')}
        </p>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <div className="rounded border border-gray-200 bg-white p-3">
            <h3 className="text-sm font-semibold text-gray-900">
              {t('studio.route.blueprints.productShapeTitle')}
            </h3>
            <p className="mt-2 text-sm text-gray-600">{productShapeEvidence.shapeKind}</p>
            <p className="mt-1 text-xs text-gray-500">
              {t('studio.route.blueprints.readinessPrefix')} {productShapeEvidence.lifecycleReadiness}
            </p>
          </div>
          <div className="rounded border border-gray-200 bg-white p-3">
            <h3 className="text-sm font-semibold text-gray-900">
              {t('studio.route.blueprints.dependenciesTitle')}
            </h3>
            <p className="mt-2 text-sm text-gray-600">
              {dependencyGraphEvidence.dependencyCount} {t('studio.route.blueprints.dependenciesSuffix')}
            </p>
            <p className="mt-1 text-xs text-gray-500">
              {dependencyGraphEvidence.cycleCount} {t('studio.route.blueprints.cyclesSuffix')}
            </p>
          </div>
          <div className="rounded border border-gray-200 bg-white p-3">
            <h3 className="text-sm font-semibold text-gray-900">
              {t('studio.route.blueprints.generatedChangesTitle')}
            </h3>
            <p className="mt-2 text-sm text-gray-600">
              {generatedChangeSetSummary.changeCount} {t('studio.route.blueprints.changesSuffix')}
            </p>
            <p className="mt-1 text-xs text-gray-500">{generatedChangeSetSummary.changeSetId}</p>
          </div>
        </div>
      </div>
    </section>
  );
}
