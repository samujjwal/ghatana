/**
 * Incidents - Active Incident Management
 *
 * View and manage active incidents, assign responders,
 * and track resolution progress.
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import type { Route } from './+types/incidents';
import { MainLayout } from '@/app/Layout';
import { IncidentsExplorer } from '@/features/operate/IncidentsExplorer';

export function meta({}: Route.MetaArgs) {
    return [
        { title: 'Incidents - Operate' },
        { name: 'description', content: 'Incident management and response' },
    ];
}

export default function Incidents() {
    return (
        <MainLayout>
            <IncidentsExplorer />
        </MainLayout>
    );
}
