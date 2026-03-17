import { useEffect } from 'react';
import type { WorkItemSummary, WorkItemStatus, WorkItemType } from '@/types/workItem';
import { useItems, usePhases, mockPhases } from '@ghatana/yappc-store/devsecops';
import type { Item as DevSecOpsStoreItem, PhaseKey } from '@ghatana/yappc-types/devsecops';
import { mapWorkItemStatusToDevSecOpsStatus, mapWorkItemPriorityToDevSecOpsPriority, inferDevSecOpsPhaseIdFromWorkItemStatus } from './mapWorkItemToDevSecOpsItem';
import type { DevSecOpsPhaseId } from '@/config/devsecopsEngineerFlow';

function mapPhaseToPhaseKey(phaseId: DevSecOpsPhaseId): PhaseKey {
    switch (phaseId) {
        case 'intake':
            return 'ideation';
        case 'plan':
            return 'planning';
        case 'build':
        case 'verify':
        case 'review':
            return 'development';
        case 'staging':
        case 'deploy':
            return 'deployment';
        case 'operate':
        case 'learn':
            return 'operations';
        default:
            return 'planning';
    }
}

function mapStatusToPhaseKey(status: WorkItemStatus): PhaseKey {
    const phaseId = inferDevSecOpsPhaseIdFromWorkItemStatus(status);
    return mapPhaseToPhaseKey(phaseId);
}

function mapTypeToItemType(type: WorkItemType): DevSecOpsStoreItem['type'] {
    switch (type) {
        case 'story':
            return 'story';
        case 'epic':
            return 'epic';
        case 'bug':
            return 'bug';
        case 'task':
            return 'task';
        case 'spike':
        default:
            return 'task';
    }
}

function mapSummaryToStoreItem(summary: WorkItemSummary): DevSecOpsStoreItem {
    const status = mapWorkItemStatusToDevSecOpsStatus(summary.status);
    const phaseKey = mapStatusToPhaseKey(summary.status);
    const phase = mockPhases.find((p: { key: PhaseKey; id: string }) => p.key === phaseKey);
    const phaseId = phase?.id ?? phaseKey;

    return {
        id: summary.id,
        title: summary.title,
        description: undefined,
        type: mapTypeToItemType(summary.type),
        priority: mapWorkItemPriorityToDevSecOpsPriority(summary.priority),
        status,
        phaseId,
        owners: [
            {
                id: summary.assignee.id,
                name: summary.assignee.name,
                email: '',
                role: 'Developer',
            },
        ],
        tags: summary.service ? [summary.service] : [],
        createdAt: summary.updatedAt,
        updatedAt: summary.updatedAt,
        progress: status === 'completed' ? 100 : status === 'not-started' ? 0 : 50,
        artifacts: [],
    };
}

export function useDevSecOpsStoreFromWorkItems(workItems: WorkItemSummary[] | undefined) {
    const [currentItems, setItems] = useItems();
    const [phases, setPhases] = usePhases();

    useEffect(() => {
        if (!phases || phases.length === 0) {
            setPhases(mockPhases);
        }
    }, [phases, setPhases]);

    useEffect(() => {
        if (!workItems || workItems.length === 0) {
            if (!currentItems || currentItems.length === 0) {
                return;
            }
            setItems([]);
            return;
        }

        const nextItems = workItems.map(mapSummaryToStoreItem);

        if (
            currentItems.length === nextItems.length &&
            currentItems.every((item, index) => {
                const next = nextItems[index];
                return (
                    item.id === next.id &&
                    item.status === next.status &&
                    item.priority === next.priority &&
                    item.phaseId === next.phaseId
                );
            })
        ) {
            return;
        }

        setItems(nextItems);
    }, [workItems, setItems, currentItems]);
}
