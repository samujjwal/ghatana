import { Button } from '@ghatana/design-system';
import { ArrowRight, RefreshCw } from 'lucide-react';
import { translate } from '../../i18n/messages';
import type { ProjectDashboardAction } from '../../lib/api';

export interface DashboardDecisionBriefProps {
    readonly headline: string;
    readonly description: string;
    readonly action: ProjectDashboardAction | null;
    readonly ctaLabel: string | null;
    readonly isDegraded: boolean;
    readonly correlationId?: string;
    readonly retryAvailable: boolean;
    readonly blockedCount: number;
    readonly reviewCount: number;
    readonly safeCount: number;
    readonly onActionClick: (action: ProjectDashboardAction) => void;
    readonly onRetry?: () => void;
}

export function DashboardDecisionBrief(props: DashboardDecisionBriefProps) {
    const {
        headline,
        description,
        action,
        ctaLabel,
        isDegraded,
        correlationId,
        retryAvailable,
        blockedCount,
        reviewCount,
        safeCount,
        onActionClick,
        onRetry,
    } = props;

    const pluralize = (count: number, singular: string, plural = `${singular}s`): string => {
        return `${count} ${count === 1 ? singular : plural}`;
    };

    return (
        <section
            aria-label="Dashboard decision brief"
            className="rounded-2xl border border-divider bg-bg-paper p-5 shadow-sm"
        >
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-secondary">
                        {translate('dashboard.whatToDoNext')}
                    </p>
                    <h2 className="mt-2 text-xl font-semibold text-text-primary">
                        {headline}
                    </h2>
                    <p className="mt-2 max-w-3xl text-sm text-text-secondary">
                        {description}
                    </p>
                    {isDegraded && correlationId && (
                        <p className="mt-2 text-xs text-text-secondary">
                            {translate('dashboard.correlationId')} <code className="text-xs bg-bg-elevated px-1.5 py-0.5 rounded">{correlationId}</code>
                        </p>
                    )}
                    <p className="mt-3 text-xs font-medium uppercase tracking-[0.14em] text-text-secondary">
                        {pluralize(blockedCount, 'blocked item')} · {pluralize(reviewCount, 'review item')} · {pluralize(safeCount, 'safe continuation')}
                    </p>
                </div>
                {action !== null && ctaLabel && (
                    <Button
                        type="button"
                        variant="solid"
                        onClick={() => onActionClick(action)}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-700"
                    >
                        {ctaLabel}
                        <ArrowRight className="h-4 w-4" />
                    </Button>
                )}
                {isDegraded && retryAvailable && (
                    <Button
                        type="button"
                        variant="outline"
                        onClick={onRetry}
                        className="inline-flex items-center justify-center gap-2 rounded-lg border border-divider bg-bg-elevated px-4 py-2.5 text-sm font-semibold text-text-primary transition-colors hover:bg-bg-hover"
                    >
                        <RefreshCw className="h-4 w-4" />
                        {translate('dashboard.retry')}
                    </Button>
                )}
            </div>
        </section>
    );
}
