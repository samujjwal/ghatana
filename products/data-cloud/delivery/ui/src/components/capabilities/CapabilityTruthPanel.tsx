import React from 'react';
import { AlertTriangle, CheckCircle2, HelpCircle, MinusCircle } from 'lucide-react';
import type { SurfaceSignal } from '../../api/surfaces.service';
import { cn } from '../../lib/theme';

interface CapabilityTruthPanelProps {
  title: string;
  description: string;
  capabilities: SurfaceSignal[];
  compact?: boolean;
}

function statusConfig(status: SurfaceSignal['status']): {
  icon: React.ReactNode;
  badgeClassName: string;
  cardClassName: string;
} {
  switch (status) {
    case 'LIVE':
      return {
        icon: <CheckCircle2 className="h-4 w-4" />,
        badgeClassName: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300',
        cardClassName: 'border-emerald-200 dark:border-emerald-800/70',
      };
    case 'DEGRADED':
    case 'PREVIEW':
      return {
        icon: <AlertTriangle className="h-4 w-4" />,
        badgeClassName: 'bg-amber-100 text-amber-900 dark:bg-amber-900/30 dark:text-amber-300',
        cardClassName: 'border-amber-200 dark:border-amber-800/70',
      };
    case 'UNAVAILABLE':
    case 'DISABLED':
    case 'MISCONFIGURED':
      return {
        icon: <MinusCircle className="h-4 w-4" />,
        badgeClassName: 'bg-rose-100 text-rose-900 dark:bg-rose-900/30 dark:text-rose-300',
        cardClassName: 'border-rose-200 dark:border-rose-800/70',
      };
    default:
      return {
        icon: <HelpCircle className="h-4 w-4" />,
        badgeClassName: 'bg-slate-100 text-slate-800 dark:bg-slate-900/30 dark:text-slate-300',
        cardClassName: 'border-slate-200 dark:border-slate-700',
      };
  }
}

export function CapabilityTruthPanel({
  title,
  description,
  capabilities,
  compact = false,
}: CapabilityTruthPanelProps): React.ReactElement {
  return (
    <section className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
      <div className="flex items-start justify-between gap-4 mb-4">
        <div>
          <h2 className="text-sm font-medium text-gray-900 dark:text-white">{title}</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{description}</p>
        </div>
        <span className="text-xs text-gray-400 whitespace-nowrap">{capabilities.length} surfaces</span>
      </div>

      <div className={cn('grid gap-3', compact ? 'grid-cols-1' : 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3')}>
        {capabilities.map((capability) => {
          const config = statusConfig(capability.status);
          return (
            <article
              key={capability.key}
              className={cn(
                'rounded-xl border bg-gray-50 dark:bg-gray-900/40 px-4 py-3',
                config.cardClassName,
              )}
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-sm font-medium text-gray-900 dark:text-white">{capability.label}</div>
                  <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">{capability.key}</div>
                </div>
                <span className={cn('inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-medium', config.badgeClassName)}>
                  {config.icon}
                  {capability.summary}
                </span>
              </div>
              {capability.detail && (
                <p className="mt-3 text-sm text-gray-600 dark:text-gray-300">{capability.detail}</p>
              )}
            </article>
          );
        })}
      </div>
    </section>
  );
}