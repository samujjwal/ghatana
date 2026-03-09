/**
 * Approvals
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import { MainLayout } from '@/app/Layout';
import { ApprovalDashboard } from '@/components/approvals/ApprovalDashboard';

export default function ApprovalsRoute() {
    return (
        <MainLayout>
            <div className="p-6">
                <ApprovalDashboard />
            </div>
        </MainLayout>
    );
}
