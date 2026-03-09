/**
 * Metrics - KPIs and Performance Data
 *
 * View and analyze key performance indicators.
 *
 * @doc.type route
 * @doc.section OBSERVE
 */

import type { Route } from './+types/metrics';
import { MainLayout } from '@/app/Layout';
import { MetricsExplorer } from '@/features/observe/MetricsExplorer';

export function meta({}: Route.MetaArgs) {
    return [
        { title: 'Metrics - Observe' },
        { name: 'description', content: 'Key performance indicators and trends' },
    ];
}

export default function MetricsPage() {
    return (
        <MainLayout>
            <MetricsExplorer />
        </MainLayout>
    );
}
