import type { ReactElement } from 'react';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { artifactGraphSummary, residualIslandReport, riskHotspotReport, semanticArtifactReferences } from './yappcWorkflowData';

export default function CanvasPage(): ReactElement {
  const t = useStudioTranslation();

  return (
    <section className="studio-section" aria-labelledby="canvas-title">
      <div className="studio-card">
        <h2 id="canvas-title" className="text-xl font-semibold text-gray-950">
          {t('studio.route.canvas.title')}
        </h2>
        <p className="mt-1 text-sm text-gray-600">{t('studio.route.canvas.description')}</p>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <div className="rounded border border-gray-200 bg-white p-3">
            <h3 className="text-sm font-semibold text-gray-900">
              {t('studio.route.canvas.artifactGraphTitle')}
            </h3>
            <p className="mt-2 text-sm text-gray-600">
              {artifactGraphSummary.nodeCount} {t('studio.route.canvas.nodesSuffix')} · {artifactGraphSummary.edgeCount} {t('studio.route.canvas.edgesSuffix')}
            </p>
            <p className="mt-1 text-xs text-gray-500">{semanticArtifactReferences[0]?.displayName}</p>
          </div>
          <div className="rounded border border-amber-200 bg-amber-50 p-3">
            <h3 className="text-sm font-semibold text-amber-900">
              {t('studio.route.canvas.residualIslandsTitle')}
            </h3>
            <p className="mt-2 text-sm text-amber-800">
              {residualIslandReport.islandCount} {t('studio.route.canvas.reviewRequired')}
            </p>
            <p className="mt-1 text-xs text-amber-700">{residualIslandReport.residualArtifactRefs.join(', ')}</p>
          </div>
          <div className="rounded border border-red-200 bg-red-50 p-3 md:col-span-2">
            <h3 className="text-sm font-semibold text-red-900">
              {t('studio.route.canvas.riskHotspotsTitle')}
            </h3>
            <p className="mt-2 text-sm text-red-800">{riskHotspotReport.highestRiskLevel} risk: {riskHotspotReport.hotspots[0]?.reason}</p>
          </div>
        </div>
      </div>
    </section>
  );
}
