import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';

interface IdeationRouteStatusPanelProps {
  readonly ownershipLabel: string;
  readonly currentStatusLabel: string;
  readonly requiredNextActionLabel: string;
  readonly handoffReady: boolean;
  readonly handoffReadinessLabel: string;
  readonly evidenceRefs: readonly string[];
}

export default function IdeationRouteStatusPanel(props: IdeationRouteStatusPanelProps): ReactElement {
  const {
    ownershipLabel,
    currentStatusLabel,
    requiredNextActionLabel,
    handoffReady,
    handoffReadinessLabel,
    evidenceRefs,
  } = props;

  return (
    <article className="rounded-md border border-gray-200 bg-gray-50 p-4" aria-label="ideation route status">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-gray-900">Route status</h3>
        <Badge tone={handoffReady ? 'success' : 'warning'} variant="soft" className="text-xs">
          {handoffReadinessLabel}
        </Badge>
      </div>
      <dl className="mt-3 grid gap-2 text-sm md:grid-cols-2">
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">Ownership</dt>
          <dd className="mt-1 text-gray-900">{ownershipLabel}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">Current status</dt>
          <dd className="mt-1 text-gray-900">{currentStatusLabel}</dd>
        </div>
        <div className="md:col-span-2">
          <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">Required next action</dt>
          <dd className="mt-1 text-gray-900">{requiredNextActionLabel}</dd>
        </div>
      </dl>
      <div className="mt-3">
        <h4 className="text-xs font-medium uppercase tracking-wide text-gray-500">Evidence refs</h4>
        <ul className="mt-1 space-y-1 text-xs text-gray-700">
          {evidenceRefs.map((ref) => (
            <li key={ref} className="font-mono">{ref}</li>
          ))}
        </ul>
      </div>
    </article>
  );
}
