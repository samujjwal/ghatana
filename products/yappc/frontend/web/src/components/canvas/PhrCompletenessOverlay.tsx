import { useMemo, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  CircleDashed,
  ShieldOff,
  Smartphone,
  TestTube2,
  X,
} from 'lucide-react';

import { Button } from '../ui/Button';
import type {
  PhrCompletenessGap,
  PhrCompletenessOverlayModel,
  PhrCompletenessRouteRow,
  PhrRouteLifecycle,
} from '@/lib/phr/phrCompletenessOverlay';

export interface PhrCompletenessOverlayProps {
  model: PhrCompletenessOverlayModel;
  collapsed?: boolean;
  onDismiss?: () => void;
}

const lifecycleTone: Record<PhrRouteLifecycle, string> = {
  stable: 'bg-success-bg text-success-color border-success-border',
  hidden: 'bg-surface-muted text-fg-muted border-border',
  blocked: 'bg-destructive-bg text-destructive border-destructive-border',
  preview: 'bg-info-bg text-info-color border-info-border',
};

function coverageIcon(covered: boolean, label: string) {
  return covered ? (
    <CheckCircle2 aria-label={`${label} covered`} className="h-4 w-4 text-success-color" />
  ) : (
    <AlertTriangle aria-label={`${label} gap`} className="h-4 w-4 text-warning-color" />
  );
}

function RouteCoverageRow({ route }: { route: PhrCompletenessRouteRow }) {
  return (
    <li className="grid grid-cols-[minmax(0,1fr)_auto] gap-3 rounded border border-border bg-bg-paper px-3 py-2">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-medium text-text-primary">{route.label}</span>
          <span className={`rounded border px-1.5 py-0.5 text-[11px] font-medium ${lifecycleTone[route.lifecycle]}`}>
            {route.lifecycle}
          </span>
        </div>
        <div className="mt-1 truncate text-xs text-text-secondary">{route.path}</div>
      </div>
      <div className="flex items-center gap-2" aria-label={`${route.label} coverage`}>
        {coverageIcon(route.webCovered, 'web')}
        <Smartphone
          aria-label={route.mobileCovered ? 'mobile covered' : 'mobile gap'}
          className={`h-4 w-4 ${route.mobileCovered ? 'text-success-color' : 'text-warning-color'}`}
        />
        {coverageIcon(route.backendCovered, 'backend')}
        <TestTube2
          aria-label={route.testCovered ? 'test covered' : 'test gap'}
          className={`h-4 w-4 ${route.testCovered ? 'text-success-color' : 'text-warning-color'}`}
        />
      </div>
    </li>
  );
}

function GapRow({ gap }: { gap: PhrCompletenessGap }) {
  return (
    <li className="rounded border border-warning-border bg-warning-bg px-3 py-2 text-xs text-warning-color">
      <span className="font-medium">{gap.routePath}</span>
      <span className="mx-1">/</span>
      <span>{gap.message}</span>
    </li>
  );
}

export function PhrCompletenessOverlay({
  model,
  collapsed = false,
  onDismiss,
}: PhrCompletenessOverlayProps) {
  const [isCollapsed, setIsCollapsed] = useState(collapsed);
  const visibleRoutes = useMemo(
    () => [...model.routes].sort((left, right) => left.score - right.score).slice(0, 6),
    [model.routes],
  );
  const visibleGaps = model.gaps.slice(0, 4);

  if (isCollapsed) {
    return (
      <div className="fixed bottom-12 right-4 z-40">
        <Button
          type="button"
          variant="ghost"
          size="small"
          className="rounded-lg border border-border bg-bg-paper px-3 py-2 text-sm shadow-lg"
          onClick={() => setIsCollapsed(false)}
        >
          <CircleDashed className="mr-2 h-4 w-4" />
          PHR coverage {model.totals.stableCoveragePercent}%
        </Button>
      </div>
    );
  }

  return (
    <aside
      className="fixed bottom-12 right-4 z-40 w-[min(420px,calc(100vw-2rem))] rounded-lg border border-border bg-bg-paper shadow-xl"
      aria-label="PHR route completeness"
    >
      <header className="flex items-start justify-between gap-3 border-b border-border px-4 py-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <CircleDashed className="h-4 w-4 text-info-color" />
            <h2 className="text-sm font-semibold text-text-primary">PHR route completeness</h2>
          </div>
          <p className="mt-1 text-xs text-text-secondary">
            {model.totals.stableRoutes} stable / {model.totals.hiddenRoutes} hidden / {model.totals.blockedRoutes} blocked
          </p>
        </div>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="small"
            aria-label="Collapse PHR route completeness"
            onClick={() => setIsCollapsed(true)}
          >
            <ChevronDown className="h-4 w-4" />
          </Button>
          {onDismiss && (
            <Button
              type="button"
              variant="ghost"
              size="small"
              aria-label="Dismiss PHR route completeness"
              onClick={onDismiss}
            >
              <X className="h-4 w-4" />
            </Button>
          )}
        </div>
      </header>

      <div className="space-y-3 px-4 py-3">
        <div>
          <div className="mb-1 flex items-center justify-between text-xs text-text-secondary">
            <span>Stable coverage</span>
            <span>{model.totals.stableCoveragePercent}%</span>
          </div>
          <div className="h-2 overflow-hidden rounded bg-surface-muted">
            <div
              className="h-full rounded bg-success-color"
              style={{ width: `${model.totals.stableCoveragePercent}%` }}
            />
          </div>
        </div>

        <div className="grid grid-cols-3 gap-2 text-center text-xs">
          <div className="rounded border border-border px-2 py-2">
            <div className="font-semibold text-text-primary">{model.totals.routes}</div>
            <div className="text-text-secondary">routes</div>
          </div>
          <div className="rounded border border-border px-2 py-2">
            <div className="font-semibold text-text-primary">{model.totals.gapCount}</div>
            <div className="text-text-secondary">gaps</div>
          </div>
          <div className="rounded border border-border px-2 py-2">
            <div className="flex items-center justify-center gap-1 font-semibold text-text-primary">
              <ShieldOff className="h-3.5 w-3.5" />
              {model.totals.hiddenRoutes + model.totals.blockedRoutes}
            </div>
            <div className="text-text-secondary">guarded</div>
          </div>
        </div>

        <ul className="space-y-2" aria-label="Lowest scoring PHR routes">
          {visibleRoutes.map((route) => (
            <RouteCoverageRow key={route.path} route={route} />
          ))}
        </ul>

        {visibleGaps.length > 0 && (
          <details className="group" open>
            <summary className="flex cursor-pointer list-none items-center justify-between text-xs font-medium text-text-secondary">
              Coverage gaps
              <ChevronUp className="h-4 w-4 group-open:block" />
            </summary>
            <ul className="mt-2 space-y-2">
              {visibleGaps.map((gap) => (
                <GapRow key={`${gap.routePath}-${gap.category}`} gap={gap} />
              ))}
            </ul>
          </details>
        )}
      </div>
    </aside>
  );
}
