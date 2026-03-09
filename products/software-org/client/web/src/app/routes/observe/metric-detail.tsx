/**
 * Metric Detail - View detailed metric information
 *
 * @doc.type route
 * @doc.section OBSERVE
 */

import { MainLayout } from '@/app/Layout';
import { MetricDetail } from '@/features/observe/MetricDetail';

export default function MetricDetailPage() {
    return (
        <MainLayout>
            <MetricDetail />
        </MainLayout>
    );
}
