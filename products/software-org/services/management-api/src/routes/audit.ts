/**
 * Audit API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for audit trail and decision recording
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - POST /api/v1/audit/decisions - Record a decision
 * - GET /api/v1/audit/trails/:entityType/:entityId - Get audit trail for an entity
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { prisma } from '../db/client.js';

/** Decision record request */
interface RecordDecisionBody {
    entityType: string;
    entityId: string;
    decision: string;
    reason?: string;
    userId?: string;
}

/** Audit trail record */
interface AuditTrailRecord {
    id: string;
    entityType: string;
    entityId: string;
    decision: string | null;
    reason: string | null;
    userId: string | null;
    userName: string | null;
    timestamp: string;
}

/** Audit trail response */
interface AuditTrailResponse {
    total: number;
    records: AuditTrailRecord[];
    avgApprovalRate: number;
    avgDeferRate: number;
    avgRejectionRate: number;
}

/**
 * Register audit routes
 */
export default async function auditRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * POST /api/v1/audit/decisions
     * Record a decision in the audit trail
     */
    fastify.post<{ Body: RecordDecisionBody }>(
        '/decisions',
        async (request: FastifyRequest<{ Body: RecordDecisionBody }>, reply: FastifyReply) => {
            const { entityType, entityId, decision, reason, userId } = request.body;

            fastify.log.debug({ entityType, entityId, decision }, 'Recording decision');

            if (!entityType || !entityId || !decision) {
                return reply.status(400).send({
                    error: 'entityType, entityId, and decision are required',
                });
            }

            const auditEvent = await prisma.auditEvent.create({
                data: {
                    entityType,
                    entityId,
                    action: 'decision',
                    decision,
                    reason,
                    actorUserId: userId,
                    timestamp: new Date(),
                },
            });

            return reply.status(201).send({
                id: auditEvent.id,
                success: true,
                message: 'Decision recorded successfully',
            });
        }
    );

    /**
     * GET /api/v1/audit/trails/:entityType/:entityId
     * Get audit trail for an entity
     */
    fastify.get<{ Params: { entityType: string; entityId: string } }>(
        '/trails/:entityType/:entityId',
        async (
            request: FastifyRequest<{ Params: { entityType: string; entityId: string } }>,
            reply: FastifyReply
        ) => {
            const { entityType, entityId } = request.params;

            fastify.log.debug({ entityType, entityId }, 'Fetching audit trail');

            const events = await prisma.auditEvent.findMany({
                where: { entityType, entityId },
                include: { actor: true },
                orderBy: { timestamp: 'desc' },
            });

            // Calculate decision rates
            const decisions = events.filter((e) => e.decision);
            const approvals = decisions.filter((e) => e.decision === 'approve').length;
            const defers = decisions.filter((e) => e.decision === 'defer').length;
            const rejections = decisions.filter((e) => e.decision === 'reject').length;
            const total = decisions.length || 1; // Avoid division by zero

            const response: AuditTrailResponse = {
                total: events.length,
                records: events.map((e) => ({
                    id: e.id,
                    entityType: e.entityType,
                    entityId: e.entityId,
                    decision: e.decision,
                    reason: e.reason,
                    userId: e.actorUserId,
                    userName: e.actor?.name ?? null,
                    timestamp: e.timestamp.toISOString(),
                })),
                avgApprovalRate: Math.round((approvals / total) * 100),
                avgDeferRate: Math.round((defers / total) * 100),
                avgRejectionRate: Math.round((rejections / total) * 100),
            };

            return reply.send(response);
        }
    );
}
