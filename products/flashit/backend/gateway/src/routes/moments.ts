/**
 * Moments API routes for creating, retrieving, searching, and managing moments
 * 
 * @description Provides endpoints for moment CRUD operations, search functionality,
 * and filtering. Moments are the core data entities representing captured personal
 * context with metadata, emotions, tags, and media attachments.
 * 
 * @example
 * ```typescript
 * // Create a new moment
 * POST /api/moments
 * {
 *   "content": "Had a great meeting with the team",
 *   "sphereId": "uuid",
 *   "emotions": ["happy", "excited"],
 *   "tags": ["work", "team"],
 *   "importance": 4
 * }
 * 
 * // Search moments
 * GET /api/moments?search=meeting&tags=work&startDate=2025-01-01
 * ```
 */

import { FastifyInstance } from "fastify";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { JwtPayload } from "../lib/auth";
import { Logger } from "../lib/logger";
import { getClassificationService } from "../services/java-agents/classification-service.js";
import { VectorEmbeddingService } from "../services/embeddings/vector-service.js";

const createMomentSchema = z.object({
  sphereId: z.string().uuid().optional(),
  content: z.object({
    text: z.string().min(1),
    transcript: z.string().optional(),
    type: z.enum(["TEXT", "VOICE", "VIDEO", "IMAGE", "MIXED"]),
  }),
  signals: z.object({
    emotions: z.array(z.string()).optional(),
    tags: z.array(z.string()).optional(),
    intent: z.string().optional(),
    sentimentScore: z.number().min(-1).max(1).optional(),
    importance: z.number().int().min(1).max(5).optional(),
    entities: z.array(z.string()).optional(),
  }).optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
  capturedAt: z.string().datetime().optional(),
});

const classifySphereSchema = z.object({
  content: z.object({
    text: z.string().min(1),
    transcript: z.string().optional(),
    type: z.enum(["TEXT", "VOICE", "VIDEO", "IMAGE", "MIXED"]),
  }),
  signals: z.object({
    emotions: z.array(z.string()).optional(),
    tags: z.array(z.string()).optional(),
    intent: z.string().optional(),
    sentimentScore: z.number().min(-1).max(1).optional(),
    importance: z.number().int().min(1).max(5).optional(),
    entities: z.array(z.string()).optional(),
  }).optional(),
});

const getUserIdFromRequest = (request: any) => {
  const jwtUser = request.user as JwtPayload;
  return jwtUser.userId;
};

const searchMomentsSchema = z.object({
  sphereIds: z.union([z.string().uuid(), z.array(z.string().uuid())]).optional().transform(val => Array.isArray(val) ? val : val ? [val] : undefined),
  query: z.string().optional(),
  tags: z.union([z.string(), z.array(z.string())]).optional().transform(val => Array.isArray(val) ? val : val ? [val] : undefined),
  emotions: z.union([z.string(), z.array(z.string())]).optional().transform(val => Array.isArray(val) ? val : val ? [val] : undefined),
  startDate: z.string().datetime().optional(),
  endDate: z.string().datetime().optional(),
  limit: z.coerce.number().int().min(1).max(100).default(20),
  cursor: z.string().optional(),
});

