/**
 * MomentLink API routes for Flashit Web API
 * Manages temporal arcs and connections between Moments
 *
 * @doc.type route
 * @doc.purpose Moment linking for temporal arcs and connections
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma';
import { getUserIdFromRequest, JwtPayload } from '../lib/auth';

// Link types for temporal arcs
export const LINK_TYPES = [
  'related',      // General relationship
  'follows',      // Temporal sequence (this happened after that)
  'precedes',     // Reverse temporal sequence
  'references',   // Direct reference to another moment
  'causes',       // Causal relationship
  'similar',      // Similarity detected (often auto-generated)
  'contradicts',  // Opposing or conflicting content
  'elaborates',   // Expands on the linked moment
  'summarizes',   // Summary of linked moment(s)
] as const;

export type LinkType = typeof LINK_TYPES[number];

// Validation schemas
const createLinkSchema = z.object({
  targetMomentId: z.string().uuid(),
  linkType: z.enum(LINK_TYPES),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const updateLinkSchema = z.object({
  linkType: z.enum(LINK_TYPES).optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

const linkQuerySchema = z.object({
  direction: z.enum(['outgoing', 'incoming', 'both']).optional().default('both'),
  linkType: z.enum(LINK_TYPES).optional(),
  limit: z.coerce.number().int().min(1).max(100).optional().default(50),
});

const graphQuerySchema = z.object({
  depth: z.coerce.number().int().min(1).max(5).optional().default(2),
  linkTypes: z.string().optional(), // Comma-separated list of link types
  limit: z.coerce.number().int().min(1).max(200).optional().default(100),
});

// Type assertion for Fastify app with authenticate decorator
import type { FastifyRequest, FastifyReply } from 'fastify';
type AuthenticateFn = (request: FastifyRequest, reply: FastifyReply) => Promise<void>;
type AppWithAuth = FastifyInstance & { authenticate: AuthenticateFn };

export default async function momentLinkRoutes(app: FastifyInstance) {
  // Cast app to include authenticate decorator
  const authApp = app as AppWithAuth;

  /**
   * Create a link between moments
   * POST /api/moments/:id/links
   */
  app.post<{ Params: { id: string } }>('/:id/links', {
    onRequest: [authApp.authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { id: sourceMomentId } = request.params;
    const body = createLinkSchema.parse(request.body);
    const { targetMomentId, linkType } = body;

    try {
      // Prevent self-links
      if (sourceMomentId === targetMomentId) {
        return reply.status(400).send({
          error: 'Bad Request',
          message: 'Cannot create a link from a moment to itself',
        });
      }

      // Verify user has access to both moments
      const [sourceMoment, targetMoment] = await Promise.all([
        prisma.moment.findUnique({
          where: { id: sourceMomentId },
          include: {
            sphere: {
              include: {
                sphereAccess: {
                  where: { userId, revokedAt: null },
                },
              },
            },
          },
        }),
        prisma.moment.findUnique({
          where: { id: targetMomentId },
          include: {
            sphere: {
              include: {
                sphereAccess: {
                  where: { userId, revokedAt: null },
                },
              },
            },
          },
        }),
      ]);

      // Check source moment exists and is accessible
      if (!sourceMoment || sourceMoment.deletedAt) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Source moment not found',
        });
      }

      if (sourceMoment.sphere.sphereAccess.length === 0) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have access to the source moment',
        });
      }

      // Check target moment exists and is accessible
      if (!targetMoment || targetMoment.deletedAt) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Target moment not found',
        });
      }

      if (targetMoment.sphere.sphereAccess.length === 0) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have access to the target moment',
        });
      }

      // Check write permission on source moment's sphere
      const sourceAccess = sourceMoment.sphere.sphereAccess[0];
      if (!['OWNER', 'EDITOR'].includes(sourceAccess.role)) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have write access to create links from this moment',
        });
      }

      // Check if link already exists
      const existingLink = await prisma.momentLink.findFirst({
        where: {
          sourceMomentId,
          targetMomentId,
          linkType,
          deletedAt: null,
        },
      });

      if (existingLink) {
        return reply.status(409).send({
          error: 'Conflict',
          message: 'This link already exists',
        });
      }

      // Create the link
      const link = await prisma.momentLink.create({
        data: {
          sourceMomentId,
          targetMomentId,
          linkType,
          createdBy: userId,
        },
      });

      // Audit log
      await prisma.auditEvent.create({
        data: {
          eventType: 'MOMENT_LINK_CREATED',
          userId,
          momentId: sourceMomentId,
          actor: (request.user as JwtPayload).email,
          action: 'LINK_CREATED',
          resourceType: 'moment_link',
          resourceId: link.id,
          ipAddress: request.ip,
          userAgent: request.headers['user-agent'] || 'Unknown',
          details: {
            targetMomentId,
            linkType,
          },
        },
      });

      return reply.status(201).send({
        link: {
          id: link.id,
          sourceMomentId: link.sourceMomentId,
          targetMomentId: link.targetMomentId,
          linkType: link.linkType,
          createdAt: link.createdAt.toISOString(),
        },
      });

    } catch (error) {
      app.log.error({ err: error }, 'Failed to create moment link');
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to create link',
      });
    }
  });

  /**
   * Get links for a moment
   * GET /api/moments/:id/links
   */
  app.get<{ Params: { id: string }; Querystring: z.infer<typeof linkQuerySchema> }>('/:id/links', {
    onRequest: [authApp.authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { id: momentId } = request.params;
    const { direction, linkType, limit } = linkQuerySchema.parse(request.query);

    try {
      // Verify user has access to the moment
      const moment = await prisma.moment.findUnique({
        where: { id: momentId },
        include: {
          sphere: {
            include: {
              sphereAccess: {
                where: { userId, revokedAt: null },
              },
            },
          },
        },
      });

      if (!moment || moment.deletedAt) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Moment not found',
        });
      }

      if (moment.sphere.sphereAccess.length === 0) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have access to this moment',
        });
      }

      // Build query conditions
      const linkTypeCondition = linkType ? { linkType } : {};

      let outgoingLinks: Array<{
        id: string;
        linkType: string;
        createdAt: Date;
        targetMoment: { id: string; contentText: string; capturedAt: Date; emotions: string[]; tags: string[]; sphere: { id: string; name: string } };
        creator: { id: string; email: string; displayName: string | null };
      }> = [];
      let incomingLinks: Array<{
        id: string;
        linkType: string;
        createdAt: Date;
        sourceMoment: { id: string; contentText: string; capturedAt: Date; emotions: string[]; tags: string[]; sphere: { id: string; name: string } };
        creator: { id: string; email: string; displayName: string | null };
      }> = [];

      if (direction === 'outgoing' || direction === 'both') {
        outgoingLinks = await prisma.momentLink.findMany({
          where: {
            sourceMomentId: momentId,
            deletedAt: null,
            ...linkTypeCondition,
          },
          include: {
            targetMoment: {
              select: {
                id: true,
                contentText: true,
                capturedAt: true,
                emotions: true,
                tags: true,
                sphere: {
                  select: { id: true, name: true },
                },
              },
            },
            creator: {
              select: { id: true, email: true, displayName: true },
            },
          },
          take: limit,
          orderBy: { createdAt: 'desc' },
        });
      }

      if (direction === 'incoming' || direction === 'both') {
        incomingLinks = await prisma.momentLink.findMany({
          where: {
            targetMomentId: momentId,
            deletedAt: null,
            ...linkTypeCondition,
          },
          include: {
            sourceMoment: {
              select: {
                id: true,
                contentText: true,
                capturedAt: true,
                emotions: true,
                tags: true,
                sphere: {
                  select: { id: true, name: true },
                },
              },
            },
            creator: {
              select: { id: true, email: true, displayName: true },
            },
          },
          take: limit,
          orderBy: { createdAt: 'desc' },
        });
      }

      // Filter out links to moments the user doesn't have access to
      const userSphereIds = new Set(
        (await prisma.sphereAccess.findMany({
          where: { userId, revokedAt: null },
          select: { sphereId: true },
        })).map(a => a.sphereId)
      );

      const filteredOutgoing = outgoingLinks.filter(
        link => userSphereIds.has(link.targetMoment.sphere.id)
      );

      const filteredIncoming = incomingLinks.filter(
        link => userSphereIds.has(link.sourceMoment.sphere.id)
      );

      return {
        momentId,
        outgoing: filteredOutgoing.map(link => ({
          id: link.id,
          linkType: link.linkType,
          createdAt: link.createdAt.toISOString(),
          createdBy: link.creator,
          moment: {
            id: link.targetMoment.id,
            preview: link.targetMoment.contentText?.substring(0, 150),
            capturedAt: link.targetMoment.capturedAt?.toISOString(),
            emotions: link.targetMoment.emotions,
            tags: link.targetMoment.tags,
            sphere: link.targetMoment.sphere,
          },
        })),
        incoming: filteredIncoming.map(link => ({
          id: link.id,
          linkType: link.linkType,
          createdAt: link.createdAt.toISOString(),
          createdBy: link.creator,
          moment: {
            id: link.sourceMoment.id,
            preview: link.sourceMoment.contentText?.substring(0, 150),
            capturedAt: link.sourceMoment.capturedAt?.toISOString(),
            emotions: link.sourceMoment.emotions,
            tags: link.sourceMoment.tags,
            sphere: link.sourceMoment.sphere,
          },
        })),
        counts: {
          outgoing: filteredOutgoing.length,
          incoming: filteredIncoming.length,
          total: filteredOutgoing.length + filteredIncoming.length,
        },
      };

    } catch (error) {
      app.log.error({ err: error }, 'Failed to get moment links');
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get links',
      });
    }
  });

  /**
   * Delete a link
   * DELETE /api/moments/:id/links/:linkId
   */
  app.delete<{ Params: { id: string; linkId: string } }>('/:id/links/:linkId', {
    onRequest: [authApp.authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { id: momentId, linkId } = request.params;

    try {
      // Find the link
      const link = await prisma.momentLink.findUnique({
        where: { id: linkId },
        include: {
          sourceMoment: {
            include: {
              sphere: {
                include: {
                  sphereAccess: {
                    where: { userId, revokedAt: null },
                  },
                },
              },
            },
          },
        },
      });

      if (!link || link.deletedAt) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Link not found',
        });
      }

      // Verify the link belongs to the specified moment
      if (link.sourceMomentId !== momentId && link.targetMomentId !== momentId) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Link not found for this moment',
        });
      }

      // Check write permission (only link creator or sphere owner/editor can delete)
      const sphereAccess = link.sourceMoment.sphere.sphereAccess[0];
      const canDelete = link.createdBy === userId || 
        (sphereAccess && ['OWNER', 'EDITOR'].includes(sphereAccess.role));

      if (!canDelete) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have permission to delete this link',
        });
      }

      // Soft delete the link
      await prisma.momentLink.update({
        where: { id: linkId },
        data: { deletedAt: new Date() },
      });

      // Audit log
      await prisma.auditEvent.create({
        data: {
          eventType: 'MOMENT_LINK_DELETED',
          userId,
          momentId: link.sourceMomentId,
          actor: (request.user as JwtPayload).email,
          action: 'LINK_DELETED',
          resourceType: 'moment_link',
          resourceId: linkId,
          ipAddress: request.ip,
          userAgent: request.headers['user-agent'] || 'Unknown',
          details: {
            targetMomentId: link.targetMomentId,
            linkType: link.linkType,
          },
        },
      });

      return reply.status(204).send();

    } catch (error) {
      app.log.error({ err: error }, 'Failed to delete moment link');
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to delete link',
      });
    }
  });

  /**
   * Get link graph for visualization
   * GET /api/spheres/:id/link-graph
   */
  app.get<{ Params: { id: string }; Querystring: z.infer<typeof graphQuerySchema> }>('/spheres/:id/link-graph', {
    onRequest: [authApp.authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { id: sphereId } = request.params;
    const { linkTypes: linkTypesStr, limit } = graphQuerySchema.parse(request.query);

    try {
      // Verify user has access to the sphere
      const sphereAccess = await prisma.sphereAccess.findFirst({
        where: {
          sphereId,
          userId,
          revokedAt: null,
        },
        include: {
          sphere: {
            select: { id: true, name: true, deletedAt: true },
          },
        },
      });

      if (!sphereAccess || sphereAccess.sphere.deletedAt) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Sphere not found or access denied',
        });
      }

      // Parse link types filter
      const linkTypeFilter = linkTypesStr
        ? linkTypesStr.split(',').filter(t => LINK_TYPES.includes(t as LinkType))
        : undefined;

      // Get all moments in the sphere
      const moments = await prisma.moment.findMany({
        where: {
          sphereId,
          deletedAt: null,
        },
        select: {
          id: true,
          contentText: true,
          capturedAt: true,
          emotions: true,
          tags: true,
          importance: true,
        },
        take: limit,
        orderBy: { capturedAt: 'desc' },
      });

      const momentIds = moments.map(m => m.id);

      // Get all links between these moments
      const links = await prisma.momentLink.findMany({
        where: {
          OR: [
            { sourceMomentId: { in: momentIds } },
            { targetMomentId: { in: momentIds } },
          ],
          deletedAt: null,
          ...(linkTypeFilter ? { linkType: { in: linkTypeFilter } } : {}),
        },
        select: {
          id: true,
          sourceMomentId: true,
          targetMomentId: true,
          linkType: true,
          createdAt: true,
        },
      });

      // Filter links to only include those where both moments are in our set
      const momentIdSet = new Set(momentIds);
      const filteredLinks = links.filter(
        l => momentIdSet.has(l.sourceMomentId) && momentIdSet.has(l.targetMomentId)
      );

      // Build graph structure
      const nodes = moments.map(m => ({
        id: m.id,
        label: m.contentText?.substring(0, 50) || 'Untitled',
        capturedAt: m.capturedAt?.toISOString(),
        emotions: m.emotions,
        tags: m.tags,
        importance: m.importance,
        linkCount: filteredLinks.filter(
          l => l.sourceMomentId === m.id || l.targetMomentId === m.id
        ).length,
      }));

      const edges = filteredLinks.map(l => ({
        id: l.id,
        source: l.sourceMomentId,
        target: l.targetMomentId,
        type: l.linkType,
        createdAt: l.createdAt.toISOString(),
      }));

      // Calculate graph statistics
      const stats = {
        nodeCount: nodes.length,
        edgeCount: edges.length,
        linkTypes: [...new Set(edges.map(e => e.type))],
        avgLinksPerNode: nodes.length > 0 
          ? (edges.length * 2 / nodes.length).toFixed(2) 
          : '0',
        isolatedNodes: nodes.filter(n => n.linkCount === 0).length,
      };

      return {
        sphere: {
          id: sphereAccess.sphere.id,
          name: sphereAccess.sphere.name,
        },
        graph: {
          nodes,
          edges,
        },
        stats,
      };

    } catch (error) {
      app.log.error({ err: error }, 'Failed to get link graph');
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get link graph',
      });
    }
  });
}

// Export for registration
export { momentLinkRoutes };
