/**
 * ML Model Detail - View Model Performance
 *
 * Display detailed ML model metrics and drift analysis.
 *
 * @doc.type route
 * @doc.section OBSERVE
 */

import { MainLayout } from '@/app/Layout';
import { MLModelDetail } from '@/features/observe/MLModelDetail';

export default function MLModelDetailPage() {
    return (
        <MainLayout>
            <MLModelDetail />
        </MainLayout>
    );
}
