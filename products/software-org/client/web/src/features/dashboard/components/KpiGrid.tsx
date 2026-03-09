import React from 'react';
import { useOrgKpis } from '../hooks/useOrgKpis';
import { KpiCard } from '@/components/ui/components';
import type { OrgKpiItem, OrgKpis } from '../hooks/useOrgKpis';

/**
 * KPI Grid - Displays 6 key performance indicators.
 *
 * <p><b>Features</b><br>
 * - Deployments per week with trend
 * - Lead time to production
 * - Mean time to recovery (MTTR)
 * - Change failure rate (CFR)
 * - Security issues count
 * - Cost savings tracking
 *
 * @doc.type component
 * @doc.purpose Grid layout for KPI cards
 * @doc.layer product
 * @doc.pattern Organism
 */
export const KpiGrid = React.memo(() => {
    const { data: kpis, isLoading } = useOrgKpis();

    const deployments = React.useMemo(() => {
        if (!kpis) return 0;

        if (Array.isArray(kpis)) {
            const item = (kpis as OrgKpiItem[]).find((k) => k.title === 'Deployments');
            return Number(item?.value ?? 0);
        }

        const record = kpis as OrgKpis;
        const value = (record as Record<string, unknown>).deployments;
        return typeof value === 'number' ? value : Number(value ?? 0);
    }, [kpis]);

    if (isLoading) {
        return (
            <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
                {Array.from({ length: 6 }).map((_, i) => (
                    <div key={i} className="h-24 bg-slate-200 rounded animate-pulse dark:bg-neutral-700" />
                ))}
            </div>
        );
    }

    if (!kpis) {
        return (
            <div className="text-center py-8 text-slate-600 dark:text-neutral-400">
                No KPI data available
            </div>
        );
    }

    return (
        <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
            <KpiCard
                title="Deployments"
                value={deployments}
                subtitle="per week"
                trend="up"
                trendValue="+23%"
            />
            <KpiCard
                title="Lead Time"
                value={3.2}
                subtitle="hours"
                trend="down"
                trendValue="-45%"
            />
            <KpiCard
                title="MTTR"
                value={12}
                subtitle="minutes"
                trend="down"
                trendValue="-67%"
            />
            <KpiCard
                title="Change Failure"
                value={3.2}
                subtitle="%"
                trend="down"
                trendValue="-12%"
            />
            <KpiCard
                title="Security"
                value={0}
                subtitle="critical"
                trend="neutral"
            />
            <KpiCard
                title="Cost Savings"
                value={2.4}
                subtitle="k$/mo"
                trend="up"
                trendValue="+30%"
            />
        </div>
    );
});

KpiGrid.displayName = 'KpiGrid';

export default KpiGrid;
