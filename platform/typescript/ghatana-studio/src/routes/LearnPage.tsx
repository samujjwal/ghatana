import type { ReactElement } from 'react';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { generatedChangeSetSummary, residualIslandReport, riskHotspotReport } from './yappcWorkflowData';

export default function LearnPage(): ReactElement {
  const t = useStudioTranslation();

  return (
    <section className="studio-section" aria-labelledby="learn-title">
      <div className="studio-card">
        <h2 id="learn-title" className="text-xl font-semibold text-gray-950">
          {t('studio.route.learn.title')}
        </h2>
        <p className="mt-1 text-sm text-gray-600">{t('studio.route.learn.description')}</p>
        <div className="mt-4 space-y-3">
          {residualIslandReport.recommendedActions.map((action: string) => (
            <div key={action} className="rounded border border-gray-200 bg-white p-3">
              <h3 className="text-sm font-semibold text-gray-900">
                {t('studio.route.learn.recommendationTitle')}
              </h3>
              <p className="mt-2 text-sm text-gray-600">{action}</p>
            </div>
          ))}
          <div className="rounded border border-gray-200 bg-white p-3">
            <h3 className="text-sm font-semibold text-gray-900">
              {t('studio.route.learn.learningEvidenceTitle')}
            </h3>
            <p className="mt-2 text-sm text-gray-600">{generatedChangeSetSummary.validationEvidenceRefs.join(', ')}</p>
            <p className="mt-1 text-xs text-gray-500">
              {t('studio.route.learn.highestRiskPrefix')} {riskHotspotReport.highestRiskLevel}
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
