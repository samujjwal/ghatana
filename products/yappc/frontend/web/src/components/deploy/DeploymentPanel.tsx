import { ShieldCheck, Siren, TrendingUp } from 'lucide-react';

import { cn } from '@/lib/utils';

export type DeploymentStrategy = 'IMMEDIATE' | 'ROLLING' | 'CANARY' | 'BLUE_GREEN';

export interface DeploymentPlanSummary {
  strategy: DeploymentStrategy;
  riskScore: number;
  readiness: number;
  rationale: string;
  riskFactors: string[];
  blockers: string[];
  requiresApproval: boolean;
  canaryPercent?: number;
}

export interface DeploymentPanelProps {
  plan: DeploymentPlanSummary;
}

function riskTone(score: number): string {
  if (score >= 7) {
    return 'text-red-700 bg-red-100';
  }
  if (score >= 4) {
    return 'text-amber-700 bg-amber-100';
  }
  return 'text-green-700 bg-green-100';
}

export function DeploymentPanel({ plan }: DeploymentPanelProps) {
  return (
    <section className="flex h-full flex-col bg-bg-default" data-testid="deployment-panel">
      <div className="border-b border-divider bg-bg-paper px-4 py-3">
        <h2 className="text-lg font-semibold text-text-primary">AI deployment strategy</h2>
        <p className="text-sm text-text-secondary">
          Deployment guidance derived from lifecycle readiness and rollout risk.
        </p>
      </div>

      <div className="grid gap-4 p-4 lg:grid-cols-[1.2fr,0.8fr]">
        <article className="rounded-xl border border-divider bg-bg-paper p-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="text-xs font-semibold uppercase tracking-wide text-text-secondary">
                Recommended strategy
              </div>
              <div className="mt-2 flex items-center gap-3">
                <span className="text-2xl font-semibold text-text-primary">{plan.strategy}</span>
                <span className={cn('rounded-full px-3 py-1 text-xs font-semibold', riskTone(plan.riskScore))}>
                  Risk {plan.riskScore.toFixed(1)}/10
                </span>
              </div>
              <p className="mt-3 text-sm text-text-secondary">{plan.rationale}</p>
            </div>
            <div className="rounded-xl bg-primary-50 p-3 text-primary-700">
              <TrendingUp className="h-5 w-5" />
            </div>
          </div>

          <div className="mt-5 grid gap-3 md:grid-cols-3">
            <div className="rounded-lg bg-bg-default p-3">
              <div className="text-xs text-text-secondary">Lifecycle readiness</div>
              <div className="mt-1 text-xl font-semibold text-text-primary">{plan.readiness}%</div>
            </div>
            <div className="rounded-lg bg-bg-default p-3">
              <div className="text-xs text-text-secondary">Approval status</div>
              <div className="mt-1 text-xl font-semibold text-text-primary">
                {plan.requiresApproval ? 'Required' : 'Not required'}
              </div>
            </div>
            <div className="rounded-lg bg-bg-default p-3">
              <div className="text-xs text-text-secondary">Canary traffic</div>
              <div className="mt-1 text-xl font-semibold text-text-primary">
                {plan.strategy === 'CANARY' ? `${plan.canaryPercent ?? 5}%` : 'N/A'}
              </div>
            </div>
          </div>
        </article>

        <article className="rounded-xl border border-divider bg-bg-paper p-4">
          <div className="flex items-center gap-2 text-sm font-semibold text-text-primary">
            <ShieldCheck className="h-4 w-4 text-primary-700" />
            Risk factors
          </div>
          <ul className="mt-4 space-y-2 text-sm text-text-secondary">
            {plan.riskFactors.length === 0 ? (
              <li>No elevated risk factors detected.</li>
            ) : (
              plan.riskFactors.map((factor) => (
                <li key={factor} className="rounded-lg bg-bg-default px-3 py-2">
                  {factor}
                </li>
              ))
            )}
          </ul>
        </article>

        <article className="rounded-xl border border-divider bg-bg-paper p-4 lg:col-span-2">
          <div className="flex items-center gap-2 text-sm font-semibold text-text-primary">
            <Siren className="h-4 w-4 text-amber-600" />
            Release blockers
          </div>
          <ul className="mt-4 space-y-2 text-sm text-text-secondary">
            {plan.blockers.length === 0 ? (
              <li className="rounded-lg bg-green-50 px-3 py-2 text-green-700">
                No blockers currently prevent rollout.
              </li>
            ) : (
              plan.blockers.map((blocker) => (
                <li key={blocker} className="rounded-lg bg-amber-50 px-3 py-2 text-amber-800">
                  {blocker}
                </li>
              ))
            )}
          </ul>
        </article>
      </div>
    </section>
  );
}