import type { ReactElement } from 'react';
import { isProductUnitIntent } from '@ghatana/kernel-product-contracts';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { yappcProductUnitIntentCandidate } from './yappcWorkflowData';

export default function IdeasPage(): ReactElement {
  const candidateIsValid = isProductUnitIntent(yappcProductUnitIntentCandidate);
  const t = useStudioTranslation();

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

        <button type="button" className="mt-4 rounded bg-blue-600 px-3 py-2 text-sm font-medium text-white">
          {t('studio.route.ideas.sendToKernel')}
        </button>
      </div>
    </section>
  );
}
