/**
 * Queue Item Detail - Work Item Detail View
 *
 * Detailed view of a work queue item with context
 * and approval/rejection actions.
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import type { Route } from './+types/queue-item-detail';
import { MainLayout } from '@/app/Layout';
import { QueueItemDetail } from '@/features/operate/QueueItemDetail';

export function meta({}: Route.MetaArgs) {
    return [
        { title: 'Queue Item Detail - Operate' },
        { name: 'description', content: 'Work item details and approval actions' },
    ];
}

export default function QueueItemDetailPage() {
    return (
        <MainLayout>
            <QueueItemDetail />
        </MainLayout>
    );
}
