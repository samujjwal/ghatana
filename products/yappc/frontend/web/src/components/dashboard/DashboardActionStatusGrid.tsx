import type { ProjectDashboardAction } from '../../lib/api';
import { DashboardActionStatusCard } from './DashboardActionStatusCard';

export interface DashboardActionStatusGridProps {
    readonly blockedWork: readonly ProjectDashboardAction[];
    readonly reviewRequired: readonly ProjectDashboardAction[];
    readonly safeToContinue: readonly ProjectDashboardAction[];
    readonly loading: boolean;
    readonly error: unknown;
    readonly onOpenProject: (action: ProjectDashboardAction) => void;
    readonly onRetry?: () => void;
}

export function DashboardActionStatusGrid(props: DashboardActionStatusGridProps) {
    const {
        blockedWork,
        reviewRequired,
        safeToContinue,
        loading,
        error,
        onOpenProject,
        onRetry,
    } = props;

    return (
        <section className="grid gap-4 md:grid-cols-3" aria-label="Backed dashboard action status">
            <DashboardActionStatusCard
                titleKey="dashboard.blockedWork"
                tone="warning"
                actions={blockedWork}
                loading={loading}
                error={error}
                emptyTextKey="dashboard.noBlockers"
                onOpenProject={onOpenProject}
                onRetry={onRetry}
            />
            <DashboardActionStatusCard
                titleKey="dashboard.reviewRequired"
                tone="review"
                actions={reviewRequired}
                loading={loading}
                error={error}
                emptyTextKey="dashboard.noReviews"
                onOpenProject={onOpenProject}
                onRetry={onRetry}
            />
            <DashboardActionStatusCard
                titleKey="dashboard.safeToContinue"
                tone="safe"
                actions={safeToContinue}
                loading={loading}
                error={error}
                emptyTextKey="dashboard.noSafeContinuations"
                onOpenProject={onOpenProject}
                onRetry={onRetry}
            />
        </section>
    );
}
