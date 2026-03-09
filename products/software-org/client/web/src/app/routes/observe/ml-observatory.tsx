/**
 * ML Observatory - Model Performance Monitoring
 *
 * Monitor ML model performance, drift, and health.
 *
 * @doc.type route
 * @doc.section OBSERVE
 */

import type { Route } from './+types/ml-observatory';
import { MainLayout } from '@/app/Layout';
import { MLModelsExplorer } from '@/features/observe/MLModelsExplorer';

export function meta({}: Route.MetaArgs) {
    return [
        { title: 'ML Observatory - Observe' },
        { name: 'description', content: 'Monitor model performance and health' },
    ];
}

export default function MLObservatoryPage() {
    return (
        <MainLayout>
            <MLModelsExplorer />
        </MainLayout>
    );
}