export const registerMomentRoutes = async (app: FastifyInstance) => {
  /**
   * POST /api/moments
   * Create a new Moment
   */
  app.post("/api/moments", {
    onRequest: [(app as any).authenticate],
  }, async (request, reply) => {
    const logger = Logger.fromRequest(request);
    const userId = getUserIdFromRequest(request);
    const body = createMomentSchema.parse(request.body);

    let sphereId = body.sphereId;

    // If sphere is not provided, use AI classification to select one
    if (!sphereId) {
      try {
        const classificationService = getClassificationService();
        const classification = await classificationService.classifyMoment({
          content: body.content.text,
          transcript: body.content.transcript,
          contentType: body.content.type as 'TEXT' | 'VOICE' | 'IMAGE' | 'VIDEO',
          emotions: body.signals?.emotions || [],
          tags: body.signals?.tags || [],
          userIntent: body.signals?.intent,
          userId,
        });

        sphereId = classification.sphereId;
        
        // Log classification source for monitoring
        logger.info('Moment classified', {
          sphereId: classification.sphereId,
          sphereName: classification.sphereName,
          confidence: classification.confidence,
          source: classification.source,
        });
      } catch (error) {
        logger.error('Automatic sphere classification failed', error);
        return reply.code(500).send({
          error: "Classification failed",
          message: "Failed to automatically classify moment to a sphere",
        });
      }
    }

    // Verify user has access to the Sphere
    const access = await prisma.sphereAccess.findFirst({
      where: {
        sphereId,
        userId,
        revokedAt: null,
      },
      include: {
        sphere: {
          select: {
            deletedAt: true,
          },
        },
      },
    });

    if (!access || access.sphere.deletedAt) {
      return reply.code(403).send({
        error: "Access denied",
        message: "You do not have access to this Sphere",
      });
    }

    // Check write permissions
    if (!["OWNER", "EDITOR"].includes(access.role)) {
      return reply.code(403).send({
        error: "Access denied",
        message: "You do not have write access to this Sphere",
      });
    }

    // Create Moment
    const moment = await prisma.moment.create({
      data: {
        userId,
        sphereId,
        contentText: body.content.text,
        contentTranscript: body.content.transcript,
        contentType: body.content.type,
        emotions: body.signals?.emotions ?? [],
        tags: body.signals?.tags ?? [],
        intent: body.signals?.intent,
        sentimentScore: body.signals?.sentimentScore,
        importance: body.signals?.importance,
        entities: body.signals?.entities ?? [],
        capturedAt: body.capturedAt ? new Date(body.capturedAt) : new Date(),
        metadata: body.metadata as any,
      },
      include: {
        sphere: {
          select: {
            id: true,
            name: true,
            type: true,
          },
        },
      },
    });

    // Audit log
    await prisma.auditEvent.create({
      data: {
        eventType: "MOMENT_CREATED",
        userId,
        momentId: moment.id,
        sphereId: body.sphereId,
        actor: (request.user as JwtPayload).email,
        action: "CREATE",
        resourceType: "MOMENT",
        resourceId: moment.id,
        ipAddress: request.ip,
        userAgent: request.headers["user-agent"],
      },
    });

    // Log business event
    logger.logBusinessEvent('MOMENT_CREATED', {
      momentId: moment.id,
      sphereId: moment.sphereId,
      sphereName: moment.sphere.name,
      contentType: moment.contentType,
      hasTranscript: !!moment.contentTranscript,
      emotionsCount: moment.emotions.length,
      tagsCount: moment.tags.length,
    });

    // Trigger vector embedding for semantic search
    try {
      await VectorEmbeddingService.enqueueEmbedding({
        momentId: moment.id,
        embeddingModelId: 'openai-3-small',
        contentType: body.content.transcript ? 'combined' : 'text',
        inputText: body.content.transcript 
          ? `${body.content.text}\n\nTranscript: ${body.content.transcript}`
          : body.content.text,
        userId,
        priority: 10,
      });
    } catch (err) {
      logger.error('Failed to enqueue embedding for new moment', err);
      // Don't fail the request, just log
    }

    return reply.code(201).send({
      moment,
    });
  });

  /**
   * POST /api/moments/classify-sphere
   * AI-powered sphere classification
   * Analyzes moment content and suggests the best sphere
   */
  app.post("/api/moments/classify-sphere", {
    onRequest: [(app as any).authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const body = classifySphereSchema.parse(request.body);

    try {
      // Get all user's spheres
      const userSpheres = await prisma.sphere.findMany({
        where: {
          userId,
          deletedAt: null,
          sphereAccess: {
            some: {
              userId,
              revokedAt: null,
            },
          },
        },
        select: {
          id: true,
          name: true,
          description: true,
          type: true,
        },
      });

      if (userSpheres.length === 0) {
        return reply.code(400).send({
          error: "No spheres available",
          message: "Create at least one Sphere before capturing moments",
        });
      }

      // Simple keyword-based classification
      // In production, this would use more sophisticated NLP/ML
      const contentText = body.content.text?.toLowerCase() || "";
      const keywords = [
        ...(body.signals?.tags || []).map(t => t.toLowerCase()),
        ...(body.signals?.emotions || []).map(e => e.toLowerCase()),
      ];

      let bestMatch = userSpheres[0];
      let maxScore = 0;

      for (const sphere of userSpheres) {
        const sphereName = sphere.name.toLowerCase();
        const sphereDesc = (sphere.description || "").toLowerCase();
        const sphereContent = `${sphereName} ${sphereDesc}`;

        let score = 0;

        // Match against sphere name and description
        if (contentText.includes(sphereName)) score += 10;
        if (sphereContent.includes(contentText)) score += 5;

        // Match against keywords/tags
        for (const keyword of keywords) {
          if (keyword.length > 2) {
            if (sphereContent.includes(keyword)) score += 2;
            if (contentText.includes(keyword) && sphereContent.includes(keyword)) score += 3;
          }
        }

        // Type-based matching
        if (sphere.type === "PERSONAL" && keywords.includes("personal")) score += 5;
        if (sphere.type === "WORK" && (keywords.includes("work") || keywords.includes("professional"))) score += 5;
        if (sphere.type === "FAMILY" && keywords.includes("family")) score += 5;

        if (score > maxScore) {
          maxScore = score;
          bestMatch = sphere;
        }
      }

      return reply.send({
        sphereId: bestMatch.id,
        sphereName: bestMatch.name,
        confidence: maxScore > 0 ? Math.min(100, 50 + maxScore) : 50,
      });
    } catch (error) {
      console.error("Sphere classification error:", error);
      return reply.code(500).send({
        error: "Classification failed",
        message: "Failed to classify sphere",
      });
    }
  });

  app.get<{ Params: { id: string } }>("/api/moments/:id", {
    onRequest: [(app as any).authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { id } = request.params;

    // Fetch Moment
    const moment = await prisma.moment.findUnique({
      where: { id },
      include: {
        sphere: {
          select: {
            id: true,
            name: true,
            type: true,
            visibility: true,
          },
        },
        media: {
          include: {
            mediaReference: true,
          },
        },
        user: {
          select: {
            id: true,
            email: true,
            displayName: true,
          },
        },
      },
    });

    if (!moment || moment.deletedAt) {
      return reply.code(404).send({
        error: "Not found",
        message: "Moment not found",
      });
    }

    // Check user has access to the Sphere
    const access = await prisma.sphereAccess.findFirst({
      where: {
        sphereId: moment.sphereId,
        userId,
        revokedAt: null,
      },
    });

    if (!access) {
      return reply.code(403).send({
        error: "Access denied",
        message: "You do not have access to this Moment",
      });
    }

    // Audit log
    await prisma.auditEvent.create({
      data: {
        eventType: "MOMENT_SEARCHED",
        userId,
        momentId: moment.id,
        sphereId: moment.sphereId,
        actor: (request.user as JwtPayload).email,
        action: "VIEW",
        resourceType: "MOMENT",
        resourceId: moment.id,
      },
    });

    const canEdit = ["OWNER", "EDITOR"].includes(access.role);

    return reply.send({
      moment,
      canEdit,
    });
  });

  /**
   * GET /api/moments
   * Search/query Moments
   */
  app.get("/api/moments", {
    onRequest: [(app as any).authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const query = searchMomentsSchema.parse(request.query);

    // Get accessible Spheres
    const userSpheres = await prisma.sphereAccess.findMany({
      where: {
        userId,
        revokedAt: null,
      },
      select: {
        sphereId: true,
      },
    });

    const accessibleSphereIds = userSpheres.map(s => s.sphereId);

    // Filter by sphereIds if provided
    const targetSphereIds = query.sphereIds
      ? accessibleSphereIds.filter(id => query.sphereIds!.includes(id))
      : accessibleSphereIds;

    if (targetSphereIds.length === 0) {
      return reply.send({
        moments: [],
        nextCursor: null,
        totalCount: 0,
      });
    }

    // Build where clause
    const where: any = {
      sphereId: { in: targetSphereIds },
      deletedAt: null,
    };

    if (query.tags && query.tags.length > 0) {
      where.tags = { hasSome: query.tags };
    }

    if (query.emotions && query.emotions.length > 0) {
      where.emotions = { hasSome: query.emotions };
    }

    if (query.startDate || query.endDate) {
      where.capturedAt = {};
      if (query.startDate) {
        where.capturedAt.gte = new Date(query.startDate);
      }
      if (query.endDate) {
        where.capturedAt.lte = new Date(query.endDate);
      }
    }

    // Full-text search if query provided
    if (query.query) {
      where.OR = [
        { contentText: { contains: query.query, mode: "insensitive" } },
        { contentTranscript: { contains: query.query, mode: "insensitive" } },
      ];
    }

    // Cursor pagination
    if (query.cursor) {
      where.id = { lt: query.cursor };
    }

    // Fetch Moments
    const moments = await prisma.moment.findMany({
      where,
      orderBy: { capturedAt: "desc" },
      take: query.limit + 1, // Fetch one extra to determine if there are more
      include: {
        sphere: {
          select: {
            id: true,
            name: true,
            type: true,
          },
        },
        user: {
          select: {
            id: true,
            displayName: true,
          },
        },
      },
    });

    const hasMore = moments.length > query.limit;
    const resultMoments = hasMore ? moments.slice(0, -1) : moments;
    const nextCursor = hasMore ? resultMoments[resultMoments.length - 1].id : null;

    // Approximate count (for performance)
    const totalCount = await prisma.moment.count({ where });

    return reply.send({
      moments: resultMoments,
      nextCursor,
      totalCount,
    });
  });

  /**
   * DELETE /api/moments/:id
   * Soft delete a Moment
   */
  app.delete<{ Params: { id: string } }>("/api/moments/:id", {
    onRequest: [(app as any).authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { id } = request.params;

    // Fetch Moment
    const moment = await prisma.moment.findUnique({
      where: { id },
      select: {
        id: true,
        userId: true,
        sphereId: true,
        deletedAt: true,
      },
    });

    if (!moment || moment.deletedAt) {
      return reply.code(404).send({
        error: "Not found",
        message: "Moment not found",
      });
    }

    // Check ownership or OWNER role
    const access = await prisma.sphereAccess.findFirst({
      where: {
        sphereId: moment.sphereId,
        userId,
        revokedAt: null,
      },
    });

    const canDelete = moment.userId === userId || access?.role === "OWNER";

    if (!canDelete) {
      return reply.code(403).send({
        error: "Access denied",
        message: "You do not have permission to delete this Moment",
      });
    }

    // Soft delete
    await prisma.moment.update({
      where: { id },
      data: { deletedAt: new Date() },
    });

    // Audit log
    await prisma.auditEvent.create({
      data: {
        eventType: "MOMENT_DELETED",
        userId,
        momentId: id,
        sphereId: moment.sphereId,
        actor: (request.user as JwtPayload).email,
        action: "DELETE",
        resourceType: "MOMENT",
        resourceId: id,
      },
    });

    return reply.code(204).send();
  });
};

