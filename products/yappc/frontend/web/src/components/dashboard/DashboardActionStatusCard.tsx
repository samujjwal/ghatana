import { Button } from '@ghatana/design-system';
import { RefreshCw } from 'lucide-react';
import { translate } from '../../i18n/messages';
import type { ProjectDashboardAction } from '../../lib/api';

export interface DashboardActionStatusCardProps {
    readonly titleKey: string;
    readonly tone: 'warning' | 'review' | 'safe';
    readonly actions: readonly ProjectDashboardAction[];
    readonly loading: boolean;
    readonly error: unknown;
    readonly emptyTextKey: string;
    readonly onOpenProject: (action: ProjectDashboardAction) => void;
    readonly onRetry?: () => void;
}

export function DashboardActionStatusCard(props: DashboardActionStatusCardProps) {
    const { titleKey, tone, actions, loading, error, emptyTextKey, onOpenProject, onRetry } = props;
    const title = translate(titleKey);
    const emptyText = translate(emptyTextKey);

    const getErrorDetails = (err: unknown): { message: string; correlationId?: string } => {
        if (typeof err === 'string') {
            return { message: err };
        }
        if (typeof err === 'object' && err !== null) {
            const errorObj = err as Record<string, unknown>;
            return {
                message: typeof errorObj.message === 'string' ? errorObj.message : 'Unknown error',
                correlationId: typeof errorObj.correlationId === 'string' ? errorObj.correlationId : undefined,
            };
        }
        return { message: 'Unknown error' };
    };

    const errorDetails = error ? getErrorDetails(error) : null;

    return (
        <div className="rounded-2xl border border-divider bg-bg-paper p-5 shadow-sm">
            <div className="flex items-center justify-between gap-3">
                <h2 className="text-base font-semibold">{title}</h2>
                <span className="rounded-full bg-white/70 px-2 py-0.5 text-xs font-semibold dark:bg-black/20">
                    {actions.length}
                </span>
            </div>

            {loading ? (
                <p className="mt-4 text-sm opacity-80">{translate('dashboard.loadingActionStatus')}</p>
            ) : error ? (
                <div className="mt-4 space-y-3">
                    <p className="text-sm opacity-80">
                        {translate('dashboard.couldNotLoadActionStatus')} {errorDetails?.message || 'Unknown error'}
                    </p>
                    {errorDetails?.correlationId && (
                        <p className="text-xs opacity-70">
                            {translate('dashboard.correlationId')} <code className="rounded bg-white/50 px-1.5 py-0.5">{errorDetails.correlationId}</code>
                        </p>
                    )}
                    {onRetry && (
                        <Button
                            type="button"
                            variant="outline"
                            onClick={onRetry}
                            className="mt-2 inline-flex items-center gap-2 rounded-lg border border-white/30 bg-white/50 px-3 py-1.5 text-xs font-semibold transition-colors hover:bg-white/70 dark:border-black/30 dark:bg-black/20 dark:hover:bg-black/30"
                        >
                            <RefreshCw className="h-3 w-3" />
                            {translate('dashboard.retry')}
                        </Button>
                    )}
                </div>
            ) : actions.length === 0 ? (
                <p className="mt-4 text-sm opacity-80">{emptyText}</p>
            ) : (
                <div className="mt-4 space-y-3">
                    {actions.slice(0, 3).map((action) => (
                        <Button
                            key={action.id}
                            type="button"
                            variant="outline"
                            aria-label={tone === 'safe' ? undefined : 'Dashboard status action'}
                            onClick={() => onOpenProject(action)}
                            className="block w-full rounded-xl bg-white/75 p-3 text-left text-sm shadow-sm transition-transform hover:-translate-y-0.5 dark:bg-black/20"
                        >
                            <span className="block font-semibold">{action.title}</span>
                            <span className="mt-1 block opacity-80">{action.projectName} · {action.summary}</span>
                        </Button>
                    ))}
                </div>
            )}
        </div>
    );
}
