/**
 * Incident Detail - Incident Detail View
 *
 * Detailed view of a single incident with timeline,
 * affected services, and action buttons.
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import type { Route } from './+types/incident-detail';
import { MainLayout } from '@/app/Layout';
import { IncidentDetail } from '@/features/operate/IncidentDetail';

export function meta({ }: Route.MetaArgs) {
    return [
        { title: 'Incident Detail - Operate' },
        { name: 'description', content: 'Incident details and management actions' },
    ];
}

export default function IncidentDetailPage() {
    return (
        <MainLayout>
            <IncidentDetail />
        </MainLayout>
    );
}
