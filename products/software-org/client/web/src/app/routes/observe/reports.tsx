/**
 * Reports - Generated Reports and Analytics
 *
 * View and generate reports.
 *
 * @doc.type route
 * @doc.section OBSERVE
 */

import { MainLayout } from '@/app/Layout';
import { ReportsExplorer } from '@/features/observe/ReportsExplorer';

export default function ReportsPage() {
    return (
        <MainLayout>
            <ReportsExplorer />
        </MainLayout>
    );
}
