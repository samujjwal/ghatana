import { type ReactElement } from 'react';
import { isProductUnitIntent } from '@ghatana/kernel-product-contracts';
import { getStudioCapabilityState } from '../api/kernelLifecycleClient';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { yappcProductUnitIntentCandidate } from './yappcWorkflowData';
import IdeationRouteStatusPanel from './IdeationRouteStatusPanel';

export default function IdeasPage(): ReactElement {
  const candidateIsValid = isProductUnitIntent(yappcProductUnitIntentCandidate);
  const t = useStudioTranslation();
  const lifecycleData = useStudioLifecycleData();
  const handoffResult = lifecycleData.intentOperation.status === 'success' ? lifecycleData.intentOperation.result : undefined;
  const handoffError = lifecycleData.intentOperation.status === 'error' ? lifecycleData.intentOperation.errorMessage : undefined;
  const isSubmitting = lifecycleData.intentOperation.status === 'loading';
  const capabilityState = getStudioCapabilityState({
    runtimeConfigured: lifecycleData.authenticatedUserId !== undefined,
    lifecycleStatus: lifecycleData.snapshot.status,
    selectedProviderMode: lifecycleData.selectedProviderMode,
    productUnit: lifecycleData.snapshot.productUnit,
    selectedRun: lifecycleData.snapshot.selectedRun,
    manifestLoadState: lifecycleData.snapshot.manifestLoadState,
  });
  const handoffReady = lifecycleData.previewProductUnitIntent !== undefined && lifecycleData.applyProductUnitIntent !== undefined;
  const currentStatusLabel = capabilityState.runtimeConfigured
    ? capabilityState.lifecycleConfigured
      ? capabilityState.dataCloudEvidenceReady
        ? 'ready'
        : 'evidence pending'
      : 'lifecycle not configured'
    : 'runtime not configured';
  const requiredNextActionLabel = handoffReady
    ? capabilityState.dataCloudEvidenceReady
      ? 'Preview or apply ProductUnitIntent and validate Kernel handoff output.'
      : 'Complete lifecycle/runtime evidence prerequisites before promotion.'
    : 'Configure lifecycle client preview/apply handlers for ProductUnitIntent handoff.';

  async function previewIntent(): Promise<void> {
    if (lifecycleData.previewProductUnitIntent === undefined) {
      return;
    }
    try {
      await lifecycleData.previewProductUnitIntent(yappcProductUnitIntentCandidate, {
        providerMode: lifecycleData.selectedProviderMode,
      });
    } catch {
      // Error state is captured in the lifecycle context.
    }
  }

  async function applyIntent(): Promise<void> {
    if (lifecycleData.applyProductUnitIntent === undefined) {
      return;
    }
    try {
      await lifecycleData.applyProductUnitIntent(yappcProductUnitIntentCandidate, {
        providerMode: lifecycleData.selectedProviderMode,
      });
    } catch {
      // Error state is captured in the lifecycle context.
    }
  }

  return (
    <section className="studio-section" aria-labelledby="ideas-title">
      <div className="studio-card">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div>
            <h2 id="ideas-title" className="text-xl font-semibold text-gray-950">
              {t('studio.route.ideas.title')}
            </h2>
            <p className="mt-1 text-sm text-gray-600">
              {t('studio.route.ideas.description')}
            </p>
          </div>
          <span className="rounded bg-amber-50 px-2 py-1 text-xs font-medium text-amber-700">
            Candidate {candidateIsValid ? 'valid' : 'blocked'}
          </span>
        </div>

        <div className="mb-4">
          <IdeationRouteStatusPanel
            ownershipLabel="YAPPC"
            currentStatusLabel={currentStatusLabel}
            requiredNextActionLabel={requiredNextActionLabel}
            handoffReady={handoffReady}
            handoffReadinessLabel={handoffReady ? 'handoff ready' : 'handoff unavailable'}
            evidenceRefs={[
              yappcProductUnitIntentCandidate.intentId,
              ...(yappcProductUnitIntentCandidate.provenance?.evidenceRefs ?? []),
            ]}
          />
        </div>

        <div className="grid gap-3 md:grid-cols-3">
          <div className="rounded border border-gray-200 bg-white p-3">
            <h3 className="text-sm font-semibold text-gray-900">
              {t('studio.route.ideas.intentTitle')}
            </h3>
            <p className="mt-2 text-sm text-gray-600">{yappcProductUnitIntentCandidate.intentId}</p>
            <p className="mt-1 text-xs text-gray-500">
              {t('studio.route.ideas.statusPrefix')} {t('studio.route.ideas.statusValue')}
            </p>
          </div>
          <div className="rounded border border-gray-200 bg-white p-3">
            <h3 className="text-sm font-semibold text-gray-900">
              {t('studio.route.ideas.targetProvidersTitle')}
            </h3>
            <p className="mt-2 text-sm text-gray-600">{yappcProductUnitIntentCandidate.target.registryProvider}</p>
            <p className="mt-1 text-xs text-gray-500">{yappcProductUnitIntentCandidate.target.sourceProvider}</p>
          </div>
          <div className="rounded border border-gray-200 bg-white p-3">
            <h3 className="text-sm font-semibold text-gray-900">
              {t('studio.route.ideas.providerStateTitle')}
            </h3>
            <p className="mt-2 text-sm text-amber-700">
              {t('studio.route.ideas.providerState')}
            </p>
            <p className="mt-1 text-xs text-gray-500">
              {t('studio.route.ideas.bootstrapHandoff')}
            </p>
          </div>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            className="rounded bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-60"
            onClick={() => {
              void previewIntent();
            }}
            disabled={isSubmitting}
          >
            {t('studio.route.ideas.previewIntent')}
          </button>
          <button
            type="button"
            className="rounded border border-blue-700 px-3 py-2 text-sm font-medium text-blue-700 disabled:opacity-60"
            onClick={() => {
              void applyIntent();
            }}
            disabled={isSubmitting}
          >
            {t('studio.route.ideas.applyIntent')}
          </button>
        </div>

        {lifecycleData.previewProductUnitIntent === undefined || lifecycleData.applyProductUnitIntent === undefined ? (
          <p className="mt-3 text-sm text-red-700">{t('studio.route.ideas.handoffUnavailable')}</p>
        ) : null}

        {handoffError !== undefined ? (
          <p className="mt-3 text-sm text-red-700">{handoffError}</p>
        ) : null}

        {handoffResult !== undefined ? (
          <div className="mt-4 rounded border border-gray-200 bg-white p-3">
            <h3 className="text-sm font-semibold text-gray-900">{t('studio.route.ideas.handoffResultTitle')}</h3>
            <p className="mt-1 text-sm text-gray-700">
              {t('studio.route.ideas.handoffStatusLabel')} {handoffResult.status}
            </p>
            <p className="mt-1 text-xs text-gray-600">
              {t('studio.route.ideas.handoffCorrelationIdLabel')} {handoffResult.correlationId}
            </p>
            {handoffResult.blockedReasons.length > 0 ? (
              <p className="mt-1 text-xs text-amber-700">
                {t('studio.route.ideas.blockedReasonsLabel')} {handoffResult.blockedReasons.join(', ')}
              </p>
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  );
}
