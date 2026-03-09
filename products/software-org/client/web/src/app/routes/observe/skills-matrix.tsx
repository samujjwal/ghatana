/**
 * Skills Matrix
 *
 * @doc.type route
 * @doc.section OBSERVE
 */

import { MainLayout } from '@/app/Layout';
import { SkillsMatrix, mockSkillsData } from '@/components/cross-functional/SkillsMatrix';

export default function SkillsMatrixRoute() {
    return (
        <MainLayout>
            <div className="p-6">
                <SkillsMatrix {...mockSkillsData} />
            </div>
        </MainLayout>
    );
}
