import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { artifactGraphSummary, residualIslandReport, riskHotspotReport, semanticArtifactReferences } from './yappcWorkflowData';

function canvasRiskLevelLabel(riskLevel: string, t: (key: string) => string): string {
  return t(`studio.route.canvas.riskLevel.${riskLevel}`);
}

export default function CanvasPage(): ReactElement {
  const t = useStudioTranslation();

  const getConfidenceTone = (confidence: number): 'success' | 'warning' | 'danger' => {
    if (confidence >= 0.9) return 'success';
    if (confidence >= 0.7) return 'warning';
    return 'danger';
  };

  return (
    <section className="space-y-6" aria-labelledby="canvas-title">
      <div className="space-y-2">
        <h2 id="canvas-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.canvas.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">{t('studio.route.canvas.description')}</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Artifact Graph */}
        <article className="studio-card space-y-3" aria-labelledby="artifact-graph-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="artifact-graph-title" className="text-base font-semibold text-gray-950">
              {t('studio.route.canvas.artifactGraphTitle')}
            </h3>
            <Badge tone={getConfidenceTone(artifactGraphSummary.confidence)} variant="soft" className="text-xs">
              {Math.round(artifactGraphSummary.confidence * 100)}% confidence
            </Badge>
          </div>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.canvas.nodesLabel')}:</span>
              <span className="font-medium text-gray-900">{artifactGraphSummary.nodeCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.canvas.edgesLabel')}:</span>
              <span className="font-medium text-gray-900">{artifactGraphSummary.edgeCount}</span>
            </div>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.canvas.provenanceTitle')}</h4>
            <ul className="space-y-1 text-xs text-gray-600">
              {artifactGraphSummary.provenanceRefs.map((ref) => (
                <li key={ref} className="font-mono">{ref}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.canvas.evidenceRefTitle')}</h4>
            <code className="block rounded-md bg-gray-100 p-2 text-xs text-gray-900">
              {artifactGraphSummary.evidenceId}
            </code>
          </div>
        </article>

        {/* Residual Islands */}
        <article className="studio-card space-y-3 border-amber-200 bg-amber-50" aria-labelledby="residual-islands-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="residual-islands-title" className="text-base font-semibold text-amber-900">
              {t('studio.route.canvas.residualIslandsTitle')}
            </h3>
            <Badge tone={getConfidenceTone(residualIslandReport.confidence)} variant="soft" className="text-xs">
              {Math.round(residualIslandReport.confidence * 100)}% confidence
            </Badge>
          </div>
          <p className="text-sm text-amber-800">
            {residualIslandReport.islandCount} {t('studio.route.canvas.reviewRequired')}
          </p>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-amber-900">{t('studio.route.canvas.residualArtifactsTitle')}</h4>
            <ul className="space-y-1 text-xs text-amber-700">
              {residualIslandReport.residualArtifactRefs.map((ref) => (
                <li key={ref} className="font-mono">{ref}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-amber-900">{t('studio.route.canvas.provenanceTitle')}</h4>
            <ul className="space-y-1 text-xs text-amber-700">
              {residualIslandReport.provenanceRefs.map((ref) => (
                <li key={ref} className="font-mono">{ref}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-amber-900">{t('studio.route.canvas.evidenceRefTitle')}</h4>
            <code className="block rounded-md bg-amber-100 p-2 text-xs text-amber-900">
              {residualIslandReport.evidenceId}
            </code>
          </div>
        </article>
      </div>

      {/* Risk Hotspots */}
      <article className="studio-card space-y-3 border-red-200 bg-red-50" aria-labelledby="risk-hotspots-title">
        <div className="flex items-center justify-between gap-3">
          <h3 id="risk-hotspots-title" className="text-base font-semibold text-red-900">
            {t('studio.route.canvas.riskHotspotsTitle')}
          </h3>
          <Badge tone={getConfidenceTone(riskHotspotReport.confidence)} variant="soft" className="text-xs">
            {Math.round(riskHotspotReport.confidence * 100)}% confidence
          </Badge>
        </div>
        <p className="text-sm text-red-800">
          {t('studio.route.canvas.highestRiskPrefix')} {canvasRiskLevelLabel(riskHotspotReport.highestRiskLevel, t)}
        </p>
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-red-900">{t('studio.route.canvas.hotspotsTitle')}</h4>
          {riskHotspotReport.hotspots.map((hotspot) => (
            <div key={hotspot.artifactId} className="rounded-md border border-red-300 bg-red-100 p-3">
              <div className="flex justify-between text-sm">
                <span className="font-medium text-red-900">{hotspot.artifactId}</span>
                <Badge tone="danger" variant="soft" className="text-xs">
                  {canvasRiskLevelLabel(hotspot.riskLevel, t)}
                </Badge>
              </div>
              <p className="mt-1 text-xs text-red-800">{hotspot.reason}</p>
              <div className="mt-2 space-y-1">
                <h5 className="text-xs font-medium text-red-900">{t('studio.route.canvas.evidenceRefsTitle')}</h5>
                {hotspot.evidenceRefs.map((ref) => (
                  <code key={ref} className="block rounded bg-red-200 px-2 py-1 text-xs text-red-900">
                    {ref}
                  </code>
                ))}
              </div>
            </div>
          ))}
        </div>
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-red-900">{t('studio.route.canvas.provenanceTitle')}</h4>
          <ul className="space-y-1 text-xs text-red-700">
            {riskHotspotReport.provenanceRefs.map((ref) => (
              <li key={ref} className="font-mono">{ref}</li>
            ))}
          </ul>
        </div>
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-red-900">{t('studio.route.canvas.evidenceRefTitle')}</h4>
          <code className="block rounded-md bg-red-100 p-2 text-xs text-red-900">
            {riskHotspotReport.evidenceId}
          </code>
        </div>
      </article>

      {/* Semantic Artifact References */}
      <article className="studio-card space-y-3" aria-labelledby="semantic-artifacts-title">
        <h3 id="semantic-artifacts-title" className="text-base font-semibold text-gray-950">
          {t('studio.route.canvas.semanticArtifactsTitle')}
        </h3>
        <div className="grid gap-3 md:grid-cols-2">
          {semanticArtifactReferences.map((artifact) => (
            <div key={artifact.evidenceId} className="rounded-md border border-gray-200 p-3">
              <div className="flex items-center justify-between gap-2">
                <h4 className="text-sm font-medium text-gray-900">{artifact.displayName}</h4>
                <Badge tone={getConfidenceTone(artifact.confidence)} variant="soft" className="text-xs">
                  {Math.round(artifact.confidence * 100)}%
                </Badge>
              </div>
              <div className="mt-2 space-y-1 text-xs">
                <div className="flex justify-between">
                  <span className="text-gray-600">{t('studio.route.canvas.artifactKindLabel')}:</span>
                  <span className="font-medium text-gray-900">{artifact.artifactKind}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">{t('studio.route.canvas.riskLevelLabel')}:</span>
                  <Badge tone={artifact.riskLevel === 'critical' ? 'danger' : artifact.riskLevel === 'high' ? 'warning' : 'success'} variant="soft" className="text-xs">
                    {canvasRiskLevelLabel(artifact.riskLevel, t)}
                  </Badge>
                </div>
              </div>
              <div className="mt-2 space-y-1">
                <h5 className="text-xs font-medium text-gray-900">{t('studio.route.canvas.semanticTagsTitle')}</h5>
                <div className="flex flex-wrap gap-1">
                  {artifact.semanticTags.map((tag) => (
                    <span key={tag} className="rounded-md bg-gray-100 px-2 py-0.5 text-xs text-gray-900">
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
              <div className="mt-2 space-y-1">
                <h5 className="text-xs font-medium text-gray-900">{t('studio.route.canvas.evidenceRefTitle')}</h5>
                <code className="block rounded-md bg-gray-100 p-2 text-xs text-gray-900">
                  {artifact.evidenceId}
                </code>
              </div>
              <div className="mt-2 space-y-1">
                <h5 className="text-xs font-medium text-gray-900">{t('studio.route.canvas.provenanceTitle')}</h5>
                {artifact.provenanceRefs.map((ref) => (
                  <code key={ref} className="block rounded-md bg-gray-100 p-2 text-xs text-gray-900">
                    {ref}
                  </code>
                ))}
              </div>
            </div>
          ))}
        </div>
      </article>
    </section>
  );
}
