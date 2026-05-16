import type { ReactElement } from 'react';
import { useState } from 'react';
import { Badge, Button } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

const RISK_LEVELS = ['low', 'medium', 'high', 'critical'] as const;
const GOVERNANCE_DECISIONS = ['allowed', 'denied', 'requires-approval'] as const;
const APPROVAL_DECISIONS = ['not-required', 'pending', 'approved', 'rejected'] as const;
const HEALTH_STATUSES = ['healthy', 'degraded', 'unhealthy', 'unknown'] as const;
const ROLLBACK_READINESS = ['ready', 'not-ready', 'not-required'] as const;

type RiskLevel = (typeof RISK_LEVELS)[number];
type GovernanceDecision = (typeof GOVERNANCE_DECISIONS)[number];
type ApprovalDecision = (typeof APPROVAL_DECISIONS)[number];
type HealthStatus = (typeof HEALTH_STATUSES)[number];
type RollbackReadiness = (typeof ROLLBACK_READINESS)[number];

interface ProposalCard {
  readonly requestedAction: string;
  readonly productUnitId: string;
  readonly phase: string;
  readonly riskLevel: RiskLevel;
  readonly policyDecision: GovernanceDecision;
  readonly masteryDecision: GovernanceDecision;
  readonly approvalDecision: ApprovalDecision;
  readonly requiredNextAction: string;
  readonly evidenceRefs: readonly string[];
  readonly rollbackReadiness: RollbackReadiness;
  readonly healthStatus: HealthStatus;
  readonly correlationId: string;
}

