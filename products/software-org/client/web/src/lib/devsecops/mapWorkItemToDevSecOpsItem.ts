/**
 * DevSecOps ↔ WorkItem mapping utilities.
 *
 * Bridges the Software Org `WorkItem` domain model with the DevSecOps
 * lifecycle concepts used for cross-app dashboards and flows.
 *
 * This module is intentionally small and focused: it does not own the
 * DevSecOps model itself (that lives in Yappc), but provides a thin
 * adapter so Software Org can participate in DevSecOps views.
 *
 * @doc.type utils
 * @doc.purpose Map Software Org WorkItem domain to DevSecOps lifecycle concepts
 * @doc.layer product
 */

import type {
    WorkItem,
    WorkItemPriority,
    WorkItemStatus,
} from '@/types/workItem';
import type { DevSecOpsPhaseId } from '@/config/devsecopsEngineerFlow';
import type { ItemStatus as DevSecOpsCoreItemStatus, Priority as DevSecOpsCorePriority } from '@ghatana/yappc-types/devsecops';

/**
 * Status values used by the DevSecOps item model.
 *
 * These are aligned with the DevSecOps store in Yappc (`ItemStatus`).
 */
export type DevSecOpsItemStatus = DevSecOpsCoreItemStatus;

/**
 * Priority values used by the DevSecOps item model.
 *
 * This mirrors common DevSecOps priority conventions and is derived
 * from Software Org `WorkItemPriority`.
 */
export type DevSecOpsItemPriority = DevSecOpsCorePriority;

/**
 * Minimal DevSecOps item projection derived from a Software Org `WorkItem`.
 */
export interface DevSecOpsItem {
    id: string;
    title: string;
    description?: string;
    status: DevSecOpsItemStatus;
    priority: DevSecOpsItemPriority;
    phaseId: DevSecOpsPhaseId;
    labels?: string[];
    // Optional metadata for linking to seeded entities
    tenantId?: string;
    relatedIncidentId?: string;
    relatedQueueItemId?: string;
}

/**
 * Map Software Org `WorkItemStatus` to DevSecOps `ItemStatus`.
 */
export function mapWorkItemStatusToDevSecOpsStatus(
    status: WorkItemStatus,
): DevSecOpsItemStatus {
    switch (status) {
        case 'backlog':
        case 'ready':
            return 'not-started';
        case 'in-progress':
            return 'in-progress';
        case 'in-review':
            return 'in-review';
        case 'staging':
            // Still in motion; treat as in-progress from a lifecycle POV.
            return 'in-progress';
        case 'deployed':
        case 'done':
            return 'completed';
        case 'blocked':
            return 'blocked';
        default:
            return 'not-started';
    }
}

/**
 * Map Software Org `WorkItemPriority` (p0–p3) to DevSecOps priority buckets.
 */
export function mapWorkItemPriorityToDevSecOpsPriority(
    priority: WorkItemPriority,
): DevSecOpsItemPriority {
    switch (priority) {
        case 'p0':
            return 'critical';
        case 'p1':
            return 'high';
        case 'p2':
            return 'medium';
        case 'p3':
        default:
            return 'low';
    }
}

/**
 * Infer the DevSecOps lifecycle phase from a `WorkItemStatus`.
 *
 * This is a heuristic mapping aligned with the engineer flow:
 * - backlog           → intake
 * - ready             → plan
 * - in-progress       → build
 * - in-review         → review
 * - staging           → staging
 * - deployed          → deploy
 * - done              → operate
 * - blocked           → build (blocked during active work)
 */
export function inferDevSecOpsPhaseIdFromWorkItemStatus(
    status: WorkItemStatus,
): DevSecOpsPhaseId {
    switch (status) {
        case 'backlog':
            return 'intake';
        case 'ready':
            return 'plan';
        case 'in-progress':
        case 'blocked':
            return 'build';
        case 'in-review':
            return 'review';
        case 'staging':
            return 'staging';
        case 'deployed':
            return 'deploy';
        case 'done':
            return 'operate';
        default:
            return 'intake';
    }
}

/**
 * Project a Software Org `WorkItem` into the minimal DevSecOps item shape.
 */
export function mapWorkItemToDevSecOpsItem(workItem: WorkItem): DevSecOpsItem {
    return {
        id: workItem.id,
        title: workItem.title,
        description: workItem.description,
        status: mapWorkItemStatusToDevSecOpsStatus(workItem.status),
        phaseId: inferDevSecOpsPhaseIdFromWorkItemStatus(workItem.status),
        priority: mapWorkItemPriorityToDevSecOpsPriority(workItem.priority),
        labels: workItem.labels,
    };
}
