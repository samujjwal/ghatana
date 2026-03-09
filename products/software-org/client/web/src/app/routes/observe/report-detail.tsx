/**
 * Report Detail - View Generated Report
 *
 * Display detailed report with metrics and visualizations.
 *
 * @doc.type route
 * @doc.section OBSERVE
 */

import { MainLayout } from '@/app/Layout';
import { ReportViewer } from '@/features/observe/ReportViewer';

export default function ReportDetailPage() {
    return (
        <MainLayout>
            <ReportViewer />
        </MainLayout>
    );
}