export default function AgentsPage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const [isGovernanceAvailable] = useState(true);

  // Mock proposal data - in real implementation, this would come from the agent lifecycle client
  const mockProposals: ProposalCard[] = [
    {
      requestedAction: 'deploy-local',
      productUnitId: 'digital-marketing',
      phase: 'deploy',
      riskLevel: 'medium',
      policyDecision: 'allowed',
      masteryDecision: 'requires-approval',
      approvalDecision: 'pending',
      requiredNextAction: 'request-approval',
      evidenceRefs: ['evidence:graph-summary-123', 'evidence:risk-hotspot-456'],
      rollbackReadiness: 'ready',
      healthStatus: 'unknown',
      correlationId: 'studio-agent-123',
    },
  ];

  const canExecute = (proposal: ProposalCard): boolean => {
    return (
      proposal.policyDecision === 'allowed' &&
      proposal.masteryDecision === 'allowed' &&
      (proposal.approvalDecision === 'not-required' || proposal.approvalDecision === 'approved')
    );
  };

  const getDecisionTone = (decision: GovernanceDecision | ApprovalDecision): 'success' | 'warning' | 'danger' | 'neutral' => {
    if (decision === 'allowed' || decision === 'approved' || decision === 'not-required') return 'success';
    if (decision === 'denied' || decision === 'rejected') return 'danger';
    if (decision === 'requires-approval' || decision === 'pending') return 'warning';
    return 'neutral';
  };

  const getHealthTone = (status: HealthStatus): 'success' | 'warning' | 'danger' | 'neutral' => {
    if (status === 'healthy') return 'success';
    if (status === 'degraded') return 'warning';
    if (status === 'unhealthy') return 'danger';
    return 'neutral';
  };

  const getRollbackTone = (readiness: RollbackReadiness): 'success' | 'warning' | 'danger' | 'neutral' => {
    if (readiness === 'ready') return 'success';
    if (readiness === 'not-ready') return 'danger';
    return 'neutral';
  };

  return (
    <section className="space-y-6" aria-labelledby="agents-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(lifecycleData.snapshot.status)} variant="soft">
          {describeLifecycleDataStatus(lifecycleData.snapshot.status)}
        </Badge>
        <h2 id="agents-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.agents.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.agents.description')}
        </p>
      </div>

      {!isGovernanceAvailable && (
        <article className="studio-card border-yellow-200 bg-yellow-50" aria-labelledby="governance-warning-title">
          <h3 id="governance-warning-title" className="text-base font-semibold text-yellow-900">
            Governance Providers Unavailable
          </h3>
          <p className="text-sm text-yellow-800">
            Policy, mastery, and approval providers are currently unavailable. Agentic actions are blocked until governance is restored.
          </p>
        </article>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {mockProposals.map((proposal) => (
          <article
            key={proposal.correlationId}
            className="studio-card space-y-4"
            aria-labelledby={`proposal-${proposal.correlationId}-title`}
          >
            <div className="flex items-center justify-between gap-3">
              <h3 id={`proposal-${proposal.correlationId}-title`} className="text-base font-semibold text-gray-950">
                {proposal.requestedAction}
              </h3>
              <Badge tone={canExecute(proposal) ? 'success' : 'warning'} variant="soft">
                {canExecute(proposal) ? 'Ready to execute' : 'Requires action'}
              </Badge>
            </div>

            <div className="grid gap-3 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-600">ProductUnit:</span>
                <span className="font-medium text-gray-900">{proposal.productUnitId}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">Phase:</span>
                <span className="font-medium text-gray-900">{proposal.phase}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">Risk Level:</span>
                <Badge tone={proposal.riskLevel === 'critical' ? 'danger' : proposal.riskLevel === 'high' ? 'warning' : 'success'} variant="soft" className="text-xs">
                  {proposal.riskLevel}
                </Badge>
              </div>
            </div>

            <div className="space-y-2">
              <h4 className="text-sm font-medium text-gray-900">Governance Decisions</h4>
              <div className="grid gap-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-600">Policy:</span>
                  <Badge tone={getDecisionTone(proposal.policyDecision)} variant="soft" className="text-xs">
                    {proposal.policyDecision}
                  </Badge>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Mastery:</span>
                  <Badge tone={getDecisionTone(proposal.masteryDecision)} variant="soft" className="text-xs">
                    {proposal.masteryDecision}
                  </Badge>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Approval:</span>
                  <Badge tone={getDecisionTone(proposal.approvalDecision)} variant="soft" className="text-xs">
                    {proposal.approvalDecision}
                  </Badge>
                </div>
              </div>
            </div>

            <div className="space-y-2">
              <h4 className="text-sm font-medium text-gray-900">Readiness</h4>
              <div className="grid gap-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-600">Health Status:</span>
                  <Badge tone={getHealthTone(proposal.healthStatus)} variant="soft" className="text-xs">
                    {proposal.healthStatus}
                  </Badge>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Rollback Readiness:</span>
                  <Badge tone={getRollbackTone(proposal.rollbackReadiness)} variant="soft" className="text-xs">
                    {proposal.rollbackReadiness}
                  </Badge>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Required Next Action:</span>
                  <span className="font-medium text-gray-900">{proposal.requiredNextAction}</span>
                </div>
              </div>
            </div>

            <div className="space-y-2">
              <h4 className="text-sm font-medium text-gray-900">Evidence Refs</h4>
              <ul className="space-y-1 text-xs text-gray-600">
                {proposal.evidenceRefs.map((ref) => (
                  <li key={ref} className="font-mono">{ref}</li>
                ))}
              </ul>
            </div>

            <div className="flex flex-wrap gap-2">
              {proposal.approvalDecision === 'pending' && (
                <>
                  <Button variant="primary" size="sm">
                    {t('studio.route.agents.approve')}
                  </Button>
                  <Button variant="outline" size="sm">
                    {t('studio.route.agents.reject')}
                  </Button>
                </>
              )}
              <Button
                variant={canExecute(proposal) ? 'primary' : 'outline'}
                size="sm"
                disabled={!canExecute(proposal)}
              >
                Execute
              </Button>
            </div>
          </article>
        ))}
      </div>

      <p className="text-sm leading-6 text-gray-600">
        {t('studio.route.agents.noRawExecution')}
      </p>
    </section>
  );
}
