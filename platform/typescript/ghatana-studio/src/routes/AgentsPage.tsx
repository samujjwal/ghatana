import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

const PROPOSAL_FACTORS = ['policy', 'mastery', 'risk', 'approval', 'verification', 'rollback'] as const;

export default function AgentsPage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const approvalRefs = lifecycleData.selectedRun?.approvalRefs ?? [];

  return (
    <section className="space-y-6" aria-labelledby="agents-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(lifecycleData.status)} variant="soft">
          {describeLifecycleDataStatus(lifecycleData.status)}
        </Badge>
        <h2 id="agents-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.agents.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.agents.description')}
        </p>
      </div>

      <article className="studio-card space-y-4" aria-labelledby="agent-proposal-title">
        <div className="flex items-center justify-between gap-3">
          <h3 id="agent-proposal-title" className="text-base font-semibold text-gray-950">
            {t('studio.route.agents.proposalTitle')}
          </h3>
          <Badge tone="warning" variant="soft">
            {approvalRefs.length > 0
              ? `${approvalRefs.length} approvals`
              : t('studio.route.agents.badge.fallback')}
          </Badge>
        </div>
        <div className="grid gap-3 md:grid-cols-3">
          {PROPOSAL_FACTORS.map((factor: string) => (
            <div key={factor} className="rounded-md border border-gray-200 p-3">
              <h4 className="text-sm font-semibold text-gray-950">{factor}</h4>
              <p className="mt-1 text-sm text-gray-600">
                {t('studio.route.agents.evidenceRequired')}
              </p>
            </div>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="rounded-md border border-gray-300 px-3 py-2 text-sm font-medium">
            {t('studio.route.agents.approve')}
          </button>
          <button type="button" className="rounded-md border border-gray-300 px-3 py-2 text-sm font-medium">
            {t('studio.route.agents.reject')}
          </button>
        </div>
        <p className="text-sm leading-6 text-gray-600">
          {t('studio.route.agents.noRawExecution')}
        </p>
      </article>
    </section>
  );
}
