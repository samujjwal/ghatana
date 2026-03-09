/**
 * DevSecOps Items API Routes
 *
 * Provides consolidated view of DevSecOps work items by stage.
 * Joins WorkItems, Incidents, Queue items, and Workflows to provide
 * a unified DevSecOpsItem[] response for stage dashboards.
 *
 * @doc.type api
 * @doc.purpose DevSecOps items aggregation
 */

import type { FastifyPluginAsync } from 'fastify';
import { prisma } from '../db/client.js';

// Type definitions (matching frontend DevSecOpsItem interface)
interface DevSecOpsItem {
    id: string;
    title: string;
    description?: string;
    status: 'not-started' | 'in-progress' | 'in-review' | 'blocked' | 'completed';
    priority: 'low' | 'medium' | 'high' | 'critical';
    phaseId: string;
    labels?: string[];
    tenantId?: string;
    relatedIncidentId?: string;
    relatedQueueItemId?: string;
}

// Helper function to map work item to DevSecOps item
function mapWorkItemToDevSecOpsItem(workItem: any): DevSecOpsItem {
    return {
        id: workItem.id,
        title: workItem.title,
        description: workItem.description || undefined,
        status: mapWorkItemStatus(workItem.status),
        priority: mapWorkItemPriority(workItem.priority),
        phaseId: workItem.phase || 'build',
        labels: workItem.tags ? workItem.tags.split(',') : [],
        tenantId: workItem.tenantId || undefined,
    };
}

function mapWorkItemStatus(status: string): DevSecOpsItem['status'] {
    const statusMap: Record<string, DevSecOpsItem['status']> = {
        'TODO': 'not-started',
        'IN_PROGRESS': 'in-progress',
        'IN_REVIEW': 'in-review',
        'BLOCKED': 'blocked',
        'DONE': 'completed',
    };
    return statusMap[status] || 'not-started';
}

function mapWorkItemPriority(priority: string): DevSecOpsItem['priority'] {
    const priorityMap: Record<string, DevSecOpsItem['priority']> = {
        'LOW': 'low',
        'MEDIUM': 'medium',
        'HIGH': 'high',
        'CRITICAL': 'critical',
    };
    return priorityMap[priority] || 'medium';
}

const devsecopsRoutes: FastifyPluginAsync = async (fastify) => {
    /**
     * GET /api/v1/devsecops/items
     * 
     * Query parameters:
     * - stage: Filter by stage (e.g., 'build', 'test', 'secure')
     * - tenantId: Filter by tenant
     * - status: Filter by status
     * - priority: Filter by priority
     */
    fastify.get('/devsecops/items', async (request, reply) => {
        try {
            const { stage, tenantId, status, priority } = request.query as {
                stage?: string;
                tenantId?: string;
                status?: string;
                priority?: string;
            };

            // Build where clause for filtering
            const where: any = {};
            
            if (tenantId) {
                where.tenantId = tenantId;
            }
            
            if (status) {
                where.status = status.toUpperCase();
            }
            
            if (priority) {
                where.priority = priority.toUpperCase();
            }

            // If stage filter is provided, map to phase
            // This is a simplified mapping - in production you'd use the stage_phase_mapping.yaml
            if (stage) {
                const stagePhaseMap: Record<string, string[]> = {
                    'plan': ['intake', 'plan'],
                    'design': ['plan'],
                    'develop': ['build'],
                    'build': ['build'],
                    'test': ['verify'],
                    'secure': ['verify', 'review'],
                    'compliance': ['review'],
                    'staging': ['staging'],
                    'deploy': ['deploy'],
                    'operate': ['operate'],
                    'monitor': ['operate'],
                };
                
                const phases = stagePhaseMap[stage];
                if (phases) {
                    where.phase = { in: phases };
                }
            }

            // Fetch work items (primary source)
            // Note: WorkItem table doesn't exist yet in the schema,
            // so this is a placeholder. In reality, you'd fetch from your WorkItem model.
            // For now, return empty array or mock data
            
            const items: DevSecOpsItem[] = [];

            // TODO: Implement actual data fetching when WorkItem model is available
            // const workItems = await prisma.workItem.findMany({ where });
            // items.push(...workItems.map(mapWorkItemToDevSecOpsItem));

            // TODO: Fetch and map related incidents
            // const incidents = await prisma.incident.findMany({ where: { status: 'OPEN', ...where } });
            // items.push(...incidents.map(mapIncidentToDevSecOpsItem));

            // TODO: Fetch and map queue items
            // const queueItems = await prisma.queueItem.findMany({ where: { status: 'PENDING', ...where } });
            // items.push(...queueItems.map(mapQueueItemToDevSecOpsItem));

            return {
                success: true,
                data: items,
                meta: {
                    total: items.length,
                    filters: { stage, tenantId, status, priority },
                },
            };
        } catch (error) {
            fastify.log.error(error, '[DevSecOps API] Error fetching items');
            return reply.code(500).send({
                success: false,
                error: 'Failed to fetch DevSecOps items',
            });
        }
    });

    /**
     * GET /api/v1/devsecops/stage-health/:stageKey
     * 
     * Get aggregated health metrics for a specific stage
     */
    fastify.get('/devsecops/stage-health/:stageKey', async (request, reply) => {
        try {
            const { stageKey } = request.params as { stageKey: string };
            const { tenantId } = request.query as { tenantId?: string };

            // TODO: Implement actual health calculation
            // For now, return mock structure
            const health = {
                stage: stageKey,
                status: 'on-track' as const,
                itemsTotal: 0,
                itemsCompleted: 0,
                itemsBlocked: 0,
                itemsInProgress: 0,
                criticalIssues: 0,
                lastUpdated: new Date().toISOString(),
            };

            return {
                success: true,
                data: health,
            };
        } catch (error) {
            fastify.log.error(error, '[DevSecOps API] Error fetching stage health');
            return reply.code(500).send({
                success: false,
                error: 'Failed to fetch stage health',
            });
        }
    });
};

export default devsecopsRoutes;
