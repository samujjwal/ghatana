/**
 * IC - Time Off
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import { MainLayout } from '@/app/Layout';
import { TimeOffRequestForm } from '@/components/time-off/TimeOffRequestForm';

export default function IcTimeOffRoute() {
    return (
        <MainLayout>
            <div className="p-6 max-w-3xl mx-auto">
                <TimeOffRequestForm />
            </div>
        </MainLayout>
    );
}
