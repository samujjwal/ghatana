import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import type { StudioTranslationKey } from '../i18n/studioTranslations';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { generatedChangeSetSummary, residualIslandReport, riskHotspotReport } from './yappcWorkflowData';

type TranslateFn = (key: StudioTranslationKey, params?: { readonly [key: string]: string | number }) => string;

function learnRiskLevelLabel(riskLevel: string, t: TranslateFn): string {
  if (riskLevel === 'critical' || riskLevel === 'high' || riskLevel === 'medium' || riskLevel === 'low') {
    return t(`studio.route.learn.riskLevel.${riskLevel}`);
  }
  return t('studio.route.learn.riskLevel.unknown');
}

export default function LearnPage(): ReactElement {
  const t = useStudioTranslation();

  const getConfidenceTone = (confidence: number): 'success' | 'warning' | 'danger' => {
    if (confidence >= 0.9) return 'success';
    if (confidence >= 0.7) return 'warning';
    return 'danger';
  };

  const getRiskReductionImpact = (originalRisk: string, recommendedAction: string): 'high' | 'medium' | 'low' => {
    // Mock logic for risk reduction impact
    if (originalRisk === 'high' && recommendedAction.includes('review')) return 'high';
    if (originalRisk === 'medium' || originalRisk === 'high') return 'medium';
    return 'low';
  };

  return (
    <section className="space-y-6" aria-labelledby="learn-title">
      <div className="space-y-2">
        <h2 id="learn-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.learn.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">{t('studio.route.learn.description')}</p>
      </div>

      {/* Recommendations */}
      <article className="studio-card space-y-4" aria-labelledby="recommendations-title">
        <h3 id="recommendations-title" className="text-base font-semibold text-gray-950">
          {t('studio.route.learn.recommendationsTitle')}
        </h3>
        <div className="space-y-3">
          {residualIslandReport.recommendedActions.map((action, index) => (
            <div key={index} className="rounded-md border border-gray-200 p-4">
              <div className="flex items-start justify-between gap-3">
                <h4 className="text-sm font-medium text-gray-900">{t('studio.route.learn.recommendationTitle')} {index + 1}</h4>
                <Badge tone="success" variant="soft" className="text-xs">
                  {getRiskReductionImpact(riskHotspotReport.highestRiskLevel, action)} impact
                </Badge>
              </div>
              <p className="mt-2 text-sm text-gray-700">{action}</p>
              <div className="mt-3 space-y-2">
                <h5 className="text-xs font-medium text-gray-900">{t('studio.route.learn.whyRecommendedTitle')}</h5>
                <p className="text-xs text-gray-600">
                  {t('studio.route.learn.whyRecommendedText', { risk: learnRiskLevelLabel(riskHotspotReport.highestRiskLevel, t) })}
                </p>
              </div>
              <div className="mt-3 space-y-2">
                <h5 className="text-xs font-medium text-gray-900">{t('studio.route.learn.supportingEvidenceTitle')}</h5>
                <ul className="space-y-1 text-xs text-gray-600">
                  <li className="font-mono">{residualIslandReport.evidenceId}</li>
                  <li className="font-mono">{riskHotspotReport.evidenceId}</li>
                </ul>
              </div>
            </div>
          ))}
        </div>
      </article>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Learning Evidence */}
        <article className="studio-card space-y-3" aria-labelledby="learning-evidence-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="learning-evidence-title" className="text-base font-semibold text-gray-950">
              {t('studio.route.learn.learningEvidenceTitle')}
            </h3>
            <Badge tone={getConfidenceTone(generatedChangeSetSummary.confidence)} variant="soft" className="text-xs">
              {Math.round(generatedChangeSetSummary.confidence * 100)}% confidence
            </Badge>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.learn.validationEvidenceTitle')}</h4>
            <ul className="space-y-1 text-xs text-gray-600">
              {generatedChangeSetSummary.validationEvidenceRefs.map((ref) => (
                <li key={ref} className="font-mono">{ref}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.learn.generatedArtifactsTitle')}</h4>
            <ul className="space-y-1 text-xs text-gray-600">
              {generatedChangeSetSummary.generatedArtifactRefs.map((ref) => (
                <li key={ref} className="font-mono">{ref}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-900">{t('studio.route.learn.evidenceRefTitle')}</h4>
            <code className="block rounded-md bg-gray-100 p-2 text-xs text-gray-900">
              {generatedChangeSetSummary.evidenceId}
            </code>
          </div>
        </article>

        {/* Risk Reduction Impact */}
        <article className="studio-card space-y-3 border-blue-200 bg-blue-50" aria-labelledby="risk-impact-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="risk-impact-title" className="text-base font-semibold text-blue-900">
              {t('studio.route.learn.riskReductionImpactTitle')}
            </h3>
            <Badge tone={getConfidenceTone(riskHotspotReport.confidence)} variant="soft" className="text-xs">
              {Math.round(riskHotspotReport.confidence * 100)}% confidence
            </Badge>
          </div>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-blue-800">{t('studio.route.learn.highestRiskPrefix')}:</span>
              <Badge tone={riskHotspotReport.highestRiskLevel === 'critical' ? 'danger' : riskHotspotReport.highestRiskLevel === 'high' ? 'warning' : 'success'} variant="soft" className="text-xs">
                {learnRiskLevelLabel(riskHotspotReport.highestRiskLevel, t)}
              </Badge>
            </div>
            <div className="flex justify-between">
              <span className="text-blue-800">{t('studio.route.learn.hotspotCountLabel')}:</span>
              <span className="font-medium text-blue-900">{riskHotspotReport.hotspotCount}</span>
            </div>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-blue-900">{t('studio.route.learn.riskReductionTitle')}</h4>
            <p className="text-xs text-blue-800">
              {t('studio.route.learn.riskReductionText', { count: riskHotspotReport.hotspotCount })}
            </p>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-blue-900">{t('studio.route.learn.provenanceTitle')}</h4>
            <ul className="space-y-1 text-xs text-blue-700">
              {riskHotspotReport.provenanceRefs.map((ref) => (
                <li key={ref} className="font-mono">{ref}</li>
              ))}
            </ul>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-blue-900">{t('studio.route.learn.evidenceRefTitle')}</h4>
            <code className="block rounded-md bg-blue-100 p-2 text-xs text-blue-900">
              {riskHotspotReport.evidenceId}
            </code>
          </div>
        </article>
      </div>

      {/* Privacy Notice */}
      <article className="studio-card space-y-3 border-gray-300 bg-gray-50" aria-labelledby="privacy-notice-title">
        <h3 id="privacy-notice-title" className="text-base font-semibold text-gray-900">
          {t('studio.route.learn.privacyNoticeTitle')}
        </h3>
        <p className="text-sm text-gray-700">
          {t('studio.route.learn.privacyNoticeText')}
        </p>
        <div className="space-y-2 text-xs text-gray-600">
          <p>• {t('studio.route.learn.privacyPoint1')}</p>
          <p>• {t('studio.route.learn.privacyPoint2')}</p>
          <p>• {t('studio.route.learn.privacyPoint3')}</p>
        </div>
      </article>
    </section>
  );
}
