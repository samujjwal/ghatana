import { ArrowDownRight, ArrowUpRight, Cpu, DollarSign, MemoryStick } from 'lucide-react';

export type CapacityAction = 'SCALE_UP' | 'SCALE_DOWN' | 'RIGHTSIZE' | 'HOLD';

export interface CapacityRecommendationView {
  action: CapacityAction;
  currentReplicas: number;
  targetReplicas: number;
  avgCpuUtilization: number;
  peakCpuUtilization: number;
  avgMemoryUtilization: number;
  currentMonthlyCost: number;
  projectedMonthlyCost: number;
  confidence: number;
  rationale: string;
}

export interface CapacityDashboardProps {
  recommendation: CapacityRecommendationView;
}

function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`;
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(value);
}

function actionTone(action: CapacityAction): string {
  switch (action) {
    case 'SCALE_UP':
      return 'bg-info-bg text-info-color';
    case 'SCALE_DOWN':
      return 'bg-emerald-100 text-emerald-700';
    case 'RIGHTSIZE':
      return 'bg-warning-bg text-warning-color';
    default:
      return 'bg-grey-100 text-text-secondary';
  }
}

export function CapacityDashboard({ recommendation }: CapacityDashboardProps) {
  const costDelta = recommendation.projectedMonthlyCost - recommendation.currentMonthlyCost;

  return (
    <section className="flex h-full flex-col bg-bg-default" data-testid="capacity-dashboard">
      <div className="border-b border-divider bg-bg-paper px-4 py-3">
        <h2 className="text-lg font-semibold text-text-primary">Capacity advisor</h2>
        <p className="text-sm text-text-secondary">
          Short-horizon scaling recommendation for the current deployment posture.
        </p>
      </div>

      <div className="grid gap-4 p-4 lg:grid-cols-[1.2fr,0.8fr]">
        <article className="rounded-xl border border-divider bg-bg-paper p-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="text-xs font-semibold uppercase tracking-wide text-text-secondary">
                Recommended action
              </div>
              <div className="mt-2 flex items-center gap-3">
                <span className={`rounded-full px-3 py-1 text-xs font-semibold ${actionTone(recommendation.action)}`}>
                  {recommendation.action}
                </span>
                <span className="text-lg font-semibold text-text-primary">
                  {recommendation.currentReplicas} → {recommendation.targetReplicas} replicas
                </span>
              </div>
              <p className="mt-3 text-sm text-text-secondary">{recommendation.rationale}</p>
            </div>
            <div className="rounded-xl bg-primary-50 p-3 text-primary-700">
              {costDelta >= 0 ? <ArrowUpRight className="h-5 w-5" /> : <ArrowDownRight className="h-5 w-5" />}
            </div>
          </div>

          <div className="mt-5 grid gap-3 md:grid-cols-3">
            <div className="rounded-lg bg-bg-default p-3">
              <div className="flex items-center gap-2 text-xs text-text-secondary">
                <Cpu className="h-3.5 w-3.5" /> Avg CPU
              </div>
              <div className="mt-1 text-xl font-semibold text-text-primary">{formatPercent(recommendation.avgCpuUtilization)}</div>
              <div className="text-xs text-text-secondary">Peak {formatPercent(recommendation.peakCpuUtilization)}</div>
            </div>
            <div className="rounded-lg bg-bg-default p-3">
              <div className="flex items-center gap-2 text-xs text-text-secondary">
                <MemoryStick className="h-3.5 w-3.5" /> Avg memory
              </div>
              <div className="mt-1 text-xl font-semibold text-text-primary">{formatPercent(recommendation.avgMemoryUtilization)}</div>
            </div>
            <div className="rounded-lg bg-bg-default p-3">
              <div className="flex items-center gap-2 text-xs text-text-secondary">
                <DollarSign className="h-3.5 w-3.5" /> Confidence
              </div>
              <div className="mt-1 text-xl font-semibold text-text-primary">{formatPercent(recommendation.confidence)}</div>
            </div>
          </div>
        </article>

        <article className="rounded-xl border border-divider bg-bg-paper p-4">
          <div className="text-sm font-semibold text-text-primary">Cost outlook</div>
          <div className="mt-4 space-y-3 text-sm text-text-secondary">
            <div className="flex items-center justify-between rounded-lg bg-bg-default px-3 py-2">
              <span>Current monthly cost</span>
              <span className="font-semibold text-text-primary">{formatCurrency(recommendation.currentMonthlyCost)}</span>
            </div>
            <div className="flex items-center justify-between rounded-lg bg-bg-default px-3 py-2">
              <span>Projected monthly cost</span>
              <span className="font-semibold text-text-primary">{formatCurrency(recommendation.projectedMonthlyCost)}</span>
            </div>
            <div className="flex items-center justify-between rounded-lg bg-bg-default px-3 py-2">
              <span>Delta</span>
              <span className={`font-semibold ${costDelta <= 0 ? 'text-success-color' : 'text-warning-color'}`}>
                {costDelta <= 0 ? '' : '+'}
                {formatCurrency(costDelta)}
              </span>
            </div>
          </div>
        </article>
      </div>
    </section>
  );
}