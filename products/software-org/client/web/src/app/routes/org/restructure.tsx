/**
 * Organization Restructuring
 *
 * @doc.type route
 * @doc.section ADMIN
 */

import { MainLayout } from '@/app/Layout';
import { RestructurePage } from '@/app/pages/org/RestructurePage';

export default function OrgRestructureRoute() {
    return (
        <MainLayout>
            <RestructurePage />
        </MainLayout>
    );
}
