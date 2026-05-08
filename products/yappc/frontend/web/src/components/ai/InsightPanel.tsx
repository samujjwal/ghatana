import { Bell, CircleAlert, CircleCheck, Info, X } from 'lucide-react';

import { cn } from '@/lib/utils';
import type { InsightStreamItem } from '@/hooks/useInsightStream';
import { ConfidenceBadge } from './ConfidenceBadge';
import { Button } from '../ui/Button';

export interface InsightPanelProps {
  open: boolean;
  insights: InsightStreamItem[];
  unreadCount: number;
  onClose: () => void;
  onDismiss: (insightId: string) => void;
  onMarkAllRead: () => void;
}

const severityStyles: Record<InsightStreamItem['severity'], { badge: string; icon: typeof Info }> = {
  info: { badge: 'bg-sky-100 text-sky-700', icon: Info },
  warning: { badge: 'bg-warning-bg text-warning-color', icon: CircleAlert },
  error: { badge: 'bg-rose-100 text-rose-700', icon: CircleAlert },
  critical: { badge: 'bg-destructive-bg text-destructive', icon: CircleAlert },
};

function formatCategory(category: InsightStreamItem['category']): string {
  return category.replace(/-/g, ' ');
}

export function InsightPanel({
  open,
  insights,
  unreadCount,
  onClose,
  onDismiss,
  onMarkAllRead,
}: InsightPanelProps) {
  if (!open) {
    return null;
  }

  return (
    <aside
      className="fixed right-0 top-14 z-40 flex h-[calc(100vh-3.5rem)] w-full max-w-md flex-col border-l border-divider bg-bg-paper shadow-2xl"
      role="complementary"
      aria-label="AI insights panel"
      data-testid="insight-panel"
    >
      <div className="flex items-center justify-between border-b border-divider px-5 py-4">
        <div className="flex items-center gap-3">
          <div className="rounded-full bg-primary-100 p-2 text-primary-700">
            <Bell className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-sm font-semibold text-text-primary">Proactive AI insights</h2>
            <p className="text-xs text-text-secondary">{unreadCount} unread suggestions</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onMarkAllRead}
            className="rounded-md px-2 py-1 text-xs font-medium text-primary-700 hover:bg-primary-50"
          >
            Mark all read
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onClose}
            className="rounded-md p-1 text-text-secondary hover:bg-grey-100"
            aria-label="Close insights panel"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {insights.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
          <CircleCheck className="h-10 w-10 text-success-color" />
          <div>
            <h3 className="text-sm font-semibold text-text-primary">No active insights</h3>
            <p className="mt-1 text-sm text-text-secondary">
              New proactive suggestions will appear here as background analysis completes.
            </p>
          </div>
        </div>
      ) : (
        <div className="flex-1 space-y-3 overflow-auto px-4 py-4">
          {insights.map((insight) => {
            const severity = severityStyles[insight.severity];
            const SeverityIcon = severity.icon;

            return (
              <article
                key={insight.id}
                className={cn(
                  'rounded-xl border border-divider bg-bg-default p-4 transition-colors',
                  !insight.read && 'border-primary-200 bg-primary-50/30'
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={cn('inline-flex items-center gap-1 rounded-full px-2 py-1 text-[11px] font-semibold uppercase', severity.badge)}>
                        <SeverityIcon className="h-3 w-3" />
                        {insight.severity}
                      </span>
                      <span className="rounded-full bg-grey-100 px-2 py-1 text-[11px] uppercase tracking-wide text-text-secondary">
                        {formatCategory(insight.category)}
                      </span>
                    </div>
                    <div>
                      <h3 className="text-sm font-semibold text-text-primary">{insight.title}</h3>
                      <p className="mt-1 text-sm text-text-secondary">{insight.description}</p>
                    </div>
                    {insight.suggestion && (
                      <div className="rounded-lg bg-bg-paper px-3 py-2 text-sm text-text-primary">
                        <span className="font-medium">Suggested next step:</span> {insight.suggestion}
                      </div>
                    )}
                    <div className="flex flex-wrap items-center gap-3 text-xs text-text-secondary">
                      <ConfidenceBadge score={insight.confidence * 100} size="sm" />
                      {insight.sourceRef && <span>{insight.sourceRef}</span>}
                      <span>{new Date(insight.createdAt).toLocaleString()}</span>
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => onDismiss(insight.id)}
                    className="rounded-md p-1 text-text-secondary hover:bg-grey-100"
                    aria-label={`Dismiss ${insight.title}`}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </aside>
  );
}
