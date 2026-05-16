import { useState, type ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import type { ProductUnitIntentApplicationResult } from '@ghatana/kernel-product-contracts';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { dependencyGraphEvidence, generatedChangeSetSummary, productShapeEvidence, yappcProductUnitIntentCandidate } from './yappcWorkflowData';

export default function BlueprintsPage(): ReactElement {
  const t = useStudioTranslation();
  const lifecycleData = useStudioLifecycleData();
  const requestedLifecycle = yappcProductUnitIntentCandidate.requestedLifecycle;
  const provenance = yappcProductUnitIntentCandidate.provenance;
  const [handoffResult, setHandoffResult] = useState<ProductUnitIntentApplicationResult | null>(null);
  const [handoffError, setHandoffError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function previewIntent(): Promise<void> {
    if (lifecycleData.previewProductUnitIntent === undefined) {
      setHandoffError(t('studio.route.blueprints.handoffUnavailable'));
      return;
    }
    setHandoffError(null);
    setIsSubmitting(true);
    try {
      const result = await lifecycleData.previewProductUnitIntent(yappcProductUnitIntentCandidate, {
        providerMode: lifecycleData.selectedProviderMode,
      });
      setHandoffResult(result);
    } catch (error: unknown) {
      setHandoffError(error instanceof Error ? error.message : t('studio.route.blueprints.handoffFailed'));
    } finally {
      setIsSubmitting(false);
    }
  }

  async function applyIntent(): Promise<void> {
    if (lifecycleData.applyProductUnitIntent === undefined) {
      setHandoffError(t('studio.route.blueprints.handoffUnavailable'));
      return;
    }
    setHandoffError(null);
    setIsSubmitting(true);
    try {
      const result = await lifecycleData.applyProductUnitIntent(yappcProductUnitIntentCandidate, {
        providerMode: lifecycleData.selectedProviderMode,
      });
      setHandoffResult(result);
    } catch (error: unknown) {
      setHandoffError(error instanceof Error ? error.message : t('studio.route.blueprints.handoffFailed'));
    } finally {
      setIsSubmitting(false);
    }
  }

  const getConfidenceTone = (confidence: number): 'success' | 'warning' | 'danger' => {
    if (confidence >= 0.9) return 'success';
    if (confidence >= 0.7) return 'warning';
    return 'danger';
  };

  const getReadinessTone = (readiness: string): 'success' | 'warning' | 'danger' | 'neutral' => {
    if (readiness === 'ready' || readiness === 'complete') return 'success';
    if (readiness === 'partial' || readiness === 'in-progress') return 'warning';
    if (readiness === 'missing' || readiness === 'blocked') return 'danger';
    return 'neutral';
  };

  return (
    <section className="space-y-6" aria-labelledby="blueprints-title">
      <div className="space-y-2">
        <h2 id="blueprints-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.blueprints.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">{t('studio.route.blueprints.description')}</p>
      </div>

      {/* ProductUnitIntent Readiness */}
      <article className="studio-card space-y-3" aria-labelledby="intent-readiness-title">
        <div className="flex items-center justify-between gap-3">
          <h3 id="intent-readiness-title" className="text-base font-semibold text-gray-950">
            {t('studio.route.blueprints.intentReadinessTitle')}
          </h3>
          <Badge tone={getReadinessTone('candidate')} variant="soft" className="text-xs">
            {yappcProductUnitIntentCandidate.intentType}
          </Badge>
        </div>
        <div className="grid gap-3 text-sm">
          <div className="flex justify-between">
            <span className="text-gray-600">{t('studio.route.blueprints.intentIdLabel')}:</span>
            <span className="font-mono text-gray-900">{yappcProductUnitIntentCandidate.intentId}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">{t('studio.route.blueprints.productUnitLabel')}:</span>
            <span className="font-medium text-gray-900">{yappcProductUnitIntentCandidate.productUnit.name}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">{t('studio.route.blueprints.lifecycleProfileLabel')}:</span>
            <span className="font-medium text-gray-900">{requestedLifecycle?.profile ?? 'not set'}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">{t('studio.route.blueprints.enableExecutionLabel')}:</span>
            <Badge tone={requestedLifecycle?.enableExecution === true ? 'success' : 'neutral'} variant="soft" className="text-xs">
              {requestedLifecycle?.enableExecution === true ? 'Enabled' : 'Disabled'}
            </Badge>
          </div>
        </div>
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.targetProvidersTitle')}</h4>
          <div className="space-y-1 text-xs">
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.blueprints.registryProviderLabel')}:</span>
              <span className="font-mono text-gray-900">{yappcProductUnitIntentCandidate.target.registryProvider}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.blueprints.sourceProviderLabel')}:</span>
              <span className="font-mono text-gray-900">{yappcProductUnitIntentCandidate.target.sourceProvider}</span>
            </div>
          </div>
        </div>
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.provenanceTitle')}</h4>
          <ul className="space-y-1 text-xs text-gray-600">
            <li className="font-mono">{provenance?.sourceSystem ?? 'unknown'}</li>
            {(provenance?.evidenceRefs ?? []).map((ref) => (
              <li key={ref} className="font-mono">{ref}</li>
            ))}
          </ul>
        </div>
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.kernelHandoffStatusLabel')}</h4>
          <Badge tone="warning" variant="soft" className="text-xs">
            {t('studio.route.blueprints.handoffStatusPending')}
          </Badge>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="rounded bg-blue-600 px-3 py-2 text-xs font-medium text-white disabled:opacity-60"
            onClick={() => {
              void previewIntent();
            }}
            disabled={isSubmitting}
          >
            {t('studio.route.blueprints.exportPreview')}
          </button>
          <button
            type="button"
            className="rounded border border-blue-700 px-3 py-2 text-xs font-medium text-blue-700 disabled:opacity-60"
            onClick={() => {
              void applyIntent();
            }}
            disabled={isSubmitting}
          >
            {t('studio.route.blueprints.exportApply')}
          </button>
        </div>
        {handoffError !== null ? (
          <p className="text-xs text-red-700">{handoffError}</p>
        ) : null}
        {handoffResult !== null ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-2 text-xs text-gray-700">
            <p>{t('studio.route.blueprints.handoffResultLabel')} {handoffResult.status}</p>
            {handoffResult.blockedReasons.length > 0 ? (
              <p>{t('studio.route.blueprints.blockedReasonsLabel')} {handoffResult.blockedReasons.join(', ')}</p>
            ) : null}
          </div>
        ) : null}
      </article>

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Product Shape Evidence */}
        <article className="studio-card space-y-3" aria-labelledby="product-shape-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="product-shape-title" className="text-base font-semibold text-gray-950">
              {t('studio.route.blueprints.productShapeTitle')}
            </h3>
            <Badge tone={getConfidenceTone(productShapeEvidence.confidence)} variant="soft" className="text-xs">
              {Math.round(productShapeEvidence.confidence * 100)}% confidence
            </Badge>
          </div>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.blueprints.shapeKindLabel')}:</span>
              <span className="font-medium text-gray-900">{productShapeEvidence.shapeKind}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.blueprints.lifecycleReadinessLabel')}:</span>
              <Badge tone={getReadinessTone(productShapeEvidence.lifecycleReadiness)} variant="soft" className="text-xs">
                {productShapeEvidence.lifecycleReadiness}
              </Badge>
            </div>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.detectedSurfacesTitle')}</h4>
            <div className="flex flex-wrap gap-1">
              {productShapeEvidence.detectedSurfaces.map((surface) => (
                <span key={surface} className="rounded-md bg-gray-100 px-2 py-0.5 text-xs text-gray-900">
                  {surface}
                </span>
              ))}
            </div>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.requiredAdaptersTitle')}</h4>
            <ul className="space-y-1 text-xs text-gray-600">
              {productShapeEvidence.requiredAdapters.map((adapter) => (
                <li key={adapter} className="font-mono">{adapter}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.missingEvidenceTitle')}</h4>
            <ul className="space-y-1 text-xs text-gray-600">
              {productShapeEvidence.missingEvidenceRefs.map((ref) => (
                <li key={ref} className="font-mono">{ref}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.evidenceRefTitle')}</h4>
            <code className="block rounded-md bg-gray-100 p-2 text-xs text-gray-900">
              {productShapeEvidence.evidenceId}
            </code>
          </div>
        </article>

        {/* Dependency Graph Evidence */}
        <article className="studio-card space-y-3" aria-labelledby="dependencies-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="dependencies-title" className="text-base font-semibold text-gray-950">
              {t('studio.route.blueprints.dependenciesTitle')}
            </h3>
            <Badge tone={getConfidenceTone(dependencyGraphEvidence.confidence)} variant="soft" className="text-xs">
              {Math.round(dependencyGraphEvidence.confidence * 100)}% confidence
            </Badge>
          </div>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.blueprints.totalDependenciesLabel')}:</span>
              <span className="font-medium text-gray-900">{dependencyGraphEvidence.dependencyCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.blueprints.internalDependenciesLabel')}:</span>
              <span className="font-medium text-gray-900">{dependencyGraphEvidence.internalDependencyCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.blueprints.externalDependenciesLabel')}:</span>
              <span className="font-medium text-gray-900">{dependencyGraphEvidence.externalDependencyCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.blueprints.cyclesLabel')}:</span>
              <Badge tone={dependencyGraphEvidence.cycleCount > 0 ? 'danger' : 'success'} variant="soft" className="text-xs">
                {dependencyGraphEvidence.cycleCount}
              </Badge>
            </div>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.evidenceRefTitle')}</h4>
            <code className="block rounded-md bg-gray-100 p-2 text-xs text-gray-900">
              {dependencyGraphEvidence.evidenceId}
            </code>
          </div>
        </article>

        {/* Generated Change Set Summary */}
        <article className="studio-card space-y-3" aria-labelledby="generated-changes-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="generated-changes-title" className="text-base font-semibold text-gray-950">
              {t('studio.route.blueprints.generatedChangesTitle')}
            </h3>
            <Badge tone={getConfidenceTone(generatedChangeSetSummary.confidence)} variant="soft" className="text-xs">
              {Math.round(generatedChangeSetSummary.confidence * 100)}% confidence
            </Badge>
          </div>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">{t('studio.route.blueprints.changeCountLabel')}:</span>
              <span className="font-medium text-gray-900">{generatedChangeSetSummary.changeCount}</span>
            </div>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.changeSetIdLabel')}</h4>
            <code className="block rounded-md bg-gray-100 p-2 text-xs text-gray-900">
              {generatedChangeSetSummary.changeSetId}
            </code>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.affectedArtifactsTitle')}</h4>
            <ul className="space-y-1 text-xs text-gray-600">
              {generatedChangeSetSummary.affectedArtifactRefs.map((ref) => (
                <li key={ref} className="font-mono">{ref}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.validationEvidenceTitle')}</h4>
            <ul className="space-y-1 text-xs text-gray-600">
              {generatedChangeSetSummary.validationEvidenceRefs.map((ref) => (
                <li key={ref} className="font-mono">{ref}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.blueprints.evidenceRefTitle')}</h4>
            <code className="block rounded-md bg-gray-100 p-2 text-xs text-gray-900">
              {generatedChangeSetSummary.evidenceId}
            </code>
          </div>
        </article>
      </div>
    </section>
  );
}
